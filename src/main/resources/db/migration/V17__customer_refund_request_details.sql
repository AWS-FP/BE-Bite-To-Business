ALTER TABLE public.package_refunds
    ADD COLUMN request_group_id UUID,
    ADD COLUMN recipient_bank VARCHAR(100),
    ADD COLUMN recipient_account VARCHAR(34),
    ADD COLUMN recipient_name VARCHAR(200);

CREATE INDEX ix_package_refunds_request_group ON public.package_refunds (request_group_id)
    WHERE request_group_id IS NOT NULL;

ALTER TABLE public.orders ADD COLUMN reservation_managed BOOLEAN NOT NULL DEFAULT FALSE;
