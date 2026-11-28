-- Evidence belongs to public product documentation, never to customer profiles or orders.
CREATE TABLE IF NOT EXISTS public.product_detail_evidence (
    product_id UUID PRIMARY KEY REFERENCES public.products(id) ON DELETE CASCADE,
    nutrition_verified BOOLEAN NOT NULL DEFAULT FALSE,
    sources JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes_vi TEXT,
    notes_en TEXT,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE public.product_detail_evidence ENABLE ROW LEVEL SECURITY;
