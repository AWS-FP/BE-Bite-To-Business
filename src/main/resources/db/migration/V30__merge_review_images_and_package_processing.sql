ALTER TABLE public.product_reviews ADD COLUMN images JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.product_reviews ADD CONSTRAINT ck_product_reviews_images_array CHECK (jsonb_typeof(images) = 'array');

UPDATE public.product_reviews r
SET images = grouped.images
FROM (
    SELECT review_id, jsonb_agg(jsonb_build_object(
        'id', id, 'image_url', image_url, 'cloudinary_public_id', cloudinary_public_id,
        'sort_order', sort_order, 'created_at', created_at
    ) ORDER BY sort_order, created_at, id) AS images
    FROM public.review_images GROUP BY review_id
) grouped
WHERE r.id = grouped.review_id;

DROP TABLE public.review_images;

ALTER TABLE public.order_packages
    ADD COLUMN warehouse_processed_by UUID REFERENCES public.users(id) ON DELETE RESTRICT,
    ADD COLUMN warehouse_processed_at TIMESTAMPTZ,
    ADD COLUMN warehouse_processing_note TEXT;

UPDATE public.order_packages p
SET warehouse_processed_by = c.processed_by, warehouse_processed_at = c.processed_at,
    warehouse_processing_note = c.note
FROM public.cancelled_package_processing c
WHERE c.order_package_id = p.id;

ALTER TABLE public.order_packages ADD CONSTRAINT ck_package_warehouse_processing CHECK (
    (warehouse_processed_by IS NULL AND warehouse_processed_at IS NULL AND warehouse_processing_note IS NULL)
    OR (warehouse_processed_by IS NOT NULL AND warehouse_processed_at IS NOT NULL AND cancelled_at IS NOT NULL)
);
DROP TABLE public.cancelled_package_processing;
