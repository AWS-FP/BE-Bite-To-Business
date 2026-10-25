-- Chuẩn hóa số điện thoại của mọi role; không thay đổi luồng OTP email.
-- Khóa ghi trong lúc chuẩn hóa và tạo unique index để tránh dữ liệu chen vào giữa.
LOCK TABLE public.users IN SHARE ROW EXCLUSIVE MODE;

WITH cleaned_phones AS (
    SELECT id, REGEXP_REPLACE(phone, '[[:space:]()-]', '', 'g') AS phone
    FROM public.users
), normalized_phones AS (
    SELECT id,
        CASE
            WHEN phone ~ '^0[35789][0-9]{8}$' THEN '+84' || SUBSTRING(phone FROM 2)
            WHEN phone ~ '^[35789][0-9]{8}$' THEN '+84' || phone
            ELSE phone
        END AS phone
    FROM cleaned_phones
)
UPDATE public.users AS users
SET phone = normalized.phone, updated_at = NOW()
FROM normalized_phones AS normalized
WHERE users.id = normalized.id AND users.phone IS DISTINCT FROM normalized.phone;

-- Nếu dữ liệu cũ sai/trùng thì rollback migration để xử lý, không tự xóa tài khoản.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.users WHERE phone !~ '^[+]84[35789][0-9]{8}$') THEN
        RAISE EXCEPTION 'V5: users contains invalid Vietnamese mobile numbers. Correct these numbers before retrying migration.';
    END IF;

    IF EXISTS (SELECT 1 FROM public.users GROUP BY phone HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'V5: multiple users share the same normalized phone number. Resolve duplicates before retrying migration.';
    END IF;
END $$;

ALTER TABLE public.users
    ADD CONSTRAINT ck_users_phone_format CHECK (phone ~ '^[+]84[35789][0-9]{8}$');

CREATE UNIQUE INDEX ux_users_phone ON public.users (phone);
