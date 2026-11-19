ALTER TABLE public.inventory_movements ADD COLUMN order_package_id UUID REFERENCES public.order_packages(id) ON DELETE RESTRICT;
CREATE INDEX ix_inventory_movements_package ON public.inventory_movements(order_package_id) WHERE order_package_id IS NOT NULL;

CREATE TABLE public.cancelled_package_processing (
    order_package_id UUID PRIMARY KEY REFERENCES public.order_packages(id) ON DELETE RESTRICT,
    processed_by UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note TEXT
);
ALTER TABLE public.cancelled_package_processing ENABLE ROW LEVEL SECURITY;
