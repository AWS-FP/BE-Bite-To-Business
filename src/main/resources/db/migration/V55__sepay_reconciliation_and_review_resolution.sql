-- Khoản tiền SePay chưa khớp đơn (result = 'REVIEW'): Admin chọn hoàn tiền, khôi phục đơn hoặc bỏ qua.
-- source cho biết giao dịch đến từ webhook hay từ job đối soát định kỳ (khi webhook bị sót).
ALTER TABLE public.sepay_webhook_events
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'WEBHOOK',
    ADD COLUMN content TEXT,
    ADD COLUMN resolution VARCHAR(20),
    ADD COLUMN resolution_note TEXT,
    ADD COLUMN refund_reference VARCHAR(100),
    ADD COLUMN resolved_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    ADD COLUMN resolved_at TIMESTAMPTZ,
    -- Email "đã nhận tiền nhưng chưa ghi nhận được" và email báo kết quả xử lý.
    ADD COLUMN customer_notified_at TIMESTAMPTZ,
    ADD COLUMN resolution_notified_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_sepay_event_source CHECK (source IN ('WEBHOOK', 'RECONCILIATION')),
    ADD CONSTRAINT ck_sepay_event_resolution CHECK (
        (resolution IS NULL AND resolved_at IS NULL AND resolved_by IS NULL
            AND refund_reference IS NULL AND resolution_notified_at IS NULL)
        OR (result = 'REVIEW' AND resolved_at IS NOT NULL
            AND resolution IN ('REFUNDED', 'RESTORED', 'IGNORED')
            AND (resolution <> 'REFUNDED'
                OR (refund_reference IS NOT NULL AND BTRIM(refund_reference) <> ''))
            AND (resolution <> 'IGNORED'
                OR (resolution_note IS NOT NULL AND BTRIM(resolution_note) <> '')))
    );

-- Trang Admin "Thanh toán cần xử lý" và job gửi email chỉ đọc các khoản chưa xử lý / chưa báo.
CREATE INDEX ix_sepay_event_open_review
    ON public.sepay_webhook_events (received_at DESC)
    WHERE result = 'REVIEW' AND resolution IS NULL;

CREATE INDEX ix_sepay_event_pending_notice
    ON public.sepay_webhook_events (received_at)
    WHERE result = 'REVIEW' AND payment_id IS NOT NULL
        AND (customer_notified_at IS NULL OR (resolution IS NOT NULL AND resolution_notified_at IS NULL));
