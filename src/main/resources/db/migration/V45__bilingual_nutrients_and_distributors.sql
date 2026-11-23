-- 1) Tên thành phần dinh dưỡng song ngữ: nutrient = tiếng Việt, nutrient_en = tiếng Anh.
--    Dữ liệu seed cũ dạng "Năng lượng (Energy)" được tách thành "Năng lượng" / "Energy".
ALTER TABLE public.product_nutrition_facts
    ADD COLUMN nutrient_en VARCHAR(100);

UPDATE public.product_nutrition_facts
SET nutrient_en = CASE
        WHEN nutrient ~ '\([^()]+\)$' THEN BTRIM(SUBSTRING(nutrient FROM '\(([^()]+)\)$'))
        ELSE nutrient
    END,
    nutrient = CASE
        WHEN nutrient ~ '\([^()]+\)$' THEN BTRIM(REGEXP_REPLACE(nutrient, '\s*\([^()]+\)$', ''))
        ELSE nutrient
    END;

ALTER TABLE public.product_nutrition_facts
    ALTER COLUMN nutrient_en SET NOT NULL,
    ADD CONSTRAINT ck_product_nutrition_facts_nutrient_en CHECK (BTRIM(nutrient_en) <> '');

-- 2) Nhà phân phối: danh mục riêng để chọn từ dropdown, sản phẩm tham chiếu bằng khóa ngoại.
CREATE TABLE public.distributors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_distributors_name_nonblank CHECK (BTRIM(name) <> '')
);
CREATE UNIQUE INDEX ux_distributors_name_ci ON public.distributors (LOWER(BTRIM(name)));
ALTER TABLE public.distributors ENABLE ROW LEVEL SECURITY;

-- Toàn bộ sản phẩm hiện có ghi "phân phối trực tiếp bởi MD Foods" trong phần nguồn gốc.
INSERT INTO public.distributors (name) VALUES ('MD Foods');

ALTER TABLE public.products
    ADD COLUMN distributor_id UUID REFERENCES public.distributors (id) ON DELETE RESTRICT;
CREATE INDEX ix_products_distributor_id ON public.products (distributor_id)
    WHERE distributor_id IS NOT NULL;

UPDATE public.products
SET distributor_id = (SELECT id FROM public.distributors WHERE name = 'MD Foods')
WHERE distributor_id IS NULL;
