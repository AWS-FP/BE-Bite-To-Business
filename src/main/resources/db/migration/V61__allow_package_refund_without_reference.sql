ALTER TABLE public.package_returns
    DROP CONSTRAINT ck_package_return_refund_confirmation;

ALTER TABLE public.package_returns
    ADD CONSTRAINT ck_package_return_refund_confirmation CHECK (
        (refunded_at IS NULL AND refunded_by IS NULL AND transfer_reference IS NULL)
        OR (refunded_at IS NOT NULL AND refunded_by IS NOT NULL
            AND (transfer_reference IS NULL OR btrim(transfer_reference) <> '')
            AND status = 'ACCEPTED' AND refund_amount IS NOT NULL)
    );
