# Cấu trúc Backend MD Foods

**Kiến trúc:** Spring Boot Modular Monolith, tổ chức theo module nghiệp vụ như dự án SWP391.  
**Trạng thái:** Code hiện tại dùng `features`; các nghiệp vụ còn lại được triển khai dần. Tài liệu này không định nghĩa database schema hay nội dung migration.

## Cấu trúc code hiện tại

### Luồng 3 layer

```text
React / FE
   ⇅ HTTP Request DTO / Response DTO
Controller                     Nhận request, validate đầu vào, trả response
   ⇅
Service                        Business rules, phân quyền nghiệp vụ, transaction
   ⇅
Repository                     Truy vấn và lưu dữ liệu
   ⇅
JPA / Hibernate → JDBC → PostgreSQL tables
```

Controller, Service và Repository là ba layer chính. JPA/Hibernate và JDBC là cơ chế
truy cập DB bên dưới Repository. Controller không gọi Repository trực tiếp; business
rules nằm trong Service. Các tích hợp ngoài như Supabase Auth/SMTP dùng client/service
tương ứng, còn dữ liệu DB nghiệp vụ vẫn đi qua Repository.

### Khi tích hợp AI

AG-UI chuẩn hóa request và luồng event giữa UI và agent; dự án dự kiến dùng HTTP/SSE
để truyền các event này. Các API nghiệp vụ REST hiện có tiếp tục trả JSON.

Luồng dự kiến trong BE, chưa triển khai:

```text
React + AG-UI client
   ⇅ HTTP POST /api/ai/runs; request RunAgentInput, response SSE/AG-UI events
AI Controller
   ⇅
Agent Service → model AI qua model client
   ⇅ tool được BE cho phép
Service nghiệp vụ → Repository → JPA/Hibernate → JDBC → DB
```

Controller nhận request, Agent Service điều phối model/tools, các Service nghiệp vụ
kiểm tra quyền/trạng thái như khi được gọi từ REST. AI không bỏ qua Service hoặc tự
truy cập SQL. Dùng các event chuẩn phù hợp như `RUN_STARTED`, `TEXT_MESSAGE_START`,
`TEXT_MESSAGE_CONTENT`, `TEXT_MESSAGE_END`, `RUN_FINISHED`, `RUN_ERROR`; FE cập nhật
giao diện theo từng event. Endpoint và stream vẫn kiểm tra JWT/quyền sở hữu dữ liệu.

Đây là định hướng cho giai đoạn AI, chưa tích hợp vào BE.
Tham khảo [kiến trúc AG-UI](https://docs.ag-ui.com/concepts/architecture).

### Package hiện tại

Package nghiệp vụ đang dùng `features`, thay cho `modules`. Toàn bộ code đăng ký, đăng nhập,
OTP, tạo mật khẩu và duyệt hồ sơ đã gộp vào `features/admin`; không tạo folder `auth` riêng:

```text
features/admin/
├── client/                 # SupabaseAuthClient
├── controller/             # AuthController, RegistrationAdminController, CreateStaffController, UserSearchHistoryController
├── dto/request/
├── dto/response/
├── entity/                 # RegistrationRequest, User, UserProfile, UserSearchHistory
├── enums/                  # RegistrationStatus, StaffRole, UserRole, CustomerType
├── repository/             # RegistrationRequestRepository, UserRepository, UserProfileRepository, UserSearchHistoryRepository
└── service/                # AuthService, RegistrationService, AdminRegistrationService, AdminStaffService, ...
```

Tài khoản, profile, lịch sử tìm kiếm và các repository tương ứng nằm trong `features/admin`.
Order, package, trạng thái đơn và repository nằm trong `features/customer`.
Đường dẫn API và phân quyền được giữ nguyên; vị trí folder không quyết định ai được truy cập API.

Mỗi module nghiệp vụ có thể chứa `controller/`, `dto/`, `entity/`, `repository/` và `service/`. Chỉ thêm class và tầng cần thiết khi triển khai module; việc tạo folder không có nghĩa database schema đã được chốt.

## Trách nhiệm module

- `customer`: đơn hàng, package và các nghiệp vụ dành cho Customer.
- `catalog`: danh mục và sản phẩm MD Foods bán.
- `pricing`: giá bán và volume pricing.
- `inventory`: tồn kho do Logistics Staff cập nhật.
- `promotion`: khuyến mãi và poster do E-commerce Executive quản lý.
- `review`, `favorite`: đánh giá và sản phẩm yêu thích.
- `quote`: yêu cầu mua, package và thông tin nhận hàng theo nghiệp vụ đã chốt.
- `delivery`: chuyến giao, tuyến hiển thị và thao tác của Delivery Staff.
- `media`: tích hợp Cloudinary cho ảnh sản phẩm/poster.
- `admin`: tài khoản/profile dùng chung, lịch sử tìm kiếm, đăng ký, đăng nhập, OTP, duyệt Customer, gửi email OTP/từ chối bằng `service/RegistrationMailService`, tạo tài khoản cho 3 role Staff và thao tác quản trị được phân quyền.
- `ai`: nhận prompt, gọi model, lấy context qua service nghiệp vụ, sinh/validate A2UI và điều phối action.

AI chỉ gọi service/API của module nghiệp vụ; không truy cập trực tiếp repository của module khác và không tự quyết định giá, tồn, quyền hay trạng thái đơn. Prompt đặt ở Backend, không đặt trong Frontend. Schema database và nội dung migration sẽ bàn riêng trước khi triển khai persistence.

Không tạo module marketplace `supplier`; MD Foods là bên bán. Các ý tưởng chưa chốt như payment gateway, nhập hàng từ nhà cung cấp, import tài liệu hay embedding được bổ sung khi nghiệp vụ được quyết định.

## API Delivery Staff đã triển khai

`features/delivery` có danh sách/chi tiết chuyến được phân công, bắt đầu giao,
xác nhận bàn giao từng package và hoàn tất chuyến cùng Order. Entity/repository Order
và package dùng chung nằm ở `features/customer`. Luồng giữ Controller → Service → Repository;
Service kiểm tra role, assignment, thanh toán, trạng thái và transaction.

Chi tiết endpoint, mã lỗi, dữ liệu ETA và phần phụ thuộc chưa triển khai nằm trong
[`SHIPPER_API.md`](SHIPPER_API.md). Phần này dùng schema đã có, không sửa migration.


