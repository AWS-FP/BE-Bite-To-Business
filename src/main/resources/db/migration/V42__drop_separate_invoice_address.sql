-- Hóa đơn luôn dùng địa chỉ giao hàng của khách (user_profiles.delivery_address + ward/province).
-- Form đăng ký và hồ sơ không còn ô "địa chỉ hóa đơn riêng" / "giống địa chỉ giao".
-- Dữ liệu hiện có chỉ là bản chép của địa chỉ giao hàng, nên không mất thông tin.
-- Lưu ý: BE chạy code cũ (entity UserProfile còn map hai cột này) sẽ lỗi khi đọc hồ sơ sau migration này.
ALTER TABLE public.user_profiles
    DROP COLUMN IF EXISTS invoice_address,
    DROP COLUMN IF EXISTS invoice_same_as_delivery;
