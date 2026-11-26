package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse.*;
import com.se203730.mvc.generateuibe.features.admin.repository.FinanceReportRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FinanceReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AccountAccessService accounts;
    private final FinanceReportRepository reports;

    public FinanceReportService(AccountAccessService accounts, FinanceReportRepository reports) {
        this.accounts = accounts;
        this.reports = reports;
    }

    public FinanceReportResponse report(UUID adminId, LocalDate from, LocalDate to, String group, UUID customerId) {
        accounts.requireAdmin(adminId);
        MapSqlParameterSource params = params(from, to, customerId).addValue("group", group);
        if (!java.util.Set.of("day", "week", "month").contains(group)) invalid();
        Summary summary = reports.summary(params);
        // At most 366 daily rows. Group in Vietnamese calendar time with ISO Monday weeks.
        Map<LocalDate, Period> values = new LinkedHashMap<>();
        for (Period daily : reports.periods(params)) {
            LocalDate date = bucketDate(daily.date(), group);
            Period period = new Period(date, daily.collected(), daily.refunded(), daily.netCollected(),
                    daily.completedAmount(), daily.completedOrders());
            values.merge(date, period, (left, right) -> new Period(date,
                    left.collected().add(right.collected()), left.refunded().add(right.refunded()),
                    left.netCollected().add(right.netCollected()), left.completedAmount().add(right.completedAmount()),
                    left.completedOrders() + right.completedOrders()));
        }
        LocalDate date = bucketDate(from, group);
        var periods = new ArrayList<Period>();
        while (!date.isAfter(to)) {
            periods.add(values.getOrDefault(date, new Period(date, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0)));
            date = switch (group) {
                case "week" -> date.plusWeeks(1);
                case "month" -> date.plusMonths(1);
                default -> date.plusDays(1);
            };
        }
        return new FinanceReportResponse(from, to, group, "VND", ZONE.getId(), summary, periods);
    }

    public PageResponse<Transaction> transactions(UUID adminId, LocalDate from, LocalDate to,
            UUID customerId, String kind, String status, int page, int size) {
        accounts.requireAdmin(adminId);
        checkPage(page, size);
        return reports.transactions(params(from, to, customerId).addValue("kind", kind).addValue("status", status), page, size);
    }

    public PageResponse<Customer> customers(UUID adminId, LocalDate from, LocalDate to, UUID customerId, int page, int size) {
        accounts.requireAdmin(adminId);
        checkPage(page, size);
        return reports.customers(params(from, to, customerId), page, size);
    }

    private LocalDate bucketDate(LocalDate day, String group) {
        return switch (group) {
            case "week" -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> day.withDayOfMonth(1);
            default -> day;
        };
    }

    private MapSqlParameterSource params(LocalDate from, LocalDate to, UUID customerId) {
        if (from == null || to == null || from.getYear() < 1 || to.getYear() > 9999
                || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) invalid();
        return new MapSqlParameterSource("start", Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
                .addValue("end", Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant()))
                .addValue("customerId", customerId);
    }

    private void checkPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) invalid();
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}

