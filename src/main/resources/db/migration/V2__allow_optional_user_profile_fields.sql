-- Ho so dung chung cho Customer, Admin va Staff.
-- Cac truong rieng khong ap dung cho role duoc phep NULL.
-- Form dang ky Customer van bat buoc thong tin qua validation o backend.
ALTER TABLE public.user_profiles ALTER COLUMN customer_type DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN business_name DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN province DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN district DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN ward DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN delivery_address DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN invoice_same_as_delivery DROP NOT NULL;
ALTER TABLE public.user_profiles ALTER COLUMN invoice_same_as_delivery DROP DEFAULT;
