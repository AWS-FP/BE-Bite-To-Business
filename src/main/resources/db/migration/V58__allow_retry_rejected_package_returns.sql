-- Keep rejected requests as history while preventing concurrent or duplicate active returns.
ALTER TABLE public.package_returns
    DROP CONSTRAINT IF EXISTS package_returns_order_package_id_key;

CREATE UNIQUE INDEX ux_package_returns_active_package
    ON public.package_returns (order_package_id)
    WHERE status IN ('REQUESTED', 'ACCEPTED');
