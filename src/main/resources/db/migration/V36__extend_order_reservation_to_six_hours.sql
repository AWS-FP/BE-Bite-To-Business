-- Stock is now held for 6 hours after an order is confirmed (was exactly 1 hour in V16).
-- Existing orders keep their 1-hour window, so the check allows any window up to 6 hours.
ALTER TABLE public.orders
    DROP CONSTRAINT ck_orders_reservation_times,
    ADD CONSTRAINT ck_orders_reservation_times CHECK (
        (reservation_started_at IS NULL AND reservation_expires_at IS NULL AND reservation_released_at IS NULL)
        OR (reservation_started_at IS NOT NULL AND reservation_expires_at IS NOT NULL
            AND reservation_expires_at > reservation_started_at
            AND reservation_expires_at <= reservation_started_at + INTERVAL '6 hours'
            AND (reservation_released_at IS NULL OR reservation_released_at >= reservation_started_at))
    );
