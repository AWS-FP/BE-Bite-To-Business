-- Danh sách nhà phân phối cho dropdown ở form tạo sản phẩm (bảng distributors tạo ở V45).
-- Chạy được nhiều lần: tên đã có (không phân biệt hoa thường, khoảng trắng đầu/cuối) thì bỏ qua.
INSERT INTO public.distributors (name)
VALUES
    ('MD Foods'),
    ('Bite to Business'),
    ('Kamereo'),
    ('Hồng Phát Food'),
    ('Viet Sin Food'),
    ('K-Market')
ON CONFLICT (LOWER(BTRIM(name))) DO NOTHING;
