-- Run with psql -X -v ON_ERROR_STOP=1 -f against a disposable migrated database.
BEGIN;
DO $test$
DECLARE
    cat uuid := gen_random_uuid();
    brand uuid := gen_random_uuid();
    exact_id uuid := gen_random_uuid();
    prefix_id uuid := gen_random_uuid();
    draft_id uuid := gen_random_uuid();
    sku text := 'Z-' || left(replace(exact_id::text,'-',''),12);
    term text;
    found uuid;
BEGIN
    IF catalog_search.fold_v1('  ĐẬU--ĐỎ,  42  ') <> 'dau do 42' OR
       catalog_search.fold_v1(normalize('Dầu', NFD)) <> 'dau' THEN
        RAISE EXCEPTION 'Vietnamese normalization failed';
    END IF;
    INSERT INTO public.categories(id,code,name_vi,name_en)
        VALUES(cat,cat::text,'Gia vị','Spices');
    INSERT INTO public.brands(id,name) VALUES(brand,'Fixture ' || brand::text);
    INSERT INTO public.products(id,sku,category_id,brand_id,name_vi,name_en,unit_code,
                                min_order_quantity,quantity_step,status) VALUES
        (exact_id,sku,cat,brand,'Ngò rí','Coriander','BOX',1,1,'PUBLISHED'),
        (prefix_id,sku || '-S',cat,brand,'Ngò rí non','Coriander leaves','PACK',1,1,'PUBLISHED'),
        (draft_id,sku || '-D',cat,brand,'Ngò rí','Coriander','BOX',1,1,'DRAFT');

    FOREACH term IN ARRAY ARRAY['NGÒ--RÍ','coriander',replace(sku,'-',''),
        'coriandr','fixture spices hộp','gia vi box'] LOOP
        SELECT product_id INTO found FROM catalog_search.ranked(term,brand,ARRAY[cat])
            ORDER BY relevance DESC,product_id LIMIT 1;
        IF term = 'coriandr' THEN
            IF found IS NULL THEN RAISE EXCEPTION 'No typo match'; END IF;
        ELSIF found IS DISTINCT FROM exact_id THEN
            RAISE EXCEPTION 'Wrong first result for %', term;
        END IF;
    END LOOP;
    IF EXISTS (SELECT FROM catalog_search.ranked('coriander') WHERE product_id=draft_id)
        OR EXISTS (SELECT FROM catalog_search.ranked('coriander 948220948220948220948220',brand,ARRAY[cat]))
        OR EXISTS (SELECT FROM catalog_search.ranked(' %_-- '))
        OR EXISTS (SELECT FROM catalog_search.ranked('coriander',brand,ARRAY[]::uuid[]))
    THEN RAISE EXCEPTION 'Visibility, punctuation or category filter regression'; END IF;
END;
$test$;
ROLLBACK;
