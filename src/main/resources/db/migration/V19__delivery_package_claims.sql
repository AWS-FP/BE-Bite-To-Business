CREATE TABLE public.delivery_package_claims (
    package_id UUID PRIMARY KEY REFERENCES public.order_packages (id) ON DELETE RESTRICT,
    delivery_staff_id UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_delivery_package_claims_staff ON public.delivery_package_claims (delivery_staff_id, claimed_at DESC);
ALTER TABLE public.delivery_package_claims ENABLE ROW LEVEL SECURITY;
