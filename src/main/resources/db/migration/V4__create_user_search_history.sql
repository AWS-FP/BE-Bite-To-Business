-- Lịch sử tìm kiếm của tài khoản đăng nhập; Guest vẫn lưu tạm ở trình duyệt.
CREATE TABLE public.user_search_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    keyword VARCHAR(160) NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_search_history_keyword_nonblank CHECK (BTRIM(keyword) <> '')
);

-- Cùng tài khoản + cùng từ khóa không phân biệt hoa/thường chỉ có một dòng.
-- Tìm lại sẽ cập nhật searched_at để từ khóa lên đầu danh sách.
CREATE UNIQUE INDEX ux_user_search_history_user_keyword_ci
    ON public.user_search_history (user_id, LOWER(keyword));

-- Phục vụ truy vấn lấy 5 từ khóa gần nhất của một tài khoản.
CREATE INDEX ix_user_search_history_user_searched_at
    ON public.user_search_history (user_id, searched_at DESC, id DESC);

-- Truy cập qua backend; không mở lịch sử tìm kiếm qua Supabase public API.
ALTER TABLE public.user_search_history ENABLE ROW LEVEL SECURITY;
