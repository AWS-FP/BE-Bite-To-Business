-- Extend V56 without changing the ranked search signature or existing migrations.
SELECT set_config('search_path', 'pg_catalog,catalog_search,' || quote_ident(n.nspname), true)
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'pg_trgm';

-- Stem English product names, package specifications and unit labels only.
-- The original simple-token path remains authoritative for exact names and quantities.
CREATE FUNCTION catalog_search.english_terms(name_en text, spec_en text, unit_code text)
RETURNS tsvector LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT to_tsvector('pg_catalog.english'::regconfig, catalog_search.label_v2(
        coalesce(name_en,'') || ' ' || coalesce(spec_en,'') || ' ' || coalesce(unit_code,'')))
$$;

CREATE INDEX products_catalog_english_gin ON public.products USING gin
    (catalog_search.english_terms(name_en,package_spec_en,unit_code)) WHERE status='PUBLISHED';

CREATE OR REPLACE FUNCTION catalog_search.ranked(
    raw_query text, brand_filter uuid DEFAULT NULL, category_filter uuid[] DEFAULT NULL
) RETURNS TABLE(product_id uuid, relevance double precision)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path FROM CURRENT AS $$
    WITH input AS (
        SELECT catalog_search.label_v2(regexp_replace(lower(trim(raw_query)),
                   '^con[[:space:]]+', '')) AS q,
               replace(catalog_search.fold_v1(raw_query),' ','') AS sku,
               lower(normalize(trim(raw_query), NFC)) AS original
    ), base AS (
        SELECT *, string_to_array(q,' ') AS words FROM input
        WHERE q <> '' AND length(q) <= 200
    ), needle AS (
        SELECT * FROM base
        UNION ALL
        -- A catalog word can also be entered as two words. Preserve all other attributes.
        SELECT b.q, b.sku, b.original,
               b.words[1:i-1] || ARRAY[b.words[i] || b.words[i+1]] || b.words[i+2:cardinality(b.words)]
        FROM base b CROSS JOIN LATERAL generate_series(1,cardinality(b.words)-1) AS i
        WHERE b.words[i] ~ '^[a-z]{2,}$' AND b.words[i+1] ~ '^[a-z]{2,}$'
    ), brand_matches AS (
        SELECT DISTINCT b.id, catalog_search.label_v2(b.name) || ' ' || replace(catalog_search.label_v2(b.name),' ','') AS label
        FROM public.brands b CROSS JOIN needle n
        WHERE string_to_array(catalog_search.label_v2(b.name),' ') && n.words
           OR replace(catalog_search.label_v2(b.name),' ','')=replace(n.q,' ','')
    ), category_matches AS (
        SELECT DISTINCT c.id, label FROM public.categories c
        JOIN catalog_search.visible_categories v ON v.id=c.id
        CROSS JOIN LATERAL (VALUES (catalog_search.label_v2(c.name_vi)),
                                   (catalog_search.label_v2(c.name_en))) AS labels(label)
        CROSS JOIN needle n WHERE strpos(' ' || n.q || ' ', ' ' || label || ' ') > 0
    ), hits AS (
        SELECT p.id FROM needle n JOIN public.products p
          ON p.status='PUBLISHED' AND (
            replace(catalog_search.fold_v1(p.sku),' ','')=n.sku OR
            to_tsvector('simple'::regconfig,p.catalog_terms) @@ plainto_tsquery('simple',array_to_string(n.words,' ')))
        UNION
        -- Match all meaningful English query lexemes, including numbers and package sizes.
        SELECT p.id FROM base n JOIN public.products p ON p.status='PUBLISHED'
          AND n.original ~ '^[a-z0-9 ,./×*_-]+$'
          AND numnode(plainto_tsquery('pg_catalog.english'::regconfig,n.q)) > 0
          AND catalog_search.english_terms(p.name_en,p.package_spec_en,p.unit_code)
              @@ plainto_tsquery('pg_catalog.english'::regconfig,n.q)
        UNION
        -- Preserve autocomplete for a partially typed final word; never prefix-match quantities.
        SELECT p.id FROM needle n JOIN public.products p ON p.status='PUBLISHED'
          AND n.words[cardinality(n.words)] ~ '^[a-z]{3,}$'
          AND n.q ~ '^[a-z0-9 ]+$'
          AND to_tsvector('simple'::regconfig,p.catalog_terms) @@
              to_tsquery('simple',array_to_string(n.words,' & ') || ':*')
        UNION
        SELECT p.id FROM public.products p JOIN brand_matches b ON b.id=p.brand_id WHERE p.status='PUBLISHED'
        UNION
        SELECT p.id FROM public.products p JOIN category_matches c ON c.id=p.category_id WHERE p.status='PUBLISHED'
        UNION
        SELECT p.id FROM needle n CROSS JOIN LATERAL unnest(n.words) AS t(word)
        JOIN public.products p ON p.status='PUBLISHED'
            AND t.word ~ '^[a-z]{5,40}$'
            AND t.word <<% catalog_search.label_v2(p.name_vi || ' ' || p.name_en)
    ), candidates AS (
        SELECT p.*, n.q,n.words,n.sku AS query_sku,n.original,
            catalog_search.label_v2(p.name_vi) AS vi,catalog_search.label_v2(p.name_en) AS en,
            string_to_array(p.catalog_terms || ' ' || coalesce(b.label,'') || ' ' ||
                coalesce((SELECT string_agg(c.label,' ') FROM category_matches c WHERE c.id=p.category_id),''),' ') AS tokens
        FROM hits h JOIN public.products p ON p.id=h.id CROSS JOIN needle n
        LEFT JOIN brand_matches b ON b.id=p.brand_id
        WHERE (brand_filter IS NULL OR p.brand_id=brand_filter)
          AND (category_filter IS NULL OR p.category_id=ANY(category_filter))
    ), scored AS (
        SELECT d.id, octet_length(d.original)>char_length(d.original) AS accented, CASE
            WHEN lower(d.sku)=d.original THEN 155
            WHEN replace(catalog_search.fold_v1(d.sku),' ','')=d.query_sku THEN 150
            WHEN d.q IN (d.vi,d.en) THEN 140
            WHEN strpos(' '||d.vi||' ',' '||d.q||' ')>0 OR strpos(' '||d.en||' ',' '||d.q||' ')>0 THEN 125
            WHEN missing.words IS NULL THEN 100
            WHEN d.original ~ '^[a-z0-9 ,./×*_-]+$'
                AND numnode(plainto_tsquery('pg_catalog.english'::regconfig,d.q)) > 0
                AND catalog_search.english_terms(d.name_en,d.package_spec_en,d.unit_code)
                    @@ plainto_tsquery('pg_catalog.english'::regconfig,d.q) THEN 95
            WHEN cardinality(missing.words)=1 AND missing.words[1]=d.words[cardinality(d.words)]
                AND missing.words[1] ~ '^[a-z]{3,}$' AND EXISTS (
                    SELECT FROM unnest(d.tokens) AS token WHERE starts_with(token,missing.words[1])
                ) THEN 80
            WHEN d.words=string_to_array(d.q,' ') AND cardinality(missing.words)=1 AND EXISTS (
                SELECT FROM unnest(string_to_array(d.vi||' '||d.en,' ')) AS w
                WHERE catalog_search.one_edit(missing.words[1],w)
            ) THEN 20 ELSE 0 END AS tier,
            CASE WHEN strpos(' '||lower(normalize(d.name_vi,NFC))||' ',' '||d.original||' ')>0
                   OR strpos(' '||lower(normalize(d.name_en,NFC))||' ',' '||d.original||' ')>0 THEN 3 ELSE 0 END AS accent_bonus
        FROM candidates d CROSS JOIN LATERAL (
            SELECT array_agg(DISTINCT word) AS words FROM unnest(d.words) AS word
            WHERE NOT word=ANY(d.tokens)
        ) missing
    ), ranked AS (
        SELECT id, max(tier+accent_bonus) AS score, max(max(tier)) OVER () AS best,
               bool_or(accented AND accent_bonus>0) AS exact_spelling,
               bool_or(bool_or(accented AND accent_bonus>0)) OVER () AS prefer_spelling
        FROM scored WHERE tier>0 GROUP BY id
    )
    SELECT id,score::double precision FROM ranked WHERE (score>=95 OR (best<95 AND (best<80 OR score>=80)))
        AND (NOT prefer_spelling OR exact_spelling OR score>=150)
$$;
