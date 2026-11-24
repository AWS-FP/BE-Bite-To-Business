-- Giao hàng chỉ theo ngày: bỏ khung giờ giao ở gói báo giá và gói của đơn.
ALTER TABLE public.quote_packages DROP COLUMN IF EXISTS delivery_time_slot;
ALTER TABLE public.order_packages DROP COLUMN IF EXISTS delivery_time_slot;

-- Thanh toán xong là đơn hoàn tất; trạng thái giao hàng nằm ở từng package.
UPDATE public.orders o
   SET status = 'COMPLETED',
       completed_at = GREATEST(p.paid_at, o.confirmed_at),
       updated_at = NOW()
  FROM public.payments p
 WHERE p.order_id = o.id
   AND p.status = 'SUCCESS'
   AND p.paid_at IS NOT NULL
   AND o.status IN ('CONFIRMED', 'PROCESSING');

-- Khách yêu cầu trả package đã giao (trong 24 giờ); Admin chấp nhận thì hàng được nhập lại kho.
CREATE TABLE public.package_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_package_id UUID NOT NULL UNIQUE REFERENCES public.order_packages (id) ON DELETE RESTRICT,
    requested_by UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason TEXT NOT NULL,
    recipient_bank VARCHAR(100) NOT NULL,
    recipient_account VARCHAR(34) NOT NULL,
    recipient_name VARCHAR(200) NOT NULL,
    reviewed_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_package_returns_status CHECK (status IN ('REQUESTED', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_package_returns_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_package_returns_account CHECK (recipient_account ~ '^[0-9]{1,34}$'),
    CONSTRAINT ck_package_returns_review CHECK (
        (status = 'REQUESTED' AND reviewed_at IS NULL)
        OR (status = 'ACCEPTED' AND reviewed_at IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_at IS NOT NULL AND review_note IS NOT NULL AND BTRIM(review_note) <> '')
    )
);
CREATE INDEX ix_package_returns_status_created ON public.package_returns (status, created_at DESC);
ALTER TABLE public.package_returns ENABLE ROW LEVEL SECURITY;

-- Hàng trả về được nhập lại kho bằng movement RETURN (on_hand + quantity).
ALTER TABLE public.inventory_movements DROP CONSTRAINT ck_inventory_movements_type;
ALTER TABLE public.inventory_movements ADD CONSTRAINT ck_inventory_movements_type
    CHECK (movement_type IN ('RECEIVE', 'RESERVE', 'SHIP', 'RELEASE', 'ADJUST', 'RETURN'));
ALTER TABLE public.inventory_movements DROP CONSTRAINT ck_inventory_movements_quantity;
ALTER TABLE public.inventory_movements ADD CONSTRAINT ck_inventory_movements_quantity CHECK (
    (movement_type = 'ADJUST' AND quantity <> 0)
    OR (movement_type IN ('RECEIVE', 'RESERVE', 'SHIP', 'RELEASE', 'RETURN') AND quantity > 0)
);
