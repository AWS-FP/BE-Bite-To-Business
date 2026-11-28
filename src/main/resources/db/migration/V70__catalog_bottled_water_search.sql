-- Preserve V69; add a constrained bilingual bottled-water intent to the shared catalog search.
SELECT set_config('search_path', 'pg_catalog,catalog_search,' || quote_ident(n.nspname), true)
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'pg_trgm';

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
    ), regular AS (
        SELECT id,score::double precision AS score FROM ranked
        WHERE (score>=95 OR (best<95 AND (best<80 OR score>=80)))
          AND (NOT prefer_spelling OR exact_spelling OR score>=150)
    ), bottled_water AS (
        -- Interpret the entire normalized phrase as a product type plus packaging attribute.
        -- Never expand the individual word "water" or "bottle" to unrelated beverages.
        SELECT p.id, 90::double precision AS score
        FROM base n
        JOIN public.products p ON p.status='PUBLISHED'
        LEFT JOIN public.categories c ON c.id=p.category_id
        CROSS JOIN LATERAL (
            SELECT catalog_search.label_v2(concat_ws(' ',p.name_vi,p.name_en,c.name_vi,c.name_en)) AS labels,
                   catalog_search.label_v2(concat_ws(' ',p.name_vi,p.name_en,p.package_spec_vi,p.package_spec_en)) AS packaging
        ) d
        WHERE n.q IN ('nuoc dong chai','nuoc uong dong chai','nuoc suoi dong chai',
                      'nuoc khoang dong chai','nuoc tinh khiet dong chai',
                      'bottled water','bottle water','bottles of water','bottle of water','water bottles','water bottle')
          AND (brand_filter IS NULL OR p.brand_id=brand_filter)
          AND (category_filter IS NULL OR p.category_id=ANY(category_filter))
          AND (p.unit_code='BOTTLE' OR d.packaging ~ '\m(chai|bottle|bottles|bottled)\M')
          AND (catalog_search.label_v2(c.name_en) IN ('water','mineral water','drinking water')
               OR catalog_search.label_v2(c.name_vi) IN ('nuoc suoi','nuoc khoang','nuoc uong','nuoc tinh khiet')
               OR d.labels ~ '\m(nuoc (suoi|khoang|tinh khiet|uong|loc|dong chai)|((mineral|drinking|spring|purified|sparkling|bottled|still|carbonated|plain) water)|water (mineral|drinking|spring|purified|sparkling|bottled|still|carbonated))\M')
          AND d.labels !~ '\m(coconut water|nuoc dua|tonic water|nuoc tang luc|energy drink|flavoured water|flavored water)\M'
    ), combined AS (
        SELECT id,score FROM regular
        WHERE score>=95 OR NOT EXISTS (SELECT 1 FROM bottled_water)
        UNION ALL
        SELECT id,score FROM bottled_water
    )
    SELECT id,max(score)::double precision FROM combined GROUP BY id
$$;
