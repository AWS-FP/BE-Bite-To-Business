package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.features.admin.repository.FinanceReportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FinanceReportServiceTest {
    private final AccountAccessService accounts = mock(AccountAccessService.class);
    private final FinanceReportRepository repository = mock(FinanceReportRepository.class);
    private final FinanceReportService service = new FinanceReportService(accounts, repository);
    private final UUID admin = UUID.randomUUID();

    @Test
    void invalidPeriodOrPaginationDoesNotQueryMoneyTables() {
        LocalDate day = LocalDate.of(2026, 11, 27);
        assertThrows(BusinessException.class, () -> service.report(admin, day, day.minusDays(1), "day", null));
        assertThrows(BusinessException.class, () -> service.report(admin, day.minusDays(366), day, "day", null));
        assertThrows(BusinessException.class, () -> service.customers(admin, day, day, null, -1, 20));
        assertThrows(BusinessException.class, () -> service.transactions(admin, day, day, null, "ALL", "ALL", 0, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void adminAccessIsCheckedBeforeFinancialQueries() {
        doThrow(new BusinessException(com.se203730.mvc.generateuibe.common.exception.ErrorCode.ACCESS_DENIED))
                .when(accounts).requireAdmin(admin);
        LocalDate day = LocalDate.of(2026, 11, 27);
        assertThrows(BusinessException.class, () -> service.report(admin, day, day, "day", null));
        assertThrows(BusinessException.class, () -> service.customers(admin, day, day, null, 0, 20));
        assertThrows(BusinessException.class, () -> service.transactions(admin, day, day, null, "ALL", "ALL", 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void dailyRowsAreGroupedByIsoWeekAndCalendarMonth() {
        var amount = java.math.BigDecimal.TEN;
        when(repository.periods(any())).thenReturn(List.of(
                new com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse.Period(
                        LocalDate.of(2026, 11, 27), amount, amount, java.math.BigDecimal.ZERO, amount, 1),
                new com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse.Period(
                        LocalDate.of(2026, 11, 28), amount, amount, java.math.BigDecimal.ZERO, amount, 1)));
        for (String group : List.of("week", "month")) {
            var report = service.report(admin, LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 28), group, null);
            assertEquals(1, report.periods().size());
            assertEquals(2, report.periods().getFirst().completedOrders());
            assertEquals(0, new java.math.BigDecimal("20").compareTo(report.periods().getFirst().collected()));
            assertEquals(group.equals("week") ? "2026-11-23" : "2026-11-01",
                    report.periods().getFirst().date().toString());
        }
    }

    @Test
    void validEmptyPeriodProducesZeroBucketsAndVietnamBoundaries() {
        when(repository.periods(any())).thenReturn(List.of());
        var response = service.report(admin, LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 28), "day", null);
        assertEquals(2, response.periods().size());
        assertEquals(0, response.periods().getFirst().collected().signum());
        var captured = org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(repository).summary(captured.capture());
        assertEquals("2026-11-26T17:00:00Z", ((java.sql.Timestamp) captured.getValue().getValue("start")).toInstant().toString());
        assertEquals("2026-11-28T17:00:00Z", ((java.sql.Timestamp) captured.getValue().getValue("end")).toInstant().toString());
    }
}

