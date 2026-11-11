CREATE TABLE public.quote_address_email_outbox (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL,
    quote_package_id UUID NOT NULL,
    quote_revision INTEGER NOT NULL CHECK (quote_revision > 0),
    recipient_email VARCHAR(254) NOT NULL,
    quote_number VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claim_id UUID,
    claimed_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_quote_email_package FOREIGN KEY (quote_package_id, quote_id)
        REFERENCES public.quote_packages (id, quote_id) ON DELETE RESTRICT,
    CONSTRAINT uq_quote_email_review UNIQUE (quote_id, quote_package_id, quote_revision)
);

CREATE INDEX ix_quote_email_pending ON public.quote_address_email_outbox (status, next_attempt_at);
ALTER TABLE public.quote_address_email_outbox ENABLE ROW LEVEL SECURITY;
