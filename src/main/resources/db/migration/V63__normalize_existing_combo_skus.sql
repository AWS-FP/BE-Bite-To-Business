-- Give existing combos the same SKU convention as newly created combos (V62).
-- Preserve product UUIDs, image/public IDs, component links and historical snapshots.
-- Run during deployment before accepting combo writes from the application.
-- Lock the sequence first (same order as combo creation), then the product table.
ALTER SEQUENCE public.combo_sku_seq NO CYCLE;
LOCK TABLE public.products IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    highest_sku bigint;
    sequence_last bigint;
    sequence_called boolean;
    consumed bigint;
    legacy_count bigint;
    legacy record;
    new_sku text;
BEGIN
    -- Include ordinary products/imports and case variants so no occupied code is reused.
    SELECT COALESCE(MAX(substring(upper(sku) FROM '^BTB-COMBO-([0-9]{6})$')::bigint), 0)
    INTO highest_sku
    FROM public.products
    WHERE upper(sku) ~ '^BTB-COMBO-[0-9]{6}$';

    SELECT last_value, is_called INTO sequence_last, sequence_called
    FROM public.combo_sku_seq;
    consumed := CASE WHEN sequence_called THEN sequence_last ELSE sequence_last - 1 END;

    SELECT count(*) INTO legacy_count
    FROM public.products p
    JOIN public.combos c ON c.product_id = p.id
    WHERE p.unit_code = 'COMBO'
      AND (p.sku !~ '^BTB-COMBO-[0-9]{6}$' OR p.sku = 'BTB-COMBO-000000');

    -- Never rewind a sequence or exceed its six-digit capacity.
    IF GREATEST(highest_sku, consumed) + legacy_count > 999999 THEN
        RAISE EXCEPTION 'Not enough remaining BTB combo SKU numbers to normalize existing combos';
    END IF;

    IF highest_sku > consumed THEN
        PERFORM setval('public.combo_sku_seq', highest_sku, true);
    END IF;

    -- Deterministic order: OFFICE before PASTA for the original BTB seed data.
    FOR legacy IN
        SELECT p.id, p.sku
        FROM public.products p
        JOIN public.combos c ON c.product_id = p.id
        WHERE p.unit_code = 'COMBO'
          AND (p.sku !~ '^BTB-COMBO-[0-9]{6}$' OR p.sku = 'BTB-COMBO-000000')
        ORDER BY p.sku COLLATE "C", p.id
    LOOP
        new_sku := 'BTB-COMBO-' || lpad(nextval('public.combo_sku_seq')::text, 6, '0');
        UPDATE public.products
        SET sku = new_sku, updated_at = now()
        WHERE id = legacy.id;
    END LOOP;
END;
$$;

-- Intentionally leave quote_items, quote_revisions, order_items and order_combo_items
-- unchanged: these store snapshots, not the current catalog SKU.
