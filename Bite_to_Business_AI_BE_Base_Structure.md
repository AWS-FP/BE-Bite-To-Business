# Cấu trúc Backend Bite to Business

**Kiến trúc:** Spring Boot Modular Monolith, tổ chức theo module nghiệp vụ như dự án SWP391.  
**Trạng thái:** Cây thư mục mục tiêu; các module sẽ được triển khai dần. Tài liệu này không định nghĩa database schema hay nội dung migration.

```text
Generate-UI-BE/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── Dockerfile
├── .env.example
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── database/              # Để trống nội dung schema đến khi bàn riêng
│   └── design/
└── src/
    └── main/
    │   ├── java/com/se203730/mvc/generateuibe/
    │   │   ├── GenerateUiBeApplication.java
    │   │   ├── config/
    │   │   │   ├── security/
    │   │   │   └── web/
    │   │   ├── common/
    │   │   │   ├── api/
    │   │   │   ├── exception/
    │   │   │   ├── security/
    │   │   │   └── validation/
    │   │   └── features/
    │   │       ├── auth/
    │   │       ├── user/
    │   │       ├── customer/
    │   │       │   ├── quote/
    │   │       │   ├── order/
    │   │       │   ├── favorite/
    │   │       │   └── review/
    │   │       ├── ecommerce/
    │   │       │   ├── catalog/
    │   │       │   ├── pricing/
    │   │       │   └── promotion/
    │   │       ├── logistics/
    │   │       │   └── inventory/
    │   │       ├── delivery/
    │   │       │   └── trip/
    │   │       ├── notification/
    │   │       ├── media/
    │   │       ├── admin/
    │   │       │   └── registration/
    │   │       └── ai/
    │   │           ├── controller/
    │   │           ├── dto/
    │   │           ├── client/
    │   │           └── service/
    │   │               ├── orchestration/
    │   │               ├── context/
    │   │               ├── tools/
    │   │               ├── a2ui/
    │   │               └── memory/
    │   └── resources/
    │       ├── application.properties
    │       ├── application-local.properties
    │       ├── application-prod.properties
    │       ├── prompts/
    │       ├── a2ui/
    │       │   ├── catalog/
    │       │   └── schemas/
    │       ├── static/
    │       ├── templates/
    │       └── db/migration/
```

`features` là thư mục cha của các chức năng. Folder theo role chứa các chức năng thuộc role đó, ví dụ `admin/registration` và `logistics/inventory`. Mỗi chức năng có thể chứa `controller/`, `dto/`, `entity/`, `repository/` và `service/`; chỉ thêm tầng cần thiết khi triển khai. Các phần dùng chung như `auth`, `user`, `notification`, `media` và `ai` nằm trực tiếp trong `features`.

Package theo cùng cấu trúc thư mục. Cách đặt folder không tự cấp quyền truy cập; Spring Security và service tiếp tục kiểm tra quyền theo role. Entity/repository dùng chung được tái sử dụng, không sao chép theo từng role. Hiện luồng đăng ký và kích hoạt dùng chung nằm trong `auth`; controller và DTO xét duyệt của Admin nằm trong `admin/registration`. Các file test tự động đã được gỡ theo yêu cầu của chủ dự án.

## Trách nhiệm module

- `auth`: đăng ký, đăng nhập, OTP, kích hoạt và xác thực phiên.
- `user`: tài khoản và hồ sơ dùng chung cho mọi role; chứa `User`, `UserProfile`, `UserRole`, `CustomerType`, repository và DTO người dùng. `User` lưu thông tin tài khoản bắt buộc; `UserProfile` chứa đủ thông tin loại Customer, doanh nghiệp, địa chỉ và hóa đơn. Trường không áp dụng cho role được phép null; `invoiceSameAsDelivery` dùng `Boolean` để phân biệt null với false. Staff/Admin có thể chưa có hồ sơ hoặc có hồ sơ với các trường riêng để null. Giữ hai bảng `users` và `user_profiles`; migration mới cho phép các trường hồ sơ null, không sửa V1 đã chạy. Form đăng ký Customer vẫn yêu cầu các thông tin đã chốt.
- `customer/quote`, `customer/order`: yêu cầu mua, package, thông tin nhận hàng, theo dõi và hủy đơn theo nghiệp vụ đã chốt.
- `customer/review`, `customer/favorite`: đánh giá và sản phẩm yêu thích.
- `ecommerce/catalog`: danh mục và sản phẩm Bite to Business bán; các role khác tái sử dụng dữ liệu catalog.
- `ecommerce/pricing`: giá bán và volume pricing.
- `ecommerce/promotion`: khuyến mãi và poster do E-commerce Executive quản lý.
- `logistics/inventory`: tồn kho do Logistics Staff cập nhật.
- `delivery/trip`: chuyến giao, tuyến hiển thị và thao tác của Delivery Staff.
- `notification`: email và thông báo trong hệ thống.
- `media`: tích hợp Cloudinary cho ảnh sản phẩm/poster.
- `admin/registration`: xem và duyệt yêu cầu đăng ký Customer. Chức năng quản trị khác sẽ có thư mục riêng trong `admin` khi triển khai.
- `ai`: nhận prompt, gọi model, lấy context qua service nghiệp vụ, sinh/validate A2UI và điều phối action.

AI chỉ gọi service/API của module nghiệp vụ; không truy cập trực tiếp repository của module khác và không tự quyết định giá, tồn, quyền hay trạng thái đơn. Prompt đặt ở Backend, không đặt trong Frontend. Schema database và nội dung migration sẽ bàn riêng trước khi triển khai persistence.

Không tạo module marketplace `supplier`; Bite to Business là bên bán. Các ý tưởng chưa chốt như payment gateway, nhập hàng từ nhà cung cấp, import tài liệu hay embedding được bổ sung khi nghiệp vụ được quyết định.


