-- Smart Search relevance tuning after V50. Do not edit the already-applied V50 migration.
-- Prefer exact accented spelling and name matches, and only use fuzzy matches as a fallback.
-- Use the same pg_trgm extension schema as V50 for function resolution.
SELECT set_config('search_path', 'pg_catalog,catalog_search,' || quote_ident(n.nspname), true)
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'pg_trgm';

CREATE OR REPLACE FUNCTION catalog_search.ranked(
    raw_query text, brand_filter uuid DEFAULT NULL, category_filter uuid[] DEFAULT NULL
)
RETURNS TABLE(product_id uuid, relevance double precision)
LANGUAGE sql STABLE SECURITY INVOKER
SET search_path FROM CURRENT
AS $ranked$
    WITH needle AS (
        SELECT catalog_search.fold_v1(raw_query) AS q,
               lower(normalize(trim(coalesce(raw_query, '')), NFC)) AS raw_q,
               octet_length(trim(coalesce(raw_query, ''))) >
                   char_length(trim(coalesce(raw_query, ''))) AS has_unicode
    ), docs AS MATERIALIZED (
        SELECT p.id, p.sku,
               catalog_search.fold_v1(p.sku) AS sku_key,
               catalog_search.fold_v1(p.name_vi || ' ' || p.name_en) AS name_key,
               lower(normalize(p.name_vi || ' ' || p.name_en, NFC)) AS original_names,
               catalog_search.fold_v1(coalesce(b.name, '')) AS brand_key,
               catalog_search.fold_v1(coalesce(c.name_vi, '') || ' ' || coalesce(c.name_en, '')) AS category_key
        FROM public.products p
        LEFT JOIN public.brands b ON b.id = p.brand_id
        LEFT JOIN public.categories c ON c.id = p.category_id
            AND c.id IN (SELECT id FROM catalog_search.visible_categories)
        WHERE p.status = 'PUBLISHED'
          AND (brand_filter IS NULL OR p.brand_id = brand_filter)
          AND (category_filter IS NULL OR p.category_id = ANY(category_filter))
    ), scores AS (
        SELECT d.id, n.q, n.raw_q, n.has_unicode,
               (n.raw_q <> '' AND position(' ' || n.raw_q IN ' ' || d.original_names) > 0) AS raw_name_match,
               coalesce(token_match.weakest, 0) AS weakest,
               CASE WHEN length(n.q) >= 5 THEN word_similarity(n.q, d.name_key) ELSE 0 END AS fuzzy_folded,
               CASE WHEN length(n.q) >= 5 THEN word_similarity(n.raw_q, d.original_names) ELSE 0 END AS fuzzy_original,
               CASE
                   WHEN lower(d.sku) = lower(trim(raw_query)) THEN 150
                   WHEN d.sku_key = n.q THEN 145
                   WHEN replace(d.sku_key, ' ', '') = replace(n.q, ' ', '') THEN 140
                   WHEN n.raw_q <> '' AND position(' ' || n.raw_q IN ' ' || d.original_names) > 0 THEN 130
                   WHEN n.q = d.name_key THEN 125
                   WHEN position(' ' || n.q IN ' ' || d.name_key) > 0 THEN 110
                   WHEN token_match.weakest >= 4 THEN 100
                   WHEN token_match.weakest >= 3 THEN 75
                   WHEN token_match.weakest >= 2 THEN 55
                   ELSE 0
               END AS lexical_tier
        FROM docs d CROSS JOIN needle n
        CROSS JOIN LATERAL (
            SELECT min(CASE
                WHEN position(' ' || token || ' ' IN ' ' || d.name_key || ' ') > 0 THEN 5
                WHEN position(' ' || token IN ' ' || d.name_key) > 0 THEN 4
                WHEN position(' ' || token || ' ' IN ' ' || d.brand_key || ' ') > 0 THEN 3
                WHEN position(' ' || token || ' ' IN ' ' || d.category_key || ' ') > 0 THEN 3
                WHEN position(' ' || token IN ' ' || d.brand_key) > 0 THEN 2
                WHEN position(' ' || token IN ' ' || d.category_key) > 0 THEN 2
                ELSE 0 END) AS weakest
            FROM unnest(string_to_array(n.q, ' ')) AS token
            WHERE token <> ''
        ) token_match
        WHERE n.q <> ''
    ), ranked AS (
        SELECT s.*,
            CASE WHEN s.lexical_tier > 0 THEN s.lexical_tier
                 WHEN length(s.q) >= 5 AND (
                     (s.has_unicode AND s.fuzzy_original >= 0.48 AND s.fuzzy_folded >= 0.45)
                     OR (NOT s.has_unicode AND s.fuzzy_folded >= 0.62)
                 ) THEN 15
                 ELSE 0 END AS tier
        FROM scores s
    ), gate AS (
        SELECT max(CASE WHEN lexical_tier >= 55 THEN 1 ELSE 0 END) AS has_lexical,
               max(CASE WHEN raw_name_match THEN 1 ELSE 0 END) AS has_raw_name
        FROM ranked
    )
    SELECT r.id, (
        r.tier + CASE WHEN r.raw_name_match THEN 8 ELSE 0 END +
        CASE WHEN r.tier = 15 THEN r.fuzzy_original * 5 ELSE r.weakest END
    )::double precision
    FROM ranked r CROSS JOIN gate g
    WHERE r.tier > 0
      -- Hide typo-only candidates if the query already has genuine matches.
      AND (r.tier > 15 OR g.has_lexical = 0)
      -- If the user typed accents and there is an exact accented name match,
      -- do not mix in unrelated words which become equal only after accent folding.
      AND (NOT r.has_unicode OR g.has_raw_name = 0 OR r.raw_name_match OR r.tier >= 140);
$ranked$;
