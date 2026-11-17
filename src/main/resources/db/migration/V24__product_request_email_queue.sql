ALTER TABLE public.product_request_responses
    ADD COLUMN email_status VARCHAR(10) NOT NULL DEFAULT 'SKIPPED',
    ADD COLUMN email_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_email_attempt_at TIMESTAMPTZ;

ALTER TABLE public.product_request_responses
    ADD CONSTRAINT ck_product_response_email_status CHECK (email_status IN ('SKIPPED', 'PENDING', 'SENT')),
    ADD CONSTRAINT ck_product_response_email_attempts CHECK (email_attempts >= 0);

CREATE INDEX ix_product_response_email_queue
    ON public.product_request_responses (next_email_attempt_at, created_at) WHERE email_status = 'PENDING';
