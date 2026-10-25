-- Ghi chu giao hang tuy chon; du lieu cu khong co ghi chu van de NULL.
ALTER TABLE public.user_profiles
    ADD COLUMN delivery_note VARCHAR(1000);
