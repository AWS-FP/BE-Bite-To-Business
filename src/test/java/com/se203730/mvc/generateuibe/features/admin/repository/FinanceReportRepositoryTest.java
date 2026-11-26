package com.se203730.mvc.generateuibe.features.admin.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class FinanceReportRepositoryTest {
    private FinanceReportRepository reports;
    private final UUID customer = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=TIME", "sa", "");
        var jdbc = new JdbcTemplate(source);
        reports = new FinanceReportRepository(new NamedParameterJdbcTemplate(source));
        jdbc.execute("CREATE TABLE users(id UUID PRIMARY KEY, first_name TEXT, last_name TEXT)");
        jdbc.execute("CREATE TABLE orders(id UUID PRIMARY KEY, customer_id UUID, order_number TEXT, status TEXT, total_amount NUMERIC, created_at TIMESTAMP WITH TIME ZONE, completed_at TIMESTAMP WITH TIME ZONE, cancelled_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE payments(id UUID PRIMARY KEY, order_id UUID, status TEXT, amount NUMERIC, currency TEXT, paid_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE, provider_transaction_id TEXT)");
        jdbc.execute("CREATE TABLE package_refunds(id UUID PRIMARY KEY, payment_id UUID, status TEXT, refund_amount NUMERIC, refunded_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE, transfer_reference TEXT)");
        jdbc.execute("CREATE TABLE sepay_webhook_events(transaction_id BIGINT PRIMARY KEY, payment_id UUID, result TEXT, resolution TEXT, transfer_type TEXT, amount NUMERIC, received_at TIMESTAMP WITH TIME ZONE, reference_code TEXT)");
        jdbc.update("INSERT INTO users VALUES (?, 'First', 'Last')", customer);
        for (int n = 1; n <= 2; n++) {
            UUID id = id(n + 10);
            jdbc.update("INSERT INTO orders VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, NULL)", id, customer, "O" + n, n * 100,
                    Timestamp.from(Instant.parse("2026-11-26T17:00:00Z")), Timestamp.from(Instant.parse("2026-11-26T18:00:00Z")));
            jdbc.update("INSERT INTO payments VALUES (?, ?, 'SUCCESS', ?, 'VND', ?, ?, ?)",
                    id(n + 20), id, n * 100, Timestamp.from(Instant.parse("2026-11-26T18:00:00Z")),
                    Timestamp.from(Instant.parse("2026-11-26T17:00:00Z")), "TX" + n);
        }
        // One failed attempt must not increase receipts.
        jdbc.update("INSERT INTO payments VALUES (?, ?, 'FAILED', 100, 'VND', NULL, ?, NULL)",
                id(23), id(11), Timestamp.from(Instant.parse("2026-11-26T19:00:00Z")));
        for (int n = 1; n <= 2; n++) {
            jdbc.update("INSERT INTO package_refunds VALUES (?, ?, 'REFUNDED', ?, ?, ?, 'RF')", id(n + 30), id(21), n * 10,
                    Timestamp.from(Instant.parse("2026-11-27T00:00:00Z")), Timestamp.from(Instant.parse("2026-11-26T21:00:00Z")));
        }
        // A requested refund is not money returned.
        jdbc.update("INSERT INTO package_refunds VALUES (?, ?, 'REQUESTED', 70, NULL, ?, NULL)",
                id(33), id(21), Timestamp.from(Instant.parse("2026-11-27T01:00:00Z")));
        jdbc.update("INSERT INTO sepay_webhook_events VALUES (1, NULL, 'REVIEW', NULL, 'IN', 999, ?, 'REV')",
                Timestamp.from(Instant.parse("2026-11-27T00:00:00Z")));
        // Restored review is already represented by its successful payment.
        jdbc.update("INSERT INTO sepay_webhook_events VALUES (2, ?, 'REVIEW', 'RESTORED', 'in', 100, ?, 'OLD')",
                id(21), Timestamp.from(Instant.parse("2026-11-27T00:00:00Z")));
    }

    private UUID id(int number) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", number));
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("start", Timestamp.from(Instant.parse("2026-11-26T17:00:00Z")))
                .addValue("end", Timestamp.from(Instant.parse("2026-11-27T17:00:00Z")))
                .addValue("customerId", null).addValue("group", "day").addValue("kind", "ALL").addValue("status", "ALL");
    }

    private void amount(String expected, BigDecimal value) {
        assertEquals(0, new BigDecimal(expected).compareTo(value));
    }

    @Test
    void countsEachPaymentOnceAndOnlySettledRefunds() {
        var summary = reports.summary(params());
        assertEquals(2, summary.orders());
        assertEquals(2, summary.paymentCount());
        assertEquals(2, summary.refundCount());
        amount("300", summary.collected());
        amount("30", summary.refunded());
        amount("270", summary.netCollected());
        assertEquals(1, summary.openReviewCount());
        amount("999", summary.openReviewAmount());
        var periods = reports.periods(params());
        assertEquals(1, periods.size());
        assertEquals("2026-11-27", periods.getFirst().date().toString());
        amount("270", periods.getFirst().netCollected());
    }

    @Test
    void customerTotalsAndPaginationDoNotMultiplySplitRefunds() {
        var page = reports.customers(params(), 0, 20);
        assertEquals(1, page.totalElements());
        assertEquals(2, page.content().getFirst().orders());
        amount("270", page.content().getFirst().netCollected());
        var transactions = reports.transactions(params().addValue("kind", "REFUND").addValue("status", "REFUNDED"), 0, 1);
        assertEquals(2, transactions.totalElements());
        assertEquals(2, transactions.totalPages());
        assertEquals(1, transactions.content().size());
    }

    @Test
    void settledRefundCanProduceNegativeReceiptsInALaterPeriod() {
        var parameters = params().addValue("start", Timestamp.from(Instant.parse("2026-11-26T23:00:00Z")));
        var summary = reports.summary(parameters);
        amount("0", summary.collected());
        amount("30", summary.refunded());
        amount("-30", summary.netCollected());
        amount("-30", reports.customers(parameters, 0, 20).content().getFirst().netCollected());

    }

    @Test
    void inclusiveVietnamDateAndCustomerFiltersApplyToAllSources() {
        assertEquals(0, reports.summary(params().addValue("customerId", id(99))).paymentCount());
        assertEquals(0, reports.transactions(params().addValue("customerId", id(99)), 0, 20).totalElements());
        // Exclusive end excludes all receipts and refunds after this boundary.
        var summary = reports.summary(params().addValue("end", Timestamp.from(Instant.parse("2026-11-26T18:00:00Z"))));
        assertEquals(2, summary.orders());
        assertEquals(0, summary.paymentCount());
        assertEquals(0, summary.refundCount());
    }
}

