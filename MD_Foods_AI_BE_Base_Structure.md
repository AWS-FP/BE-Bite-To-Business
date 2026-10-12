# Cấu trúc Backend MD Foods

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
    ├── main/
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
    │   │   └── modules/
    │   │       ├── identity/
    │   │       ├── customer/
    │   │       ├── catalog/
    │   │       ├── pricing/
    │   │       ├── inventory/
    │   │       ├── promotion/
    │   │       ├── review/
    │   │       ├── favorite/
    │   │       ├── quote/
    │   │       ├── order/
    │   │       ├── delivery/
    │   │       ├── notification/
    │   │       ├── media/
    │   │       ├── admin/
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
    └── test/java/com/se203730/mvc/generateuibe/modules/
        └── [test package theo từng module]
```

Mỗi module nghiệp vụ có thể chứa `controller/`, `dto/`, `entity/`, `repository/` và `service/`. Chỉ thêm class và tầng cần thiết khi triển khai module; việc tạo folder không có nghĩa database schema đã được chốt.

## Trách nhiệm module

- `identity`: đăng nhập, tài khoản, xác thực và role.
- `customer`: thông tin Customer và loại khách hàng cá nhân/hộ gia đình hoặc doanh nghiệp/hợp tác xã.
- `catalog`: danh mục và sản phẩm MD Foods bán.
- `pricing`: giá bán và volume pricing.
- `inventory`: tồn kho do Logistics Staff cập nhật.
- `promotion`: khuyến mãi và poster do E-commerce Executive quản lý.
- `review`, `favorite`: đánh giá và sản phẩm yêu thích.
- `quote`: yêu cầu mua, package và thông tin nhận hàng theo nghiệp vụ đã chốt.
- `order`: xác nhận, theo dõi và hủy đơn.
- `delivery`: chuyến giao, tuyến hiển thị và thao tác của Delivery Staff.
- `notification`: email và thông báo trong hệ thống.
- `media`: tích hợp Cloudinary cho ảnh sản phẩm/poster.
- `admin`: metric và thao tác quản trị được phân quyền.
- `ai`: nhận prompt, gọi model, lấy context qua service nghiệp vụ, sinh/validate A2UI và điều phối action.

AI chỉ gọi service/API của module nghiệp vụ; không truy cập trực tiếp repository của module khác và không tự quyết định giá, tồn, quyền hay trạng thái đơn. Prompt đặt ở Backend, không đặt trong Frontend. Schema database và nội dung migration sẽ bàn riêng trước khi triển khai persistence.

Không tạo module marketplace `supplier`; MD Foods là bên bán. Các ý tưởng chưa chốt như payment gateway, nhập hàng từ nhà cung cấp, import tài liệu hay embedding được bổ sung khi nghiệp vụ được quyết định.


