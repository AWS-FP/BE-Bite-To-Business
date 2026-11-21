-- Transactional outbox dùng chung cho việc bất đồng bộ (email, thông báo, đồng bộ ngoài).
-- Service nghiệp vụ ghi một dòng outbox_events trong CÙNG transaction với thay đổi nghiệp vụ.
-- Relay (job trong BE hoặc Lambda chạy theo lịch EventBridge Scheduler) lấy dòng PENDING bằng
-- FOR UPDATE SKIP LOCKED, đẩy lên SQS/EventBridge rồi đánh dấu PUBLISHED.
-- Consumer (Lambda đọc SQS) ghi processed_events trước khi làm việc để chống xử lý trùng.
-- Các bảng hàng đợi cũ (quote_address_email_outbox, newsletter_promotion_deliveries, OTP đăng ký,
-- email yêu cầu sản phẩm) giữ nguyên; chuyển dần sang outbox_events theo từng loại việc.

CREATE TABLE public.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Tên event dạng <aggregate>.<hành động>, ví dụ payment.succeeded, order.reservation_expired.
    event_type VARCHAR(80) NOT NULL,
    -- Phiên bản schema của payload để consumer xử lý tương thích ngược.
    event_version SMALLINT NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- Khóa nghiệp vụ duy nhất, ví dụ payment.succeeded:<payment_id>. Ghi lại cùng khóa sẽ bị bỏ qua.
    idempotency_key VARCHAR(200) NOT NULL,
    -- Nơi relay đẩy event tới: SQS (một consumer, cần thứ tự/thử lại) hoặc EVENTBRIDGE (nhiều consumer).
    destination VARCHAR(16) NOT NULL DEFAULT 'SQS',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 10,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claim_id UUID,
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    -- MessageId của SQS hoặc EventId của EventBridge sau khi đẩy thành công.
    broker_message_id VARCHAR(200),
    last_error VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_outbox_events_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_outbox_events_type CHECK (event_type ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'),
    CONSTRAINT ck_outbox_events_aggregate_nonblank CHECK (BTRIM(aggregate_type) <> ''),
    CONSTRAINT ck_outbox_events_key_nonblank CHECK (BTRIM(idempotency_key) <> ''),
    CONSTRAINT ck_outbox_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_outbox_events_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_events_destination CHECK (destination IN ('SQS', 'EVENTBRIDGE')),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT ck_outbox_events_claim CHECK (
        (status = 'PUBLISHING' AND claim_id IS NOT NULL AND claimed_at IS NOT NULL)
        OR (status <> 'PUBLISHING' AND claim_id IS NULL AND claimed_at IS NULL)
    ),
    CONSTRAINT ck_outbox_events_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

-- Relay chỉ quét các dòng còn việc; index partial giữ nhỏ khi bảng có nhiều dòng PUBLISHED.
CREATE INDEX ix_outbox_events_ready
    ON public.outbox_events (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PUBLISHING');
CREATE INDEX ix_outbox_events_aggregate
    ON public.outbox_events (aggregate_type, aggregate_id, created_at);
-- Dọn dẹp định kỳ các event đã đẩy lâu ngày.
CREATE INDEX ix_outbox_events_published_at
    ON public.outbox_events (published_at)
    WHERE status = 'PUBLISHED';
-- Theo dõi event hỏng cần người xem lại.
CREATE INDEX ix_outbox_events_failed
    ON public.outbox_events (created_at)
    WHERE status = 'FAILED';

ALTER TABLE public.outbox_events ENABLE ROW LEVEL SECURITY;

-- Bảng chống xử lý trùng phía consumer. SQS standard giao ít nhất một lần nên một event có thể đến hai lần.
-- Consumer chạy INSERT ... ON CONFLICT DO NOTHING trong transaction của nó; không chèn được nghĩa là đã xử lý.
CREATE TABLE public.processed_events (
    -- Tên consumer, ví dụ customer-email-lambda, notification-lambda.
    consumer VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    attempts INTEGER NOT NULL DEFAULT 1,
    -- Kết quả ngắn gọn (ví dụ message id của SMTP/SES) để đối soát; không lưu dữ liệu mật.
    result_ref VARCHAR(200),
    last_error VARCHAR(500),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,

    CONSTRAINT pk_processed_events PRIMARY KEY (consumer, event_id),
    CONSTRAINT ck_processed_events_consumer_nonblank CHECK (BTRIM(consumer) <> ''),
    CONSTRAINT ck_processed_events_status CHECK (status IN ('PROCESSING', 'DONE', 'SKIPPED', 'FAILED')),
    CONSTRAINT ck_processed_events_attempts CHECK (attempts > 0),
    CONSTRAINT ck_processed_events_done CHECK ((status IN ('DONE', 'SKIPPED')) = (processed_at IS NOT NULL))
);

-- Không đặt FK tới outbox_events: event PUBLISHED có thể bị dọn trước khi bản ghi chống trùng hết hạn.
CREATE INDEX ix_processed_events_event ON public.processed_events (event_id);
CREATE INDEX ix_processed_events_processed_at ON public.processed_events (processed_at)
    WHERE processed_at IS NOT NULL;

ALTER TABLE public.processed_events ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE public.outbox_events IS
    'Transactional outbox: ghi cùng transaction nghiệp vụ, relay đẩy lên SQS/EventBridge.';
COMMENT ON TABLE public.processed_events IS
    'Chống xử lý trùng phía consumer (Lambda) theo cặp consumer + event_id.';
