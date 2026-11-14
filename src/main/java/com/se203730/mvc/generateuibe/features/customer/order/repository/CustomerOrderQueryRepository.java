package com.se203730.mvc.generateuibe.features.customer.order.repository;

import com.se203730.mvc.generateuibe.features.customer.order.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CustomerOrderQueryRepository {
    private final JdbcTemplate jdbc;

    private static final String SELECT_SUMMARY = """
            SELECT o.id, o.order_number, o.status, o.items_subtotal, o.vat_amount,
                   o.shipping_fee, o.total_amount, o.confirmed_at, o.completed_at,
                   o.cancelled_at, o.created_at,
                   COALESCE(
                       (SELECT 'SUCCESS' FROM public.payments pay
                        WHERE pay.order_id = o.id AND pay.status='SUCCESS' LIMIT 1),
                       (SELECT pay.status FROM public.payments pay WHERE pay.order_id = o.id
                        ORDER BY pay.created_at DESC, pay.id DESC LIMIT 1),
                       'NOT_STARTED') AS payment_status
            FROM public.orders o
            """;

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private CustomerOrderSummaryResponse mapSummary(ResultSet rs) throws SQLException {
        return new CustomerOrderSummaryResponse(
                rs.getObject("id", UUID.class), rs.getString("order_number"), rs.getString("status"),
                rs.getString("payment_status"), rs.getBigDecimal("items_subtotal"),
                rs.getBigDecimal("vat_amount"), rs.getBigDecimal("shipping_fee"),
                rs.getBigDecimal("total_amount"), instant(rs.getTimestamp("confirmed_at")),
                instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("cancelled_at")),
                instant(rs.getTimestamp("created_at")));
    }

    public long countForCustomer(UUID customerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.orders WHERE customer_id = ?", Long.class, customerId);
        return count == null ? 0 : count;
    }

    public List<CustomerOrderSummaryResponse> listForCustomer(UUID customerId, int size, long offset) {
        return jdbc.query(SELECT_SUMMARY + """
                WHERE o.customer_id = ? ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> mapSummary(rs), customerId, size, offset);
    }

    public Optional<CustomerOrderSummaryResponse> findOwned(UUID customerId, UUID orderId) {
        List<CustomerOrderSummaryResponse> rows = jdbc.query(SELECT_SUMMARY + """
                WHERE o.customer_id = ? AND o.id = ?
                """, (rs, row) -> mapSummary(rs), customerId, orderId);
        return rows.stream().findFirst();
    }

    public List<CustomerOrderItemResponse> items(UUID customerId, UUID orderId) {
        return jdbc.query("""
                SELECT oi.id, oi.product_id, oi.sku, oi.name_vi, oi.name_en,
                       oi.unit_code, oi.quantity, oi.sale_unit_price, oi.vat_rate,
                       oi.vat_amount, oi.line_total
                FROM public.order_items oi JOIN public.orders o ON o.id = oi.order_id
                WHERE o.id = ? AND o.customer_id = ? ORDER BY oi.created_at, oi.id
                """, (rs, row) -> new CustomerOrderItemResponse(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("sku"), rs.getString("name_vi"), rs.getString("name_en"),
                rs.getString("unit_code"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("sale_unit_price"), rs.getBigDecimal("vat_rate"),
                rs.getBigDecimal("vat_amount"), rs.getBigDecimal("line_total")), orderId, customerId);
    }

    public List<CustomerOrderPackageResponse> packages(UUID customerId, UUID orderId) {
        return jdbc.query("""
                SELECT op.id, op.package_number, op.package_name, op.delivery_date,
                       op.delivery_address, op.recipient_name, op.recipient_phone,
                       op.delivery_time_slot, op.cancelled_at
                FROM public.order_packages op JOIN public.orders o ON o.id = op.order_id
                WHERE o.id = ? AND o.customer_id = ?
                ORDER BY op.delivery_date, op.package_number, op.id
                """, (rs, row) -> new CustomerOrderPackageResponse(
                rs.getObject("id", UUID.class), rs.getString("package_number"),
                rs.getString("package_name"), rs.getObject("delivery_date", java.time.LocalDate.class),
                rs.getString("delivery_address"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), rs.getString("delivery_time_slot"),
                rs.getTimestamp("cancelled_at") != null), orderId, customerId);
    }
}
