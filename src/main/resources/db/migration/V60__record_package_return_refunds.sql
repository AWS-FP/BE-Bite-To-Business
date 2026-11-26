ALTER TABLE public.package_returns
    ADD COLUMN refunded_at TIMESTAMPTZ,
    ADD COLUMN refunded_by UUID REFERENCES public.users(id) ON DELETE RESTRICT,
    ADD COLUMN transfer_reference VARCHAR(100),
    ADD CONSTRAINT ck_package_return_refund_confirmation CHECK (
        (refunded_at IS NULL AND refunded_by IS NULL AND transfer_reference IS NULL)
        OR (refunded_at IS NOT NULL AND refunded_by IS NOT NULL AND transfer_reference IS NOT NULL
            AND btrim(transfer_reference) <> '' AND status = 'ACCEPTED' AND refund_amount IS NOT NULL)
    );
