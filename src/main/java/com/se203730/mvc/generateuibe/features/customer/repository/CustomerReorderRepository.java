package com.se203730.mvc.generateuibe.features.customer.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Historical order snapshots. Never uses old pricing for the new quote. */
@Repository
public class CustomerReorderRepository {
    private final JdbcTemplate jdbc;

    public CustomerReorderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Contact(String name, String phone, String email, String company, String note) {}
    public record Line(UUID productId, BigDecimal quantity) {}
    public record Parcel(UUID id, String name, String recipientName, String recipientPhone,
                         LocalDate deliveryDate, String address, String note, String city,
                         BigDecimal latitude, BigDecimal longitude) {}
    public record Allocation(UUID productId, BigDecimal quantity) {}

    public Optional<Contact> contact(UUID customerId, UUID orderId) {
        return jdbc.query("""
            select o.contact_name, o.contact_phone, o.contact_email, o.company_name, o.note
            from public.orders o where o.customer_id = ? and o.id = ?
            """, (rs, n) -> new Contact(rs.getString("contact_name"), rs.getString("contact_phone"),
                    rs.getString("contact_email"), rs.getString("company_name"), rs.getString("note")),
                customerId, orderId).stream().findFirst();
    }

    public List<Line> lines(UUID customerId, UUID orderId) {
        return jdbc.query("""
            select oi.product_id, oi.quantity from public.order_items oi
            join public.orders o on o.id = oi.order_id
            where o.customer_id = ? and o.id = ?
            order by oi.created_at, oi.id
            """, (rs, n) -> new Line(rs.getObject("product_id", UUID.class),
                    rs.getBigDecimal("quantity")), customerId, orderId);
    }

    public List<Parcel> parcels(UUID customerId, UUID orderId) {
        return jdbc.query("""
            select p.id, p.package_name, p.recipient_name, p.recipient_phone,
                p.delivery_date, p.delivery_address, p.delivery_note, p.city,
                p.latitude, p.longitude
            from public.order_packages p
            join public.orders o on o.id = p.order_id
            where o.customer_id = ? and o.id = ?
            order by p.delivery_date, p.package_number, p.id
            """, (rs, n) -> new Parcel(rs.getObject("id", UUID.class),
                    rs.getString("package_name"), rs.getString("recipient_name"),
                    rs.getString("recipient_phone"), rs.getObject("delivery_date", LocalDate.class),
                    rs.getString("delivery_address"), rs.getString("delivery_note"),
                    rs.getString("city"), rs.getBigDecimal("latitude"),
                    rs.getBigDecimal("longitude")), customerId, orderId);
    }

    public List<Allocation> allocations(UUID customerId, UUID orderId, UUID parcelId) {
        return jdbc.query("""
            select oi.product_id, pi.quantity
            from public.package_items pi
            join public.order_items oi on oi.id = pi.order_item_id
            join public.order_packages p on p.id = pi.order_package_id
            join public.orders o on o.id = p.order_id
            where o.customer_id = ? and o.id = ? and p.id = ? and oi.order_id = o.id
            order by oi.product_id
            """, (rs, n) -> new Allocation(rs.getObject("product_id", UUID.class),
                    rs.getBigDecimal("quantity")), customerId, orderId, parcelId);
    }
}
