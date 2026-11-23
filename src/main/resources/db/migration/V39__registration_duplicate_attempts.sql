-- Registration attempts whose email or phone already belongs to an account.
-- The public register API answers them exactly like a normal request (no "already exists"
-- error that would let anyone probe which emails/phones are registered); they are stored here
-- for admins, flagged as duplicates, and auto-rejected by email after a short random delay.
CREATE TABLE public.registration_duplicate_attempts (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    duplicate_field VARCHAR(20) NOT NULL,
    existing_user_id UUID REFERENCES public.users (id) ON DELETE SET NULL,
    email_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    email_attempts INTEGER NOT NULL DEFAULT 0,
    next_email_at TIMESTAMPTZ NOT NULL,
    email_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_registration_duplicates_field CHECK (
        duplicate_field IN ('EMAIL', 'PHONE', 'EMAIL_AND_PHONE')
    ),
    -- SKIPPED: an auto-rejection already went to this address recently (anti email-bombing).
    CONSTRAINT ck_registration_duplicates_email_status CHECK (
        email_status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')
    ),
    CONSTRAINT ck_registration_duplicates_attempts CHECK (email_attempts >= 0),
    CONSTRAINT ck_registration_duplicates_sent CHECK (
        (email_status = 'SENT' AND email_sent_at IS NOT NULL)
        OR (email_status <> 'SENT' AND email_sent_at IS NULL)
    )
);

CREATE INDEX ix_registration_duplicates_due
    ON public.registration_duplicate_attempts (next_email_at)
    WHERE email_status = 'PENDING';

CREATE INDEX ix_registration_duplicates_created
    ON public.registration_duplicate_attempts (created_at DESC, id);

CREATE INDEX ix_registration_duplicates_email
    ON public.registration_duplicate_attempts (lower(email), created_at DESC);

ALTER TABLE public.registration_duplicate_attempts ENABLE ROW LEVEL SECURITY;
