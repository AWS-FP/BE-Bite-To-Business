ALTER TABLE public.product_request_responses
    ADD COLUMN email_claim_token UUID,
    ADD COLUMN email_claimed_until TIMESTAMPTZ;

ALTER TABLE public.product_request_responses
    ADD CONSTRAINT ck_product_response_email_claim CHECK (
        (email_claim_token IS NULL AND email_claimed_until IS NULL)
        OR (email_claim_token IS NOT NULL AND email_claimed_until IS NOT NULL AND email_status = 'PENDING')
    );
