ALTER TABLE public.payments DROP CONSTRAINT ck_payments_method;

ALTER TABLE public.payments
    ADD CONSTRAINT ck_payments_method
    CHECK (payment_method IN ('MOMO', 'MASTERCARD', 'BANK_TRANSFER'));

ALTER TABLE public.categories
    DROP COLUMN image_url,
    DROP COLUMN cloudinary_public_id,
    ADD COLUMN image_svg TEXT;
