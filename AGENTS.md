# Quy ước Backend

## Kiến trúc 3 layer

- Giữ luồng React/FE → HTTP Request DTO → Controller → Service → Repository → JPA/Hibernate → JDBC → PostgreSQL tables. Response DTO đi ngược về FE.
- Ba layer chính là Controller, Service và Repository. JPA/Hibernate/JDBC là công nghệ truy cập dữ liệu trong tầng persistence.
- Controller nhận/validate request, lấy danh tính đã xác thực, gọi Service và trả HTTP status/Response DTO. Không truy cập Repository trực tiếp.
- Service xử lý business rules, phân quyền nghiệp vụ, kiểm tra trạng thái, điều phối transaction và gọi Repository. Quyết định duyệt/từ chối, tạo Staff, giá/tồn kho/trạng thái đơn nằm ở Service.
- Repository chỉ truy vấn/lưu/xóa dữ liệu. Ràng buộc DB vẫn bảo đảm toàn vẹn và chống ghi đồng thời; xử lý quyết định nghiệp vụ ở Service.
- Entity ánh xạ bảng; DTO dành cho request/response. Không trả thẳng Entity hoặc dữ liệu mật ra API.
- Tích hợp ngoài như Supabase Auth/SMTP/model AI đi qua client/service tương ứng. Dữ liệu DB nghiệp vụ vẫn đi qua Repository.
- Dùng package `features`, không tạo lại cây folder `modules`. Code auth, tài khoản và profile dùng chung gộp vào `features/admin`; Order và package nằm trong `features/customer`. Không tạo lại folder `features/user` hoặc `features/order` riêng.

## AI và AG-UI trong tương lai

- AG-UI là giao thức event cho tương tác agent–UI; HTTP/SSE là transport được chọn cho dự án. Không thay toàn bộ REST/HTTP hiện tại.
- Khi người dùng yêu cầu tích hợp AI: bổ sung endpoint riêng, Controller nhận `RunAgentInput`, Agent Service điều phối model/tools và trả các AG-UI events qua SSE.
- Tool của AI gọi Service nghiệp vụ theo quyền của người dùng đang đăng nhập; không bỏ qua business rules, gọi trực tiếp Repository của tính năng khác hoặc cho model tự thực thi SQL.
- Giữ JWT, kiểm tra quyền và quyền sở hữu dữ liệu. Danh tính/quyền phải do BE xác thực; không tin role, user ID hoặc tools do prompt/FE tự khai.
- Tách event AG-UI khỏi response JSON thông thường; lỗi trước khi mở stream dùng HTTP error, lỗi trong stream dùng event tương ứng như `RUN_ERROR`.
- Đây là định hướng kiến trúc; chưa tích hợp AG-UI hoặc thay provider AI chỉ vì có quy ước này.

## Cách viết code

- Không tự viết hoặc thêm file test/test script. Chỉ viết test khi người dùng yêu cầu rõ ràng; có thể kiểm tra build/lint khi cần mà không tạo test mới.
- Java thụt lề 4 dấu cách, dấu `{` cùng dòng khai báo, mỗi annotation nằm trên dòng riêng phía trên khai báo.
- Ưu tiên code đơn giản, dễ đọc và dùng cấu hình `formatter.xml` hiện có.
- Không sửa migration Flyway đã áp dụng; dùng migration mới khi thật sự cần đổi schema.

Chi tiết: `MD_Foods_AI_BE_Base_Structure.md`. Tham khảo AG-UI: https://docs.ag-ui.com/concepts/architecture.
