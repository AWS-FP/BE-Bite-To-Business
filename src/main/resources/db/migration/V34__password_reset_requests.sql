-- Quên mật khẩu tự phục vụ: mỗi tài khoản giữ một dòng cho lần yêu cầu gần nhất.
-- Mã OTP do Supabase tạo và kiểm tra; bảng chỉ lưu mốc thời gian để giới hạn gửi lại,
-- thời hạn mã và bảo đảm phiên đặt mật khẩu thuộc đúng lần yêu cầu.
CREATE TABLE public.password_reset_requests (
    user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    otp_generated_at TIMESTAMPTZ NOT NULL,
    otp_sent_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    CONSTRAINT ck_password_reset_sent_after_generated
        CHECK (otp_sent_at IS NULL OR otp_sent_at >= otp_generated_at),
    CONSTRAINT ck_password_reset_verified_after_generated
        CHECK (verified_at IS NULL OR verified_at >= otp_generated_at),
    CONSTRAINT ck_password_reset_completed_after_verified
        CHECK (completed_at IS NULL OR (verified_at IS NOT NULL AND completed_at >= verified_at))
);

ALTER TABLE public.password_reset_requests ENABLE ROW LEVEL SECURITY;
