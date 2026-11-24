-- Loại bỏ dữ liệu dư thừa ngoài ý muốn (docs/database/normalization-audit.md, mục N1, N8).
-- An toàn với DB dùng chung: không xóa bảng/cột; code cũ vẫn chạy được.

-- N1: quan hệ khuyến mãi - sản phẩm chỉ còn lưu ở promotion_products (nhiều-nhiều, V37).
-- Chép liên kết cũ từ products.promotion_id sang bảng nối rồi bỏ trống cột cũ.
INSERT INTO public.promotion_products (promotion_id, product_id)
SELECT p.promotion_id, p.id
FROM public.products p
WHERE p.promotion_id IS NOT NULL
ON CONFLICT (promotion_id, product_id) DO NOTHING;

UPDATE public.products
SET promotion_id = NULL
WHERE promotion_id IS NOT NULL;

-- Chặn ghi lại cột cũ để hai nơi lưu không thể lệch nhau. Cột sẽ được xóa ở migration sau,
-- khi mọi nhánh đã thôi đọc products.promotion_id.
ALTER TABLE public.products
    ADD CONSTRAINT ck_products_promotion_id_deprecated CHECK (promotion_id IS NULL);
COMMENT ON COLUMN public.products.promotion_id IS
    'Deprecated (V41): dùng public.promotion_products. Luôn NULL.';

-- N8: luồng giao hàng dùng delivery_package_claims; mô hình chuyến/điểm dừng đã bỏ khỏi code.
-- Giữ bảng (đang rỗng) đến khi mọi nhánh đã gộp, sau đó xóa ở migration riêng.
COMMENT ON TABLE public.delivery_trips IS
    'Deprecated (V41): thay bằng delivery_package_claims. Không còn code đọc/ghi.';
COMMENT ON TABLE public.delivery_stops IS
    'Deprecated (V41): thay bằng delivery_package_claims. Không còn code đọc/ghi.';
