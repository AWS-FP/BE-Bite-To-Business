ALTER TABLE public.registration_requests
    ADD COLUMN otp_resend_requested_at timestamptz;
