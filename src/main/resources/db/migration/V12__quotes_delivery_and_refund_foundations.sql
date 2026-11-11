-- Product coupons and shipping coupons are independent; existing coupons remain product coupons.
ALTER TABLE public.coupons
    ADD COLUMN applies_to VARCHAR(20) NOT NULL DEFAULT 'PRODUCT',
    ADD CONSTRAINT ck_coupons_applies_to CHECK (applies_to IN ('PRODUCT', 'SHIPPING'));

-- A promotion can discount the volume-tier price by a percentage. Legacy fixed sale prices remain valid.
ALTER TABLE public.product_prices
    ADD COLUMN sale_discount_percent NUMERIC(5, 2),
    ADD CONSTRAINT ck_product_prices_sale_discount_percent CHECK (
        sale_discount_percent IS NULL OR
        (sale_discount_percent > 0 AND sale_discount_percent <= 100 AND sale_price IS NULL)
    );

-- coupon_id remains the product coupon. Shipping discounts cannot exceed the shipping charge.
ALTER TABLE public.quotes
    ADD COLUMN shipping_coupon_id UUID REFERENCES public.coupons (id) ON DELETE RESTRICT,
    ADD COLUMN shipping_coupon_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_quotes_shipping_coupon CHECK (
        shipping_coupon_discount_amount >= 0
        AND shipping_coupon_discount_amount <= shipping_fee
        AND (shipping_coupon_id IS NOT NULL OR shipping_coupon_discount_amount = 0)
    );

ALTER TABLE public.quotes DROP COLUMN total_amount;
ALTER TABLE public.quotes ADD COLUMN total_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
    items_subtotal - volume_discount_amount - sale_discount_amount - coupon_discount_amount
    + vat_amount + shipping_fee - shipping_coupon_discount_amount
) STORED;

-- Snapshot the shipping coupon on the order so later coupon edits do not change the order.
ALTER TABLE public.orders
    ADD COLUMN shipping_coupon_id UUID REFERENCES public.coupons (id) ON DELETE RESTRICT,
    ADD COLUMN shipping_coupon_code VARCHAR(8),
    ADD COLUMN shipping_coupon_discount_type VARCHAR(20),
    ADD COLUMN shipping_coupon_discount_value NUMERIC(14, 2),
    ADD COLUMN shipping_coupon_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    ADD COLUMN shipping_coupon_redeemed_at TIMESTAMPTZ,
    ADD COLUMN reservation_started_at TIMESTAMPTZ,
    ADD COLUMN reservation_expires_at TIMESTAMPTZ,
    ADD COLUMN reservation_released_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_orders_shipping_coupon CHECK (
        shipping_coupon_discount_amount >= 0 AND shipping_coupon_discount_amount <= shipping_fee
        AND (
            (shipping_coupon_id IS NULL AND shipping_coupon_code IS NULL
                AND shipping_coupon_discount_type IS NULL AND shipping_coupon_discount_value IS NULL
                AND shipping_coupon_redeemed_at IS NULL AND shipping_coupon_discount_amount = 0)
            OR (shipping_coupon_id IS NOT NULL AND shipping_coupon_code ~ '^[A-Z0-9]{8}$'
                AND shipping_coupon_discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')
                AND shipping_coupon_discount_value > 0
                AND (shipping_coupon_discount_type <> 'PERCENTAGE' OR shipping_coupon_discount_value <= 100))
        )
    ),
    ADD CONSTRAINT ck_orders_reservation_times CHECK (
        (reservation_started_at IS NULL AND reservation_expires_at IS NULL AND reservation_released_at IS NULL)
        OR (reservation_started_at IS NOT NULL AND reservation_expires_at = reservation_started_at + INTERVAL '1 hour'
            AND (reservation_released_at IS NULL OR reservation_released_at >= reservation_started_at))
    );

ALTER TABLE public.orders DROP COLUMN total_amount;
ALTER TABLE public.orders ADD COLUMN total_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
    items_subtotal - volume_discount_amount - sale_discount_amount - coupon_discount_amount
    + vat_amount + shipping_fee - shipping_coupon_discount_amount
) STORED;

CREATE INDEX ix_orders_reservation_expiry ON public.orders (reservation_expires_at)
    WHERE reservation_expires_at IS NOT NULL AND reservation_released_at IS NULL;

-- Nullable for legacy records and drafts; BE validates completeness before submission.
ALTER TABLE public.quote_packages
    ADD COLUMN recipient_name VARCHAR(200),
    ADD COLUMN recipient_phone VARCHAR(30),
    ADD COLUMN delivery_time_slot VARCHAR(100),
    ADD COLUMN delivery_note TEXT,
    ADD COLUMN city VARCHAR(20),
    ADD COLUMN latitude NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    ADD COLUMN distance_metres INTEGER,
    ADD COLUMN shipping_fee NUMERIC(16, 0),
    ADD COLUMN address_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    ADD CONSTRAINT ck_quote_packages_city CHECK (city IS NULL OR city IN ('HCM', 'HANOI')),
    ADD CONSTRAINT ck_quote_packages_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    ADD CONSTRAINT ck_quote_packages_estimate CHECK (
        (distance_metres IS NULL OR distance_metres >= 0) AND (shipping_fee IS NULL OR shipping_fee >= 0)
    ),
    ADD CONSTRAINT ck_quote_packages_address_status CHECK (
        address_status IN ('UNVERIFIED', 'VALID', 'NEEDS_UPDATE')
    );

-- The amounts on a confirmed package are snapshots used to calculate partial refunds.
ALTER TABLE public.order_packages
    ADD COLUMN recipient_name VARCHAR(200),
    ADD COLUMN recipient_phone VARCHAR(30),
    ADD COLUMN delivery_time_slot VARCHAR(100),
    ADD COLUMN delivery_note TEXT,
    ADD COLUMN city VARCHAR(20),
    ADD COLUMN latitude NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    ADD COLUMN distance_metres INTEGER,
    ADD COLUMN address_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN merchandise_amount NUMERIC(16, 0),
    ADD COLUMN product_coupon_discount_amount NUMERIC(16, 0),
    ADD COLUMN vat_amount NUMERIC(16, 0),
    ADD COLUMN shipping_fee NUMERIC(16, 0),
    ADD COLUMN shipping_coupon_discount_amount NUMERIC(16, 0),
    ADD CONSTRAINT ck_order_packages_city CHECK (city IS NULL OR city IN ('HCM', 'HANOI')),
    ADD CONSTRAINT ck_order_packages_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    ADD CONSTRAINT ck_order_packages_address_status CHECK (
        address_status IN ('UNVERIFIED', 'VALID', 'NEEDS_UPDATE')
    ),
    ADD CONSTRAINT ck_order_packages_amounts CHECK (
        (distance_metres IS NULL OR distance_metres >= 0)
        AND (merchandise_amount IS NULL OR merchandise_amount >= 0)
        AND (product_coupon_discount_amount IS NULL OR product_coupon_discount_amount >= 0)
        AND (vat_amount IS NULL OR vat_amount >= 0)
        AND (shipping_fee IS NULL OR shipping_fee >= 0)
        AND (shipping_coupon_discount_amount IS NULL OR shipping_coupon_discount_amount >= 0)
        AND (product_coupon_discount_amount IS NULL OR merchandise_amount IS NULL OR product_coupon_discount_amount <= merchandise_amount)
        AND (shipping_coupon_discount_amount IS NULL OR shipping_fee IS NULL OR shipping_coupon_discount_amount <= shipping_fee)
    );

-- Availability is a staff setting; busy/assigned is derived from active delivery_trips.
CREATE TABLE public.delivery_staff_availability (
    user_id UUID PRIMARY KEY REFERENCES public.users (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'OFF_DUTY',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_delivery_staff_availability_status CHECK (status IN ('AVAILABLE', 'OFF_DUTY'))
);
ALTER TABLE public.delivery_staff_availability ENABLE ROW LEVEL SECURITY;

-- A request is not a transfer. Staff must record a verified manual transfer separately.
CREATE TABLE public.package_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    order_package_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES public.payments (id) ON DELETE RESTRICT,
    requested_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    reviewed_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    processed_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason TEXT NOT NULL,
    refund_amount NUMERIC(16, 0),
    transfer_reference VARCHAR(200),
    reviewed_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_package_refunds_package FOREIGN KEY (order_package_id, order_id)
        REFERENCES public.order_packages (id, order_id) ON DELETE RESTRICT,
    CONSTRAINT ck_package_refunds_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PROCESSING', 'REFUNDED')),
    CONSTRAINT ck_package_refunds_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_package_refunds_amount CHECK (refund_amount IS NULL OR refund_amount > 0),
    CONSTRAINT ck_package_refunds_state CHECK (
        (status IN ('REQUESTED', 'REJECTED') OR (refund_amount IS NOT NULL AND reviewed_at IS NOT NULL))
        AND ((status = 'REFUNDED' AND refunded_at IS NOT NULL AND transfer_reference IS NOT NULL
            AND BTRIM(transfer_reference) <> '') OR (status <> 'REFUNDED' AND refunded_at IS NULL))
    )
);
CREATE INDEX ix_package_refunds_order ON public.package_refunds (order_id, created_at DESC);
CREATE UNIQUE INDEX ux_package_refunds_active ON public.package_refunds (order_package_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'PROCESSING');
ALTER TABLE public.package_refunds ENABLE ROW LEVEL SECURITY;

-- Snapshots allow customers and staff to compare revisions; populated by future quote APIs.
CREATE TABLE public.quote_revisions (
    quote_id UUID NOT NULL REFERENCES public.quotes (id) ON DELETE RESTRICT,
    revision INTEGER NOT NULL CHECK (revision > 0),
    snapshot JSONB NOT NULL,
    change_note TEXT,
    created_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (quote_id, revision)
);
ALTER TABLE public.quote_revisions ENABLE ROW LEVEL SECURITY;
