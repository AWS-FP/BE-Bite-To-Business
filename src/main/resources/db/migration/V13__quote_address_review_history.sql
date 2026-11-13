CREATE TABLE public.quote_address_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id UUID NOT NULL,
    quote_package_id UUID NOT NULL,
    quote_revision INTEGER NOT NULL CHECK (quote_revision > 0),
    reviewed_by UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    result VARCHAR(20) NOT NULL,
    reason TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_quote_address_reviews_package FOREIGN KEY (quote_package_id, quote_id)
        REFERENCES public.quote_packages (id, quote_id) ON DELETE RESTRICT,
    CONSTRAINT ck_quote_address_reviews_result CHECK (result IN ('VALID', 'NEEDS_UPDATE')),
    CONSTRAINT ck_quote_address_reviews_reason CHECK (
        result <> 'NEEDS_UPDATE' OR (reason IS NOT NULL AND BTRIM(reason) <> '')
    )
);

CREATE INDEX ix_quote_address_reviews_quote
    ON public.quote_address_reviews (quote_id, quote_revision, reviewed_at);

ALTER TABLE public.quote_address_reviews ENABLE ROW LEVEL SECURITY;
