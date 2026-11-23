package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.service.NotificationEventPublisher;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.request.ExecutiveRefundDecisionRequest;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.EcommerceExecutiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * E-commerce duyệt yêu cầu hủy package của khách: duyệt (hủy package, chốt số tiền hoàn),
 * từ chối (kèm lý do) và xác nhận đã chuyển khoản hoàn tiền.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveRefundService {
    private static final Set<String> STATES = Set.of("REQUESTED", "APPROVED", "REJECTED", "PROCESSING", "REFUNDED");
    // Tiền hàng của package = phần line_total tương ứng số lượng được phân bổ vào package.
    private static final String PACKAGE_AMOUNT = "(select coalesce(sum(round(oi.line_total * pi.quantity / oi.quantity, 0)), 0) "
        + "from public.package_items pi join public.order_items oi on oi.id = pi.order_item_id "
        + "where pi.order_package_id = p.id)";
    private static final String SELECT = "select r.id, r.request_group_id, r.status, r.reason, r.recipient_bank, "
        + "r.recipient_account, r.recipient_name, r.refund_amount, r.review_note, r.transfer_reference, "
        + "r.created_at, r.reviewed_at, r.refunded_at, p.id as package_id, p.package_name, p.cancelled_at, "
        + "p.delivery_date, p.delivery_time_slot, p.delivery_address, o.id as order_id, o.order_number, "
        + "o.customer_id, o.company_name, o.contact_name, o.contact_phone, " + PACKAGE_AMOUNT + " as package_amount "
        + "from public.package_refunds r join public.order_packages p on p.id = r.order_package_id "
        + "join public.orders o on o.id = p.order_id";

    private final EcommerceExecutiveRepository db;
    private final DatabaseClock clock;
    private final NotificationEventPublisher notifications;

    @Transactional(readOnly = true)
    public EcommercePageResponse<Map<String, Object>> list(String status, int page, int size) {
        if (status != null && !STATES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var params = new MapSqlParameterSource("status", status);
        String where = status == null ? "" : " where r.status = :status";
        return db.page(SELECT + where + " order by r.created_at desc, r.id",
            "select count(*) from public.package_refunds r" + where, params, page, size);
    }

    @Transactional
    public Map<String, Object> approve(UUID id, UUID staffId, ExecutiveRefundDecisionRequest request) {
        Map<String, Object> current = lock(id, "REQUESTED");
        UUID packageId = (UUID) current.get("packageId");
        UUID orderId = (UUID) current.get("orderId");
        var params = new MapSqlParameterSource("id", id)
            .addValue("packageId", packageId)
            .addValue("orderId", orderId)
            .addValue("staff", staffId)
            .addValue("now", Timestamp.from(clock.now()))
            .addValue("note", note(request));
        // Khóa đơn trước khi kiểm tra shipper, giống thứ tự khóa khi nhân viên giao nhận package.
        db.queryForObject("select id from public.orders where id = :orderId for update", params, UUID.class);
        Long claimed = db.queryForObject(
            "select count(*) from public.delivery_package_claims where package_id = :packageId", params, Long.class);
        if ((claimed != null && claimed > 0) || current.get("cancelledAt") != null) {
            throw new BusinessException(ErrorCode.REFUND_PACKAGE_NOT_CANCELLABLE);
        }
        BigDecimal amount = (BigDecimal) current.get("packageAmount");
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATE);
        }
        db.update("update public.package_refunds set status = 'APPROVED', refund_amount = :amount, "
            + "reviewed_by = :staff, reviewed_at = :now, review_note = :note, updated_at = :now where id = :id",
            params.addValue("amount", amount));
        db.update("update public.order_packages set cancelled_at = :now, updated_at = :now "
            + "where id = :packageId and cancelled_at is null", params);
        settleOrder(params);
        String name = (String) current.get("packageName");
        notifications.publish((UUID) current.get("customerId"), null, null, "ORDER",
            "Yêu cầu hủy đã được duyệt", "Cancellation approved",
            "Kiện \"" + name + "\" đã được hủy. Chúng tôi sẽ hoàn tiền vào tài khoản bạn cung cấp.",
            "Package \"" + name + "\" was cancelled. The refund will be sent to the account you provided.",
            "/orders/" + orderId);
        return one(id);
    }

    @Transactional
    public Map<String, Object> reject(UUID id, UUID staffId, ExecutiveRefundDecisionRequest request) {
        Map<String, Object> current = lock(id, "REQUESTED");
        String note = note(request);
        if (note == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        db.update("update public.package_refunds set status = 'REJECTED', reviewed_by = :staff, "
            + "reviewed_at = :now, review_note = :note, updated_at = :now where id = :id",
            new MapSqlParameterSource("id", id)
                .addValue("staff", staffId)
                .addValue("now", Timestamp.from(clock.now()))
                .addValue("note", note));
        String name = (String) current.get("packageName");
        notifications.publish((UUID) current.get("customerId"), null, null, "ORDER",
            "Yêu cầu hủy bị từ chối", "Cancellation declined",
            "Yêu cầu hủy kiện \"" + name + "\" không được chấp nhận: " + note,
            "The cancellation request for package \"" + name + "\" was declined: " + note,
            "/orders/" + current.get("orderId"));
        return one(id);
    }

    @Transactional
    public Map<String, Object> markRefunded(UUID id, UUID staffId, ExecutiveRefundDecisionRequest request) {
        Map<String, Object> current = lock(id, "APPROVED");
        String reference = request == null || request.transferReference() == null
            ? "" : request.transferReference().strip();
        if (reference.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        db.update("update public.package_refunds set status = 'REFUNDED', transfer_reference = :reference, "
            + "processed_by = :staff, refunded_at = :now, updated_at = :now where id = :id",
            new MapSqlParameterSource("id", id)
                .addValue("staff", staffId)
                .addValue("now", Timestamp.from(clock.now()))
                .addValue("reference", reference));
        String name = (String) current.get("packageName");
        notifications.publish((UUID) current.get("customerId"), null, null, "PAYMENT",
            "Đã hoàn tiền", "Refund sent",
            "Đã hoàn tiền cho kiện \"" + name + "\". Mã giao dịch: " + reference + ".",
            "The refund for package \"" + name + "\" was sent. Transfer reference: " + reference + ".",
            "/orders/" + current.get("orderId"));
        return one(id);
    }

    // Hủy hết package thì đơn bị hủy; các package còn lại đều đã giao thì đơn hoàn thành.
    private void settleOrder(MapSqlParameterSource params) {
        Long active = db.queryForObject("select count(*) from public.order_packages "
            + "where order_id = :orderId and cancelled_at is null", params, Long.class);
        if (active != null && active == 0) {
            db.update("update public.orders set status = 'CANCELLED', cancelled_at = :now, cancelled_by = :staff, "
                + "cancellation_reason = 'Customer cancellation approved', updated_at = :now "
                + "where id = :orderId and status in ('CONFIRMED', 'PROCESSING')", params);
            return;
        }
        Long undelivered = db.queryForObject("select count(*) from public.order_packages p "
            + "where p.order_id = :orderId and p.cancelled_at is null and not exists (select 1 from "
            + "public.delivery_package_claims c where c.package_id = p.id and c.delivered_at is not null)",
            params, Long.class);
        if (undelivered != null && undelivered == 0) {
            db.update("update public.orders set status = 'COMPLETED', completed_at = :now, updated_at = :now "
                + "where id = :orderId and status in ('CONFIRMED', 'PROCESSING')", params);
        }
    }

    private Map<String, Object> lock(UUID id, String expectedStatus) {
        var params = new MapSqlParameterSource("id", id);
        String status = db.list("select status from public.package_refunds where id = :id for update", params)
            .stream()
            .findFirst()
            .map(row -> (String) row.get("status"))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!expectedStatus.equals(status)) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATE);
        }
        return one(id);
    }

    private Map<String, Object> one(UUID id) {
        return db.one(SELECT + " where r.id = :id", new MapSqlParameterSource("id", id));
    }

    private static String note(ExecutiveRefundDecisionRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()) {
            return null;
        }
        return request.note().strip();
    }
}
