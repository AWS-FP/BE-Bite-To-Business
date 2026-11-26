-- Fix the amounts agreed with the customer when a return is requested.
ALTER TABLE public.package_returns
    ADD COLUMN merchandise_amount NUMERIC(16, 0),
    ADD COLUMN return_fee NUMERIC(16, 0),
    ADD COLUMN refund_amount NUMERIC(16, 0),
    ADD CONSTRAINT ck_package_return_amounts CHECK (
        (merchandise_amount IS NULL AND return_fee IS NULL AND refund_amount IS NULL)
        OR (merchandise_amount IS NOT NULL AND return_fee IS NOT NULL AND refund_amount IS NOT NULL
            AND merchandise_amount >= 0 AND return_fee >= 0 AND refund_amount >= 0
            AND refund_amount = merchandise_amount - return_fee)
    );
