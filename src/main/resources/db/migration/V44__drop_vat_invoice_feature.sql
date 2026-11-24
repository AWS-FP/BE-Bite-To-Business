-- Bỏ tính năng hóa đơn VAT (API /invoice, /vat-preview đã xóa) và email hóa đơn của khách.
-- Giữ invoice_purchaser/invoice_tax_code trong user_profiles (form đăng ký vẫn hỏi).
-- Lưu ý: BE chạy code cũ còn map các cột này sẽ lỗi sau migration.
DROP TABLE IF EXISTS public.invoices;

ALTER TABLE public.user_profiles
    DROP COLUMN IF EXISTS invoice_email;

ALTER TABLE public.orders
    DROP CONSTRAINT IF EXISTS ck_orders_invoice_tax_code,
    DROP CONSTRAINT IF EXISTS ck_orders_invoice_email,
    DROP COLUMN IF EXISTS invoice_purchaser,
    DROP COLUMN IF EXISTS invoice_tax_code,
    DROP COLUMN IF EXISTS invoice_email,
    DROP COLUMN IF EXISTS invoice_address;
