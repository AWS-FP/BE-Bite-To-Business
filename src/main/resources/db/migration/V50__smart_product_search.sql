-- Repeatable objects have no version slot in the numbered migration chain.
CREATE SCHEMA IF NOT EXISTS catalog_search;
GRANT USAGE ON SCHEMA catalog_search TO PUBLIC;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;
-- Supabase and self-hosted installs can use different extension schemas.
SELECT set_config('search_path', 'pg_catalog,catalog_search,' || quote_ident(n.nspname), true)
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'pg_trgm';

-- Fold punctuation after removing combining marks so decomposed Vietnamese stays searchable.
CREATE OR REPLACE FUNCTION catalog_search.fold_v1(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN trim(regexp_replace(
    translate(lower(regexp_replace(normalize(coalesce(value, ''), NFD),
        U&'[\0300-\036f]', '', 'g')), 'đĐ', 'dd'),
    '[^[:alnum:]]+', ' ', 'g'));

-- Packaging aliases are taken from the storefront/logistics VI/EN unit labels.
CREATE OR REPLACE FUNCTION catalog_search.document_v1(vi text, en text, sku text, unit text)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN catalog_search.fold_v1(vi || ' ' || en || ' ' || sku || ' ' || unit || ' ' ||
    CASE unit WHEN 'CASE' THEN 'thùng' WHEN 'BOX' THEN 'hộp' WHEN 'BAG' THEN 'túi'
        WHEN 'BOTTLE' THEN 'chai' WHEN 'PACK' THEN 'gói' WHEN 'TRAY' THEN 'khay'
        WHEN 'PIECE' THEN 'cái' WHEN 'CAN' THEN 'lon' WHEN 'TIN' THEN 'hộp thiếc'
        WHEN 'TUB' THEN 'hộp nhựa' ELSE '' END);

CREATE OR REPLACE VIEW catalog_search.visible_categories WITH (security_invoker = true) AS
    WITH RECURSIVE visible(id) AS (
        SELECT id FROM public.categories WHERE parent_id IS NULL AND is_active
        UNION
        SELECT c.id FROM public.categories c JOIN visible v ON c.parent_id = v.id WHERE c.is_active
    ) SELECT id FROM visible;
GRANT SELECT ON catalog_search.visible_categories TO PUBLIC;

CREATE OR REPLACE FUNCTION catalog_search.ranked(
    raw_query text, brand_filter uuid DEFAULT NULL, category_filter uuid[] DEFAULT NULL
)
RETURNS TABLE(product_id uuid, relevance double precision)
LANGUAGE sql STABLE SECURITY INVOKER
SET search_path FROM CURRENT
AS $ranked$
    WITH needle AS (
        SELECT catalog_search.fold_v1(raw_query) AS q
    ), terms AS (
        SELECT DISTINCT word FROM needle, unnest(string_to_array(q, ' ')) AS word WHERE word <> ''
    ), docs AS MATERIALIZED (
        -- At the current catalog size, normalize each eligible row once per request.
        SELECT p.id, p.sku, catalog_search.fold_v1(p.sku) AS sku_key,
            catalog_search.fold_v1(p.name_vi) AS vi, catalog_search.fold_v1(p.name_en) AS en,
            catalog_search.fold_v1(b.name) AS brand,
            catalog_search.fold_v1(c.name_vi) AS category_vi,
            catalog_search.fold_v1(c.name_en) AS category_en,
            catalog_search.document_v1(p.name_vi,p.name_en,p.sku,p.unit_code) AS body
        FROM public.products p
        LEFT JOIN public.brands b ON b.id=p.brand_id
        LEFT JOIN public.categories c ON c.id=p.category_id
            AND c.id IN (SELECT id FROM catalog_search.visible_categories)
        WHERE p.status='PUBLISHED'
            AND (brand_filter IS NULL OR p.brand_id=brand_filter)
            AND (category_filter IS NULL OR p.category_id=ANY(category_filter))
    ), scored AS (
        SELECT d.id, CASE
            WHEN lower(d.sku)=lower(trim(raw_query)) THEN 110
            WHEN d.sku_key=q THEN 105
            WHEN replace(d.sku_key,' ','')=replace(q,' ','') THEN 100
            WHEN q IN (d.vi,d.en) THEN 90
            WHEN q IN (d.brand,d.category_vi,d.category_en) THEN 80
            WHEN starts_with(d.vi,q) OR starts_with(d.en,q) THEN 70
            WHEN scores.weakest >= 1 THEN 50
            WHEN scores.weakest >= 0.45 THEN 10
            ELSE 0 END AS tier, scores.mean_quality
        FROM docs d CROSS JOIN needle
        CROSS JOIN LATERAL (
            SELECT concat_ws(' ',d.body,d.brand,d.category_vi,d.category_en) AS document
        ) fields
        CROSS JOIN LATERAL (
            SELECT min(quality) AS weakest, avg(quality) AS mean_quality FROM (
                SELECT CASE
                    WHEN strpos(' ' || fields.document || ' ', ' ' || t.word || ' ') > 0 THEN 3.0
                    WHEN strpos(' ' || fields.document, ' ' || t.word) > 0 THEN 2.0
                    WHEN strpos(fields.document,t.word) > 0 THEN 1.0
                    WHEN length(t.word)>=4 AND t.word !~ '[0-9]'
                        THEN word_similarity(t.word,fields.document)
                    ELSE 0 END AS quality FROM terms t
            ) per_term
        ) scores WHERE q <> ''
    )
    SELECT id, CAST(tier + coalesce(mean_quality,0) AS double precision)
    FROM scored WHERE tier > 0;
$ranked$;
