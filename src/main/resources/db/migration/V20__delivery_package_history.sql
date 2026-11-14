ALTER TABLE public.delivery_package_claims
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_delivery_claim_delivered_at
        CHECK (delivered_at IS NULL OR delivered_at >= claimed_at);

CREATE INDEX ix_delivery_package_claims_history
    ON public.delivery_package_claims (delivery_staff_id, delivered_at DESC, package_id)
    WHERE delivered_at IS NOT NULL;
