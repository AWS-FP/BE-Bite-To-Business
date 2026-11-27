-- Reserve a dedicated, non-recycling sequence for newly created BTB combo SKUs.
-- Existing product and combo identifiers are never rewritten.
CREATE SEQUENCE public.combo_sku_seq AS bigint
    MINVALUE 1 MAXVALUE 999999 START WITH 1 INCREMENT BY 1 NO CYCLE;

-- Seed above any existing code in this namespace, including imported codes.
WITH existing AS (
    SELECT COALESCE(MAX(substring(upper(sku) FROM '^BTB-COMBO-([0-9]{6})$')::bigint), 0) AS last_number
    FROM public.products
    WHERE upper(sku) ~ '^BTB-COMBO-[0-9]{6}$'
)
SELECT setval('public.combo_sku_seq', GREATEST(last_number, 1), last_number >= 1)
FROM existing;

-- The backend database user must have USAGE on this sequence (the owner does by default).
REVOKE ALL ON SEQUENCE public.combo_sku_seq FROM PUBLIC;
