-- Multiple campaigns may reference the same product. Preserve products.promotion_id
-- as the existing legacy/default campaign; do not overwrite old assignments.
ALTER TABLE public.promotions
  ADD COLUMN promotion_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE public.promotions
  ADD CONSTRAINT ck_promotions_promotion_type
  CHECK (promotion_type IN ('STANDARD', 'FLASH_SALE'));

CREATE TABLE public.promotion_products (
  promotion_id UUID NOT NULL REFERENCES public.promotions(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES public.products(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_promotion_products PRIMARY KEY (promotion_id, product_id)
);
CREATE INDEX ix_promotion_products_product ON public.promotion_products (product_id, promotion_id);
-- Previous products.promotion_id remains authoritative for historical relationships;
-- new assignments coexist in promotion_products without rewriting it.
