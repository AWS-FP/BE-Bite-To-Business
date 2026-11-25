-- Bỏ khái niệm "bước số lượng" do nhân viên nhập: mọi sản phẩm tăng/giảm theo bước 1, chỉ giữ MOQ.
-- Số lượng hợp lệ là MOQ, MOQ + 1, MOQ + 2, ... (BE kiểm tra (số lượng - MOQ) chia hết cho bước).
-- Cột vẫn giữ để FE/AI đọc như cũ, nhưng luôn bằng 1; sản phẩm mới dùng default.
UPDATE public.products SET quantity_step = 1 WHERE quantity_step <> 1;

ALTER TABLE public.products ALTER COLUMN quantity_step SET DEFAULT 1;
