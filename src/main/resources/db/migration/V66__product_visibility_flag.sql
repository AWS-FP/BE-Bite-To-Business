-- Visibility is stored by PostgreSQL and stays consistent when products are published or archived.
ALTER TABLE public.products
    ADD COLUMN is_active BOOLEAN GENERATED ALWAYS AS (status = 'PUBLISHED') STORED;
COMMENT ON COLUMN public.products.is_active IS 'Public visibility: published products are active; archived and draft products are hidden.';
