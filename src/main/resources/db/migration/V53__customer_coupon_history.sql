-- Remember successfully applied private codes per customer, even after removal.
CREATE TABLE public.customer_coupon_history (
    customer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    coupon_id UUID NOT NULL REFERENCES public.coupons(id) ON DELETE CASCADE,
    code VARCHAR(8) NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, coupon_id, code)
);
ALTER TABLE public.customer_coupon_history ENABLE ROW LEVEL SECURITY;
