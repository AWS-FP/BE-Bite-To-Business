ALTER TABLE public.payments DROP CONSTRAINT ck_payments_method;

ALTER TABLE public.payments
    ADD CONSTRAINT ck_payments_method
    CHECK (payment_method = 'BANK_TRANSFER');
