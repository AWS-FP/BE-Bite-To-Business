ALTER TABLE public.registration_requests
    ADD COLUMN otp_generated_at TIMESTAMPTZ;

-- Existing successful deliveries were generated no later than their recorded send time.
UPDATE public.registration_requests
SET otp_generated_at = otp_sent_at
WHERE otp_sent_at IS NOT NULL;

ALTER TABLE public.registration_requests
    DROP CONSTRAINT ck_registration_requests_otp_delivery_state,
    DROP CONSTRAINT ck_registration_requests_activation;

ALTER TABLE public.registration_requests
    ADD CONSTRAINT ck_registration_requests_otp_delivery_state CHECK (
        otp_delivery_state IS NULL OR otp_delivery_state IN ('QUEUED', 'SENT', 'FAILED', 'UNCONFIRMED', 'VERIFIED')
    ),
    ADD CONSTRAINT ck_registration_requests_activation CHECK (
        (status = 'APPROVED' AND activated_at IS NOT NULL AND otp_generated_at IS NOT NULL
            AND activated_at >= otp_generated_at)
        OR (status <> 'APPROVED' AND activated_at IS NULL)
    );
