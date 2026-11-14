-- Newsletter belongs to the Customer module. No change to ecommerce/promotions.
CREATE TABLE IF NOT EXISTS public.newsletter_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    consented_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_newsletter_email_not_blank CHECK (BTRIM(email) <> ''),
    CONSTRAINT ck_newsletter_email_normalized CHECK (email = LOWER(BTRIM(email)))
);
CREATE INDEX IF NOT EXISTS ix_newsletter_subscriptions_active
    ON public.newsletter_subscriptions (is_active) WHERE is_active = TRUE;
-- Public API uses authenticated server JDBC. Do not expose this list to anon Supabase users.
ALTER TABLE public.newsletter_subscriptions ENABLE ROW LEVEL SECURITY;

-- One delivery per subscription and promotion, protecting against repeated emails.
CREATE TABLE public.newsletter_promotion_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES public.newsletter_subscriptions(id) ON DELETE CASCADE,
    promotion_id UUID NOT NULL REFERENCES public.promotions(id) ON DELETE CASCADE,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENDING','SENT')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_newsletter_promotion_delivery UNIQUE(subscription_id,promotion_id)
);
CREATE INDEX ix_newsletter_deliveries_pending
    ON public.newsletter_promotion_deliveries (next_attempt_at, created_at)
    WHERE status IN ('PENDING','SENDING');
ALTER TABLE public.newsletter_promotion_deliveries ENABLE ROW LEVEL SECURITY;
