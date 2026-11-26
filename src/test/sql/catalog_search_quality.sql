-- Run with psql on the same staging snapshot before V56 and again after V57.
-- Read-only SKU/name checks use real rows. The labelled cases below use isolated fixtures.
\set ON_ERROR_STOP on
\timing on
BEGIN;
SET LOCAL statement_timeout='60s';
CREATE TEMP TABLE search_sample ON COMMIT DROP AS
    SELECT id,sku,name_vi,name_en FROM public.products WHERE status='PUBLISHED' ORDER BY id LIMIT 16;
SELECT count(*) AS sampled_skus,
       count(*) FILTER (WHERE h.product_id=s.id) AS sku_top1,
       count(*) FILTER (WHERE bad.product_id IS NOT NULL) AS wrong_pack_hits
FROM search_sample s
LEFT JOIN LATERAL (SELECT product_id FROM catalog_search.ranked(s.sku)
                   ORDER BY relevance DESC,product_id LIMIT 1) h ON true
LEFT JOIN LATERAL (SELECT product_id FROM catalog_search.ranked(s.name_vi||' 987654321ml')
                   LIMIT 1) bad ON true;

CREATE TEMP TABLE search_labels (key text PRIMARY KEY, id uuid DEFAULT gen_random_uuid(),
    vi text NOT NULL,en text NOT NULL,spec text NOT NULL);
INSERT INTO search_labels(key,vi,en,spec) VALUES
    ('cola','Coca-Cola nguyên bản','Coca Cola Original','24×320 ml'),
    ('zero','Coca-Cola Zero Sugar','Coca Cola Zero Sugar','330ml'),
    ('crab','Cua biển','Sea crab','1kg'),
    ('shrimp','Tôm sú','Black tiger shrimp','500g'),
    ('beef','Bò tảng','Whole beef cut','2kg');
CREATE TEMP TABLE search_scope AS SELECT gen_random_uuid() AS brand,gen_random_uuid() AS category;
INSERT INTO public.brands(id,name) SELECT brand,'Quality '||brand FROM search_scope;
INSERT INTO public.categories(id,code,name_vi,name_en)
    SELECT category,category::text,'Thực phẩm','Food' FROM search_scope;
INSERT INTO public.products(id,sku,category_id,brand_id,name_vi,name_en,package_spec_vi,package_spec_en,
    unit_code,min_order_quantity,quantity_step,status)
SELECT l.id,'QA-'||l.id,category,brand,vi,en,spec,spec,'BOX',1,1,'PUBLISHED' FROM search_labels l CROSS JOIN search_scope;

CREATE TEMP TABLE queries(q text PRIMARY KEY, gold text[] NOT NULL);
INSERT INTO queries VALUES
    ('Coca-Cola','{cola,zero}'),('Coca Cola','{cola,zero}'),('Cocacola','{cola,zero}'),
    ('Coca Cola Zero','{zero}'),('Coca Col','{cola,zero}'),('Coca Cola 320ml','{cola}'),('Coca Cola 320 ml','{cola}'),('24 x 320ml','{cola}'),('12 x 320ml','{}'),
    ('Cocaa Cola','{cola,zero}'),('Bò tảng','{beef}'),('Bo tang','{beef}'),
    ('con cua','{crab}'),('Tôm sú','{shrimp}'),('Tom su','{shrimp}'),
    ('crab','{crab}'),('Zero 320ml','{}'),('987654321ml','{}'),('q','{}'),
    ('thực phẩm','{cola,zero,crab,shrimp,beef}'),('nonexistentqwzz','{}');
INSERT INTO queries SELECT 'QA-'||id,ARRAY[key] FROM search_labels WHERE key='crab';
CREATE TEMP TABLE results AS
SELECT q.q,l.key,r.relevance,row_number() OVER(PARTITION BY q.q ORDER BY r.relevance DESC,r.product_id) AS pos
FROM queries q CROSS JOIN search_scope s
CROSS JOIN LATERAL catalog_search.ranked(q.q,s.brand,ARRAY[s.category]) r
JOIN search_labels l ON l.id=r.product_id;

-- Fixed-denominator P@3, R@3 (undefined for empty gold), MRR and false positives.
SELECT q.q, q.gold, array_remove(array_agg(r.key ORDER BY pos) FILTER (WHERE pos<=3),NULL) AS top3,
       round(count(*) FILTER (WHERE pos<=3 AND r.key=ANY(gold))/3.0,3) AS precision_at_3,
       round(count(*) FILTER (WHERE pos<=3 AND r.key=ANY(gold))::numeric/nullif(cardinality(gold),0),3) AS recall_at_3,
       coalesce(round(1.0/min(pos) FILTER (WHERE r.key=ANY(gold)),3),0) AS reciprocal_rank,
       count(r.key) FILTER (WHERE NOT r.key=ANY(gold)) AS false_positives
FROM queries q LEFT JOIN results r ON r.q=q.q GROUP BY q.q ORDER BY q.q;
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM catalog_search.ranked('Coca Cola 320ml') ORDER BY relevance DESC,product_id LIMIT 12;

-- Baseline should report regressions without aborting; enable assertions after migration.
\if :{?assert_quality}
DO $$
BEGIN
    IF EXISTS (SELECT FROM queries q WHERE
        ARRAY(SELECT DISTINCT r.key FROM results r WHERE r.q=q.q ORDER BY 1)
        IS DISTINCT FROM ARRAY(SELECT k FROM unnest(q.gold) k ORDER BY 1)) THEN
        RAISE EXCEPTION 'Search result differs from labelled fixture. See per-query report above.';
    END IF;
END $$;
\endif
ROLLBACK;
