-- Tighten constraints without changing the already-applied V12 migration.
ALTER TABLE public.orders
    DROP CONSTRAINT ck_orders_shipping_coupon,
    ADD CONSTRAINT ck_orders_shipping_coupon CHECK (
        shipping_coupon_discount_amount >= 0
        AND shipping_coupon_discount_amount <= shipping_fee
        AND (
            (shipping_coupon_id IS NULL AND shipping_coupon_code IS NULL
                AND shipping_coupon_discount_type IS NULL AND shipping_coupon_discount_value IS NULL
                AND shipping_coupon_redeemed_at IS NULL AND shipping_coupon_discount_amount = 0)
            OR (shipping_coupon_id IS NOT NULL AND shipping_coupon_code IS NOT NULL
                AND shipping_coupon_code ~ '^[A-Z0-9]{8}$'
                AND shipping_coupon_discount_type IS NOT NULL AND shipping_coupon_discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')
                AND shipping_coupon_discount_value IS NOT NULL AND shipping_coupon_discount_value > 0
                AND (shipping_coupon_discount_type <> 'PERCENTAGE' OR shipping_coupon_discount_value <= 100))
        )
    ),
    DROP CONSTRAINT ck_orders_reservation_times,
    ADD CONSTRAINT ck_orders_reservation_times CHECK (
        (reservation_started_at IS NULL AND reservation_expires_at IS NULL AND reservation_released_at IS NULL)
        OR (reservation_started_at IS NOT NULL AND reservation_expires_at IS NOT NULL
            AND reservation_expires_at = reservation_started_at + INTERVAL '1 hour'
            AND (reservation_released_at IS NULL OR reservation_released_at >= reservation_started_at))
    );

ALTER TABLE public.quote_packages
    DROP CONSTRAINT ck_quote_packages_coordinates,
    ADD CONSTRAINT ck_quote_packages_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    );

ALTER TABLE public.order_packages
    DROP CONSTRAINT ck_order_packages_coordinates,
    ADD CONSTRAINT ck_order_packages_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    );

ALTER TABLE public.payments ADD CONSTRAINT ux_payments_id_order UNIQUE (id, order_id);

ALTER TABLE public.package_refunds
    DROP CONSTRAINT package_refunds_payment_id_fkey,
    ADD CONSTRAINT fk_package_refunds_payment FOREIGN KEY (payment_id, order_id)
        REFERENCES public.payments (id, order_id) ON DELETE RESTRICT;
