ALTER TABLE public.registration_requests
    DROP CONSTRAINT ck_registration_requests_status,
    DROP CONSTRAINT ck_registration_requests_reviewed_state,
    DROP CONSTRAINT ck_registration_requests_otp_state,
    DROP CONSTRAINT ck_registration_requests_activation;

ALTER TABLE public.registration_requests
    ADD COLUMN otp_delivery_state VARCHAR(10);

UPDATE public.registration_requests
SET status = 'AWAITING_ACTIVATION',
    otp_delivery_state = CASE WHEN otp_sent_at IS NULL THEN 'FAILED' ELSE 'SENT' END
WHERE status = 'APPROVED' AND activated_at IS NULL;

UPDATE public.registration_requests
SET otp_delivery_state = 'SENT'
WHERE status = 'APPROVED' AND activated_at IS NOT NULL AND otp_sent_at IS NOT NULL;

ALTER TABLE public.registration_requests
    ADD CONSTRAINT ck_registration_requests_status CHECK (status IN ('PENDING', 'AWAITING_ACTIVATION', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_registration_requests_reviewed_state CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND review_note IS NULL)
        OR (status IN ('AWAITING_ACTIVATION', 'APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_at >= submitted_at)
    ),
    ADD CONSTRAINT ck_registration_requests_otp_state CHECK (
        otp_sent_at IS NULL OR (status IN ('AWAITING_ACTIVATION', 'APPROVED') AND reviewed_at IS NOT NULL AND otp_sent_at >= reviewed_at)
    ),
    ADD CONSTRAINT ck_registration_requests_activation CHECK (
        (status = 'APPROVED' AND activated_at IS NOT NULL AND otp_sent_at IS NOT NULL AND activated_at >= otp_sent_at)
        OR (status <> 'APPROVED' AND activated_at IS NULL)
    ),
    ADD CONSTRAINT ck_registration_requests_otp_delivery_state CHECK (
        otp_delivery_state IS NULL OR otp_delivery_state IN ('QUEUED', 'SENT', 'FAILED')
    );
