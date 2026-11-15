package com.se203730.mvc.generateuibe.features.customer.invoice.repository;

import com.se203730.mvc.generateuibe.features.customer.invoice.dto.response.CustomerInvoiceResponse;
import com.se203730.mvc.generateuibe.features.customer.invoice.dto.response.OrderVatLineResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerInvoiceRepository {
    private final JdbcTemplate jdbc;

    public CustomerInvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Enforce ownership IN SQL. Joining invoice data is safe only after the order
     * has been restricted to the authenticated Customer ID.
     */
    public Optional<CustomerInvoiceResponse> findInvoice(UUID customerId, UUID orderId) {
        List<CustomerInvoiceResponse> matches = jdbc.query("""
                SELECT o.id AS order_id, o.order_number, o.vat_amount,
                       o.total_amount, i.status AS invoice_status,
                       i.invoice_number, i.pdf_url, i.issued_at
                FROM public.orders o
                LEFT JOIN public.invoices i ON i.order_id = o.id
                WHERE o.id = ? AND o.customer_id = ?
                """, (rs, rowNum) -> {
            String status = rs.getString("invoice_status");
            if (status == null) status = "NOT_ISSUED";
            boolean issued = "ISSUED".equals(status);
            String url = issued ? rs.getString("pdf_url") : null;
            if (url != null && url.isBlank()) url = null;
            Timestamp issuedTimestamp = issued ? rs.getTimestamp("issued_at") : null;
            Instant issuedAt = issuedTimestamp == null ? null : issuedTimestamp.toInstant();
            return new CustomerInvoiceResponse(
                    rs.getObject("order_id", UUID.class),
                    rs.getString("order_number"), status,
                    issued ? rs.getString("invoice_number") : null,
                    rs.getBigDecimal("vat_amount"), rs.getBigDecimal("total_amount"),
                    url, issuedAt, url != null);
        }, orderId, customerId);
        return matches.stream().findFirst();
    }

    public List<OrderVatLineResponse> findVatLines(UUID orderId) {
        return jdbc.query("""
                SELECT oi.product_id, oi.sku, oi.name_vi,
                       oi.quantity, oi.vat_rate, oi.vat_amount, oi.line_total,
                       (oi.line_total - oi.vat_amount) AS amount_excluding_vat
                FROM public.order_items oi
                WHERE oi.order_id = ?
                ORDER BY oi.created_at, oi.id
                """, (rs, rowNum) -> new OrderVatLineResponse(
                rs.getObject("product_id", UUID.class),
                rs.getString("sku"), rs.getString("name_vi"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("vat_rate"),
                rs.getBigDecimal("amount_excluding_vat"),
                rs.getBigDecimal("vat_amount"), rs.getBigDecimal("line_total")
        ), orderId);
    }
}
