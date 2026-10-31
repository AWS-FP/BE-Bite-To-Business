# Tài khoản Staff do Admin tạo

Admin mở **Tài khoản Staff** (`/admin/staff`) trên FE và chọn **Tạo tài khoản Staff**.
Nhập họ, tên, Gmail, số điện thoại, bộ phận, mật khẩu ban đầu và xác nhận mật khẩu.
Staff đăng nhập ngay bằng Gmail/mật khẩu vừa được cấp; role quyết định trang sau đăng nhập.

## API

Hai endpoint đều yêu cầu phiên đăng nhập bằng mật khẩu của một Admin đang hoạt động.

- `GET /api/admin/staff`: lọc `role`, tìm `keyword`, phân trang `page` (từ 0) và `size` (1–100).
- `POST /api/admin/staff`: tạo Staff và trả `201` với thông tin tài khoản, không trả mật khẩu/token.

```json
{
    "firstName": "An",
    "lastName": "Nguyễn",
    "email": "nhanvien@gmail.com",
    "phone": "0912345678",
    "role": "LOGISTICS_STAFF",
    "password": "Example123!",
    "confirmPassword": "Example123!"
}
```

Role chỉ nhận `ECOMMERCE_EXECUTIVE`, `LOGISTICS_STAFF`, `DELIVERY_STAFF`.
`ADMIN`, `CUSTOMER` và role không tồn tại bị từ chối. Mật khẩu dài 8–72 ký tự và giữ nguyên
khoảng trắng; Supabase có thể áp dụng thêm chính sách mật khẩu của project.

Gmail được trim/chuyển chữ thường; số điện thoại được chuẩn hóa về `+84` trước khi kiểm tra trùng.
Gmail/số điện thoại trùng trả `EMAIL_ALREADY_EXISTS`/`PHONE_ALREADY_EXISTS` (`409`).
Xác nhận mật khẩu sai trả `PASSWORD_CONFIRMATION_MISMATCH` (`400`).
Các lỗi đều theo khung `ErrorResponse`, FE hiển thị cả mã lỗi và HTTP status.

## Dữ liệu và xác thực

- Tạo Auth user bằng Supabase Admin API với `email_confirm=true` và mật khẩu Admin nhập.
- Lưu cùng UUID vào `public.users`, role Staff được chọn, `is_email_verified=true`, `is_banned=false`.
- Staff không cần hồ sơ địa chỉ/hóa đơn `user_profiles` hoặc yêu cầu Customer `registration_requests`.
- Nếu lưu/commit database thất bại, rollback dữ liệu và dọn Auth user mới tạo; không xóa tài khoản cũ.
- Không cần migration mới: các bảng và 3 giá trị role đã tồn tại.

Code, entity và repository tài khoản dùng chung nằm trong `features/admin`.
Thư mục khung cũ `modules` đã được dọn khi không còn file; không tạo lại cấu trúc này.
