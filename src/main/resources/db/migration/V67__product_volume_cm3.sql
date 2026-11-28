-- Thể tích sản phẩm đổi từ m³ sang cm³ để staff nhập số dễ hiểu (chai 500ml ≈ 500 cm³, không còn 0.0005 m³).
-- 1 m³ = 1.000.000 cm³. Giá trị cũ có tối đa 6 số lẻ nên sau khi nhân là số nguyên, không mất dữ liệu.
ALTER TABLE public.product_details DROP CONSTRAINT ck_product_details_volume;

ALTER TABLE public.product_details RENAME COLUMN volume_m3 TO volume_cm3;

-- Tối đa 12 chữ số phần nguyên (đủ cho 999.999 m³ cũ) và 2 số lẻ như kích thước cm.
ALTER TABLE public.product_details
    ALTER COLUMN volume_cm3 TYPE NUMERIC(14, 2) USING volume_cm3 * 1000000;

ALTER TABLE public.product_details
    ADD CONSTRAINT ck_product_details_volume CHECK (volume_cm3 > 0);

COMMENT ON COLUMN public.product_details.volume_cm3 IS 'Thể tích một đơn vị bán, tính bằng cm³.';
