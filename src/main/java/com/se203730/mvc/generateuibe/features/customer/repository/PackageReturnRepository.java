package com.se203730.mvc.generateuibe.features.customer.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Yêu cầu trả hàng của khách cho package đã giao (bảng package_returns). */
@Repository
public class PackageReturnRepository {
    private static final String SELECT = """
            select r.id, r.status, r.reason, r.recipient_bank, r.recipient_account, r.recipient_name,
                   r.review_note, r.reviewed_at, r.created_at, p.id as package_id, p.package_name,
                   p.delivery_date, p.delivery_address, p.recipient_name as package_recipient,
                   p.recipient_phone, c.delivered_at, o.id as order_id, o.customer_id, o.contact_name,
                   o.contact_phone, o.contact_email, o.company_name,
                   (select pay.payment_number from public.payments pay
                     where pay.order_id = o.id and pay.status = 'SUCCESS' limit 1) as payment_number,
                   r.merchandise_amount as package_amount, r.return_fee, r.refund_amount,
                   r.refunded_at, r.refunded_by, r.transfer_reference
            from public.package_returns r
            join public.order_packages p on p.id = r.order_package_id
            join public.orders o on o.id = p.order_id
            left join public.delivery_package_claims c on c.package_id = p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PackageReturnRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public record ReturnView(UUID id, String status, String reason, String recipientBank, String recipientAccount,
                             String recipientName, String reviewNote, Instant reviewedAt, Instant createdAt,
                             UUID packageId, String packageName, LocalDate deliveryDate, String deliveryAddress,
                             String packageRecipient, String recipientPhone, Instant deliveredAt, UUID orderId,
                             UUID customerId, String contactName, String contactPhone, String contactEmail,
                             String companyName, String paymentNumber, BigDecimal packageAmount,
                             BigDecimal returnFee, BigDecimal refundAmount, Instant refundedAt,
                             UUID refundedBy, String transferReference) {}

    public record ReturnAmounts(BigDecimal packageAmount, BigDecimal returnFee, BigDecimal refundAmount) {}

    public ReturnAmounts calculateAmounts(UUID packageId) {
        BigDecimal amount = jdbc.queryForObject("""
                select merchandise_amount - product_coupon_discount_amount + vat_amount
                from public.order_packages where id = :packageId
                """, new MapSqlParameterSource("packageId", packageId), BigDecimal.class);
        if (amount == null) {
            return null;
        }
        BigDecimal fee = amount.multiply(new BigDecimal("0.02")).setScale(0, RoundingMode.HALF_UP);
        return new ReturnAmounts(amount, fee, amount.subtract(fee));
    }

    public record PackageState(UUID packageId, UUID orderId, UUID customerId, Instant cancelledAt,
                               Instant deliveredAt, boolean hasReturn) {}

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static final RowMapper<ReturnView> MAPPER = (ResultSet rs, int i) -> new ReturnView(
            rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("reason"),
            rs.getString("recipient_bank"), rs.getString("recipient_account"), rs.getString("recipient_name"),
            rs.getString("review_note"), instant(rs.getTimestamp("reviewed_at")), instant(rs.getTimestamp("created_at")),
            rs.getObject("package_id", UUID.class), rs.getString("package_name"),
            rs.getObject("delivery_date", LocalDate.class), rs.getString("delivery_address"),
            rs.getString("package_recipient"), rs.getString("recipient_phone"),
            instant(rs.getTimestamp("delivered_at")), rs.getObject("order_id", UUID.class),
            rs.getObject("customer_id", UUID.class), rs.getString("contact_name"), rs.getString("contact_phone"),
            rs.getString("contact_email"), rs.getString("company_name"), rs.getString("payment_number"),
            rs.getBigDecimal("package_amount"), rs.getBigDecimal("return_fee"), rs.getBigDecimal("refund_amount"),
            instant(rs.getTimestamp("refunded_at")), rs.getObject("refunded_by", UUID.class), rs.getString("transfer_reference"));

    /** Khóa package và đọc trạng thái giao / trả để kiểm tra trước khi tạo yêu cầu. */
    public Optional<PackageState> lockPackage(UUID packageId) {
        return jdbc.query("""
                select p.id, p.order_id, o.customer_id, p.cancelled_at, c.delivered_at,
                       exists(select 1 from public.package_returns r
                              where r.order_package_id = p.id and r.status in ('REQUESTED', 'ACCEPTED')) as has_return
                from public.order_packages p join public.orders o on o.id = p.order_id
                left join public.delivery_package_claims c on c.package_id = p.id
                where p.id = :packageId for update of p
                """, new MapSqlParameterSource("packageId", packageId),
                (rs, i) -> new PackageState(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getObject("customer_id", UUID.class), instant(rs.getTimestamp("cancelled_at")),
                        instant(rs.getTimestamp("delivered_at")), rs.getBoolean("has_return")))
                .stream().findFirst();
    }

    public void insert(UUID id, UUID packageId, UUID customerId, String reason, String bank, String account,
                       String name, Instant now, ReturnAmounts amounts) {
        jdbc.update("""
                insert into public.package_returns (id, order_package_id, requested_by, status, reason,
                    recipient_bank, recipient_account, recipient_name, created_at, updated_at,
                    merchandise_amount, return_fee, refund_amount)
                values (:id, :packageId, :customerId, 'REQUESTED', :reason, :bank, :account, :name, :now, :now,
                    :amount, :fee, :refund)
                """, new MapSqlParameterSource("id", id).addValue("packageId", packageId)
                .addValue("customerId", customerId).addValue("reason", reason).addValue("bank", bank)
                .addValue("account", account).addValue("name", name).addValue("now", Timestamp.from(now))
                .addValue("amount", amounts.packageAmount()).addValue("fee", amounts.returnFee())
                .addValue("refund", amounts.refundAmount()));
    }

    public Optional<ReturnView> find(UUID id) {
        return jdbc.query(SELECT + " where r.id = :id", new MapSqlParameterSource("id", id), MAPPER)
                .stream().findFirst();
    }

    /** Khóa dòng yêu cầu và trả về trạng thái hiện tại. */
    public Optional<String> lockStatus(UUID id) {
        return jdbc.queryForList("select status from public.package_returns where id = :id for update",
                new MapSqlParameterSource("id", id), String.class).stream().findFirst();
    }

    public List<ReturnView> page(String status, int page, int size) {
        String where = status == null ? "" : " where r.status = :status";
        return jdbc.query(SELECT + where + " order by r.created_at desc, r.id limit :limit offset :offset",
                new MapSqlParameterSource("status", status).addValue("limit", size)
                        .addValue("offset", (long) page * size), MAPPER);
    }

    public long count(String status) {
        String where = status == null ? "" : " where status = :status";
        Long total = jdbc.queryForObject("select count(*) from public.package_returns" + where,
                new MapSqlParameterSource("status", status), Long.class);
        return total == null ? 0 : total;
    }

    public void review(UUID id, String status, UUID adminId, String note, Instant now) {
        jdbc.update("""
                update public.package_returns set status = :status, reviewed_by = :admin, reviewed_at = :now,
                    review_note = :note, updated_at = :now where id = :id and status = 'REQUESTED'
                """, new MapSqlParameterSource("id", id).addValue("status", status).addValue("admin", adminId)
                .addValue("note", note).addValue("now", Timestamp.from(now)));
    }

    public void recordRefund(UUID id, UUID adminId, Instant now) {
        jdbc.update("""
                update public.package_returns set refunded_at = :now, refunded_by = :admin,
                    updated_at = :now where id = :id
                """, new MapSqlParameterSource("id", id).addValue("admin", adminId)
                .addValue("now", Timestamp.from(now)));
    }
}
