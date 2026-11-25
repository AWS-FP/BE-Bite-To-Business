package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.SepayWebhookEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SepayWebhookEventRepository extends JpaRepository<SepayWebhookEvent, Long> {
    boolean existsByGatewayAndReferenceCode(String gateway, String referenceCode);

    List<SepayWebhookEvent> findTop100ByResultOrderByReceivedAtDesc(String result);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SepayWebhookEvent e where e.transactionId = :id")
    Optional<SepayWebhookEvent> findForUpdate(
            @Param("id")
            Long id);

    /** Một dòng trên trang Admin "Thanh toán cần xử lý", kèm đơn/khách nếu tìm được từ mã thanh toán. */
    interface ReviewRow {
        Long getTransactionId();

        String getSource();

        String getGateway();

        String getPaymentCode();

        BigDecimal getAmount();

        String getReferenceCode();

        String getContent();

        String getReviewReason();

        Instant getReceivedAt();

        UUID getPaymentId();

        String getPaymentStatus();

        BigDecimal getPaymentAmount();

        UUID getOrderId();

        String getOrderNumber();

        String getOrderStatus();

        BigDecimal getOrderTotal();

        String getContactName();

        String getContactEmail();

        String getContactPhone();

        String getResolution();

        String getResolutionNote();

        String getRefundReference();

        Instant getResolvedAt();

        String getResolvedByName();

        Instant getCustomerNotifiedAt();
    }

    @Query(value = """
            select e.transaction_id as "transactionId", e.source, e.gateway, e.payment_code as "paymentCode",
                e.amount, e.reference_code as "referenceCode", e.content, e.review_reason as "reviewReason",
                e.received_at as "receivedAt", e.payment_id as "paymentId", p.status as "paymentStatus",
                p.amount as "paymentAmount", o.id as "orderId", o.order_number as "orderNumber",
                o.status as "orderStatus", o.total_amount as "orderTotal", o.contact_name as "contactName",
                o.contact_email as "contactEmail", o.contact_phone as "contactPhone",
                e.resolution, e.resolution_note as "resolutionNote", e.refund_reference as "refundReference",
                e.resolved_at as "resolvedAt", nullif(concat_ws(' ', u.last_name, u.first_name), '') as "resolvedByName",
                e.customer_notified_at as "customerNotifiedAt"
            from public.sepay_webhook_events e
            left join public.payments p on p.id = e.payment_id
                or (e.payment_id is null and e.payment_code is not null and p.payment_number = e.payment_code)
            left join public.orders o on o.id = p.order_id
            left join public.users u on u.id = e.resolved_by
            where e.result = 'REVIEW'
                and ((:resolved = false and e.resolution is null) or (:resolved = true and e.resolution is not null))
            order by e.received_at desc
            """, countQuery = """
            select count(*) from public.sepay_webhook_events e
            where e.result = 'REVIEW'
                and ((:resolved = false and e.resolution is null) or (:resolved = true and e.resolution is not null))
            """, nativeQuery = true)
    Page<ReviewRow> findReviews(@Param("resolved") boolean resolved, Pageable pageable);

    @Query(value = """
            select count(*) from public.sepay_webhook_events
            where result = 'REVIEW' and resolution is null
            """, nativeQuery = true)
    long countOpenReviews();

    /** Khoản cần gửi email cho khách: lần đầu ("đã nhận tiền") hoặc sau khi Admin xử lý. */
    interface NoticeRow {
        Long getTransactionId();

        BigDecimal getAmount();

        String getReviewReason();

        String getResolution();

        String getRefundReference();

        Instant getCustomerNotifiedAt();

        String getOrderNumber();

        String getContactName();

        String getContactEmail();
    }

    @Query(value = """
            select e.transaction_id as "transactionId", e.amount, e.review_reason as "reviewReason",
                e.resolution, e.refund_reference as "refundReference",
                e.customer_notified_at as "customerNotifiedAt", o.order_number as "orderNumber",
                o.contact_name as "contactName", o.contact_email as "contactEmail"
            from public.sepay_webhook_events e
            join public.payments p on p.id = e.payment_id
            join public.orders o on o.id = p.order_id
            where e.result = 'REVIEW' and e.payment_id is not null
                and (e.customer_notified_at is null
                    or (e.resolution is not null and e.resolution_notified_at is null))
                and e.received_at >= :since
            order by e.received_at
            limit 20
            """, nativeQuery = true)
    List<NoticeRow> findPendingNotices(@Param("since") Instant since);

    @Modifying
    @Query(value = """
            update public.sepay_webhook_events
            set customer_notified_at = coalesce(customer_notified_at, :now),
                resolution_notified_at = case when resolution is not null then :now else resolution_notified_at end
            where transaction_id = :id
            """, nativeQuery = true)
    int markNotified(@Param("id") Long id, @Param("now") Instant now);
}
