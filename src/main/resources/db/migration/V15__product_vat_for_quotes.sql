ALTER TABLE public.products
    ADD COLUMN vat_rate NUMERIC(5, 2),
    ADD CONSTRAINT ck_products_vat_rate CHECK (vat_rate IS NULL OR (vat_rate >= 0 AND vat_rate <= 100));

-- Phân biệt thuế suất chưa nhập với thuế suất thực sự bằng 0%.
ALTER TABLE public.quote_items
    ADD COLUMN vat_rate_configured BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE public.product_vat_rate_changes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    previous_rate NUMERIC(5, 2),
    new_rate NUMERIC(5, 2) NOT NULL CHECK (new_rate >= 0 AND new_rate <= 100),
    changed_by UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_product_vat_rate_changes_product ON public.product_vat_rate_changes (product_id, changed_at DESC);
ALTER TABLE public.product_vat_rate_changes ENABLE ROW LEVEL SECURITY;
