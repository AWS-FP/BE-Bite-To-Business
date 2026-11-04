ALTER TABLE public.payments DROP CONSTRAINT ck_payments_method;
ALTER TABLE public.payments ADD CONSTRAINT ck_payments_method
    CHECK (payment_method IN ('MOMO', 'VISA', 'MASTERCARD', 'BANK_TRANSFER'));

-- Lưu các giao dịch đã xác thực để chống trùng và đối soát trường hợp chưa khớp đơn.
CREATE TABLE public.sepay_webhook_events (
    transaction_id BIGINT PRIMARY KEY,
    gateway VARCHAR(100) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    sub_account VARCHAR(100),
    payment_code VARCHAR(50),
    transfer_type VARCHAR(10) NOT NULL,
    amount NUMERIC(16, 0) NOT NULL,
    reference_code VARCHAR(100),
    payment_id UUID REFERENCES public.payments (id) ON DELETE RESTRICT,
    result VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_sepay_event_amount CHECK (amount > 0),
    CONSTRAINT ck_sepay_event_result CHECK (result IN ('RECEIVED', 'MATCHED', 'REVIEW'))
);

CREATE UNIQUE INDEX ux_sepay_event_reference
    ON public.sepay_webhook_events (gateway, reference_code)
    WHERE reference_code IS NOT NULL AND reference_code <> '';

ALTER TABLE public.sepay_webhook_events ENABLE ROW LEVEL SECURITY;
