ALTER TABLE public.promotions
    DROP CONSTRAINT ck_promotions_publication_metadata,
    DROP CONSTRAINT ck_promotions_published_state,
    DROP CONSTRAINT ck_promotions_revision,
    DROP COLUMN published_by,
    DROP COLUMN published_revision,
    DROP COLUMN revision,
    ADD COLUMN cloudinary_public_id VARCHAR(255),
    ADD COLUMN image_url TEXT,
    ADD CONSTRAINT ck_promotions_published_state CHECK (
        status <> 'PUBLISHED' OR (
            published_at IS NOT NULL AND content_vi IS NOT NULL AND content_en IS NOT NULL
        )
    );

ALTER TABLE public.products ADD COLUMN vat_updated_at TIMESTAMPTZ;

UPDATE public.products p
SET vat_updated_at = history.last_changed_at
FROM (
    SELECT product_id, MAX(changed_at) AS last_changed_at
    FROM public.product_vat_rate_changes
    GROUP BY product_id
) history
WHERE p.id = history.product_id;

DROP TABLE public.product_vat_rate_changes;

ALTER TABLE public.coupons
    ADD COLUMN product_scope VARCHAR(20) NOT NULL DEFAULT 'ALL',
    ADD CONSTRAINT ck_coupons_product_scope CHECK (product_scope IN ('ALL', 'SELECTED')),
    ADD CONSTRAINT ck_coupons_shipping_product_scope CHECK (applies_to = 'PRODUCT' OR product_scope = 'ALL');

CREATE TABLE public.coupon_products (
    coupon_id UUID NOT NULL REFERENCES public.coupons (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    PRIMARY KEY (coupon_id, product_id)
);

CREATE INDEX ix_coupon_products_product ON public.coupon_products (product_id, coupon_id);
ALTER TABLE public.coupon_products ENABLE ROW LEVEL SECURITY;
