# Đăng ký, duyệt Customer và đăng nhập — Bite to Business

Cập nhật: 17/10/2026. Backend dùng Supabase Auth để quản lý mật khẩu, phát hành OTP và JWT. Hồ sơ, role, trạng thái cấm và quyết định duyệt nằm trong database nghiệp vụ. Guest là khách chưa đăng nhập, không phải role được lưu trong `users`.

## Luồng đã triển khai

1. Guest gửi form tới `POST /api/auth/register`, không gửi password hoặc role.
2. Backend tạo Auth user chưa xác minh, hồ sơ `users` có role `CUSTOMER`, `user_profiles` và `registration_requests=PENDING`. Mật khẩu kỹ thuật ngẫu nhiên của Auth user không trả về, không gửi email và không lưu trong bảng nghiệp vụ.
3. Admin đăng nhập, xem danh sách/chi tiết hồ sơ và duyệt hoặc từ chối. Chỉ quyết định khi `PENDING`; từ chối phải ghi lý do. Không sửa form hoặc đổi lại quyết định đã xử lý.
4. Sau khi duyệt, backend gọi Supabase Admin `generate_link` để lấy OTP, rồi gửi thư qua Gmail SMTP. Admin không tự chọn mã. Chỉ ghi `otp_sent_at` khi gửi thư thành công.
5. Customer gửi Gmail và OTP tới `POST /api/auth/verify-otp`. Backend kiểm tra hồ sơ đã duyệt, thư đã gửi, mã còn trong cửa sổ 10 phút; Supabase kiểm tra mã dùng một lần. Sau đó cập nhật `is_email_verified=true`, nhưng chưa kích hoạt tài khoản.
6. Response xác minh trả access token để gọi riêng bước tạo mật khẩu. Không trả refresh token của phiên OTP.
7. Customer dùng access token đó làm Bearer token để gọi `POST /api/auth/activate`, gửi password và confirmPassword. Khi Supabase cập nhật password thành công, backend ghi `activated_at`.
8. Customer đăng nhập lại bằng Gmail/password. API nghiệp vụ kiểm tra JWT, nguồn đăng nhập password qua claim `amr`, hồ sơ đã kích hoạt và tài khoản không bị cấm. Role lấy từ database, không tin role trong metadata/JSON do client gửi.

Thư mục cha của các chức năng là `features`, thay cho `modules`. Tài khoản và hồ sơ dùng chung nằm trong `features/user`: `User`, `UserProfile`, `UserRepository`, `UserProfileRepository`, `UserRole`, `CustomerType` và `UserResponse`. Controller và DTO duyệt hồ sơ nằm trong `features/admin/registration`; đăng ký, OTP, đăng nhập và kích hoạt nằm trong `features/auth`. URL API giữ nguyên.

Hai entity `User` và `UserProfile` tiếp tục ánh xạ vào `users` và `user_profiles`. `User` chứa tên, email, số điện thoại, role, xác minh email, cấm tài khoản, avatar và thời điểm tạo/cập nhật. `UserProfile` chứa loại Customer, doanh nghiệp, tỉnh/thành, quận/huyện, phường/xã, địa chỉ giao hàng và toàn bộ thông tin hóa đơn. Các trường hồ sơ không áp dụng cho role được phép null; `invoiceSameAsDelivery` dùng `Boolean`, có thể null thay vì bắt buộc false. Customer đăng ký vẫn phải cung cấp thông tin theo validation của `RegisterRequest`. Admin/Staff có thể không có hồ sơ hoặc có hồ sơ với những trường không áp dụng để null.

Migration `V20261017140000__allow_optional_user_profile_fields.sql` cho phép các trường hồ sơ null trên database đã có V1. Chạy BE với Flyway để áp dụng; không sửa hoặc chạy lại V1. Bảng Auth thuộc Supabase quản lý; backend không ghi trực tiếp `auth.users` bằng JPA.

## Cấu hình để gọi dịch vụ thật

Đặt trong `application-local.properties` đã được gitignore, hoặc dùng biến môi trường tương ứng:

```properties
app.supabase.url=https://retniektrltcpakqixhx.supabase.co
app.supabase.publishable-key=YOUR_PUBLISHABLE_KEY
app.supabase.secret-key=YOUR_SERVER_SECRET_KEY

spring.mail.username=YOUR_GMAIL_ADDRESS
spring.mail.password=YOUR_GMAIL_APP_PASSWORD
```

Production dùng `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.

`secret-key` là **API secret key dành cho server** (`sb_secret_...`), hoặc legacy `service_role` key; đây không phải JWT Signing Key, mật khẩu database hoặc publishable key. Chỉ BE giữ key này. Key thiếu thì API liên quan trả `AUTH_NOT_CONFIGURED`, ứng dụng vẫn có thể khởi động.

Cấu hình JWKS/issuer đã có sẵn; audience là `authenticated`, thuật toán chấp nhận `ES256` (ECC của project) và `RS256`. Backend không tự ký JWT và không cần `app.jwt.secret`. Không bật các hook Supabase tùy chỉnh làm mất `sub`, `amr` hoặc thay đổi audience nếu chưa điều chỉnh backend.

Gửi thư dùng SMTP đang cấu hình ở BE, không yêu cầu cấu hình template OTP bên Supabase. Thời hạn OTP ở phía Supabase cũng cần ít nhất bằng cửa sổ 10 phút của BE; nếu provider hết hạn trước, mã vẫn bị từ chối. Mã OTP chấp nhận 6–10 chữ số theo mã provider phát hành. Admin gửi lại phải cách lần gửi thành công trước ít nhất 60 giây.

Email OTP hiện được render bằng Thymeleaf từ `templates/email/registration-approved-otp.html` và gửi dưới dạng HTML UTF-8 kèm bản text dự phòng. Có thêm template đặt lại mật khẩu và phản hồi yêu cầu sản phẩm để tích hợp khi triển khai hai luồng đó. Biến dữ liệu, phạm vi đã nối và hướng dẫn xem trước nằm ở [README email](../src/main/resources/templates/email/README.md).

## Admin đầu tiên

Không có endpoint công khai tự tạo Admin. Tạo một Auth user Gmail/password đã xác minh trong Supabase Dashboard, copy UUID và thêm hồ sơ `public.users` tương ứng bằng SQL quản trị một lần:

```sql
INSERT INTO public.users (
    id, first_name, last_name, email, phone, role, is_email_verified, is_banned
) VALUES (
    'AUTH_USER_UUID', 'Admin', 'System', 'YOUR_ADMIN_GMAIL', 'YOUR_VN_PHONE',
    'ADMIN', TRUE, FALSE
);
```

Thay toàn bộ placeholder bằng dữ liệu thật trước khi chạy; UUID phải thuộc Auth user vừa tạo. Admin không cần dòng `user_profiles` hoặc `registration_requests`. Hệ thống dự kiến một Admin; không thêm SQL seed chứa mật khẩu vào migration.

## API

| Method | Đường dẫn | Quyền |
|---|---|---|
| POST | `/api/auth/register` | Guest gửi hồ sơ Customer |
| POST | `/api/auth/login` | Gmail/password; chỉ trả phiên nếu hồ sơ ứng dụng đủ điều kiện |
| POST | `/api/auth/verify-otp` | Gmail/OTP của hồ sơ đã được duyệt |
| POST | `/api/auth/activate` | Bearer token sau xác minh OTP; chỉ tạo password ban đầu |
| POST | `/api/auth/refresh` | Refresh token của phiên password |
| POST | `/api/auth/logout` | Phiên password đã kích hoạt |
| GET | `/api/auth/me` | Tài khoản đã kích hoạt |
| GET | `/api/admin/registration-requests?status=PENDING&page=0&size=20` | Admin |
| GET | `/api/admin/registration-requests/{id}` | Admin |
| POST | `/api/admin/registration-requests/{id}/review` | Admin |
| POST | `/api/admin/registration-requests/{id}/resend-otp` | Admin; hồ sơ đã duyệt, chưa kích hoạt |

Phân trang bắt đầu từ 0, size tối đa 100. Thành công dùng `ResponseObject<T>`; lỗi dùng `ErrorResponse` với mã ổn định để FE dịch VI/EN. Các response chứa hồ sơ/token dùng `Cache-Control: no-store`.

### Form đăng ký mẫu

```json
{
  "firstName": "An",
  "lastName": "Nguyễn",
  "email": "your-customer@gmail.com",
  "phone": "0912345678",
  "customerType": "BUSINESS_COOPERATIVE",
  "businessName": "Công ty Bếp Việt",
  "province": "TP. Hồ Chí Minh",
  "district": "Quận Bình Thạnh",
  "ward": "Phường 22",
  "deliveryAddress": "Số nhà, tên đường",
  "invoiceSameAsDelivery": true,
  "invoicePurchaser": "Nguyễn An",
  "invoiceTaxCode": "0123456789",
  "invoiceEmail": "your-customer@gmail.com",
  "invoiceAddress": null
}
```

Loại còn lại là `HOUSEHOLD_INDIVIDUAL_BUSINESS`. Trường `businessName` là tên doanh nghiệp/người đại diện theo form đã thống nhất. Họ/tên chấp nhận chữ Unicode, dấu cách, dấu chấm, dấu nháy và gạch nối. SĐT theo định dạng di động VN `0...` hoặc `+84...`. Các trường hóa đơn có thể null hoặc để trống; nếu nhập MST thì phải đủ 10 chữ số. Không tra cứu doanh nghiệp/MST/Gmail thật; OTP chứng minh khả năng nhận thư. Email chuẩn hóa chữ thường và duy nhất, không hợp nhất dấu chấm/alias Gmail thành một danh tính doanh nghiệp pháp lý.

### Duyệt hoặc từ chối

```json
{ "decision": "APPROVE", "note": null }
```

```json
{ "decision": "REJECT", "note": "Thông tin đăng ký chưa phù hợp." }
```

Nếu quyết định duyệt đã lưu nhưng gửi thư lỗi, API trả `registration.status=APPROVED`, `otpSent=false` và thông báo gửi lại. Không bấm duyệt lần nữa; dùng endpoint `resend-otp`.

### Xác minh OTP, tạo password, đăng nhập

```json
{ "email": "your-customer@gmail.com", "otp": "123456" }
```

Lấy `data.accessToken` của response trên nhập vào **Authorize** của Swagger để gọi `/api/auth/activate`:

```json
{ "password": "YourPassword123!", "confirmPassword": "YourPassword123!" }
```

Password ban đầu dài 8–72 ký tự và phải đáp ứng policy bổ sung của Supabase nếu đã cấu hình. Không trim password. Sau khi kích hoạt, xóa token OTP khỏi FE và đăng nhập bằng:

```json
{ "email": "your-customer@gmail.com", "password": "YourPassword123!" }
```

Refresh gửi `{ "refreshToken": "..." }`. BE hiện trả token trong JSON và nhận access token bằng header `Authorization: Bearer ...`; không dùng cookie xác thực, CORS credentials mặc định false. FE chưa được nối vào các API này. Logout thu hồi refresh session tại Supabase; access JWT có thể còn hợp lệ tới hạn của nó. Ban tài khoản ở database vẫn chặn ngay tại cổng API.

## Tính nhất quán và build

- Tạo `users`, `user_profiles`, `registration_requests` trong cùng DB transaction. Nếu ghi DB thất bại, BE cố xóa Auth user vừa tạo; cleanup thất bại sẽ log UUID để người vận hành đối soát, không tự xóa Auth user đã tồn tại trước đó.
- Khóa dòng registration khi duyệt, gửi mã, xác minh và kích hoạt để ngăn xử lý đồng thời cùng hồ sơ. Database, Supabase Auth và SMTP không có transaction chung; gửi lại OTP là đường phục hồi khi thư lỗi hoặc xác minh đã tiêu thụ mã nhưng DB chưa lưu được. Password cập nhật thành công ở Auth nhưng DB commit lỗi thì tài khoản vẫn bị chặn; cần thử lại bước tạo password, dùng password khác nếu provider từ chối dùng lại.
- Theo yêu cầu của chủ dự án ngày 17/10/2026, các file test tự động và dependency chỉ dùng cho test đã được gỡ khỏi BE. Build hiện không cần H2 hoặc Docker/Testcontainers. Không thêm lại test khi chưa được yêu cầu.
- Kiểm tra biên dịch và đóng gói:

```powershell
.\mvnw.cmd clean package
```

Quên/đổi mật khẩu trong profile, tạo Staff từ màn hình Admin, UI đăng ký/duyệt và thông báo realtime chưa nằm trong phần API triển khai này.

Nguồn đối chiếu: [Supabase createUser](https://supabase.com/docs/reference/javascript/auth-admin-createuser), [generateLink](https://supabase.com/docs/reference/javascript/auth-admin-generatelink), [verifyOtp](https://supabase.com/docs/reference/javascript/auth-verifyotp), [Auth REST OpenAPI](https://github.com/supabase/auth/blob/master/openapi.yaml).
