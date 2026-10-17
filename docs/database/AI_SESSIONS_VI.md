# Bảng ai_sessions

Cập nhật: 13/10/2026. Lưu phiên AI theo tác vụ; chat và đặt hàng nhanh có thể dùng chung một phiên. Bảng đã thêm vào V1; chức năng AI/session chưa triển khai.

| Cột | Kiểu | Ý nghĩa |
| --- | --- | --- |
| id | UUID, PK | ID phiên |
| user_id | UUID, FK users.id, nullable | Chủ phiên; Guest để NULL |
| session_type | VARCHAR(30), NOT NULL | SHOPPING_ASSISTANT hoặc POSTER_DESIGN |
| title | VARCHAR(200), nullable | Tên phiên, không chấp nhận chuỗi trắng |
| language_code | VARCHAR(2), NOT NULL, mặc định vi | vi hoặc en |
| current_route | VARCHAR(255), nullable | Route FE bắt đầu bằng /, không phải API URL |
| context | JSONB, NOT NULL, mặc định {} | Object ngữ cảnh ngắn hạn |
| context_version | BIGINT, NOT NULL, mặc định 1 | Số phiên bản dương; kiểm soát response đến trễ |
| status | VARCHAR(20), NOT NULL, mặc định ACTIVE | ACTIVE, CLOSED hoặc EXPIRED |
| created_at | TIMESTAMPTZ, NOT NULL, mặc định NOW() | Tạo phiên |
| updated_at | TIMESTAMPTZ, NOT NULL, mặc định NOW() | Cập nhật phiên |
| expires_at | TIMESTAMPTZ, nullable | Hạn sử dụng, phải sau created_at |
| closed_at | TIMESTAMPTZ, nullable | Thời điểm đóng; bắt buộc khi CLOSED |

Index `(user_id, updated_at DESC)` cho lịch sử tài khoản, lọc user_id không NULL; `(status, expires_at)` cho quét phiên hết hạn, lọc expires_at không NULL. Chưa thêm GIN context vì chưa có truy vấn JSON cụ thể.

`context` có thể chứa active_product_id, selected_quantity, intent, điều kiện mua và quote_id; backend xác thực các tham chiếu trước khi dùng. JSONB dùng cho trạng thái tác vụ thay đổi theo loại phiên, không thay bảng sản phẩm/giá/tồn hoặc khóa ngoại nghiệp vụ. Hội thoại, UI và memory dài hạn sẽ thiết kế ở bước sau.

POSTER_DESIGN bắt buộc có chủ tài khoản; backend còn phải kiểm tra quyền E-commerce. Guest chỉ có thể dùng SHOPPING_ASSISTANT sau khi có cơ chế xác minh chủ phiên, chưa triển khai trong bước này. RLS bật nhưng chưa có policy; không mở quyền đọc công khai cho phiên user_id NULL.

API sau này cập nhật context/version/updated_at trong transaction. Response chỉ được áp khi version còn khớp. CLOSED cần closed_at trong khoảng created_at tới updated_at. EXPIRED cần expires_at và updated_at đã tới hạn, closed_at để NULL. Thời gian trôi không tự cập nhật status; truy cập phải kiểm tra hạn thực tế.

FK user_id dùng ON DELETE RESTRICT, không chuyển phiên tài khoản thành Guest khi xóa user. Ban/gỡ ban vẫn là nghiệp vụ tài khoản riêng.

Schema: [V1__init_schema.sql](../../src/main/resources/db/migration/V1__init_schema.sql). Lần đầu chạy BE với Flyway bật sẽ áp dụng lên database của profile hiện hành nếu V1 chưa chạy. Sau khi migration được áp dụng trên Supabase dùng chung, thêm migration mới cho mọi thay đổi tiếp theo; không sửa V1 đã chạy.

Kiểm tra ngày 13/10/2026: toàn bộ V1 và 411 kiểm tra schema pass trên PostgreSQL tạm (PGlite, có auth.users giả lập); Maven package pass bằng Java 21. Smoke check khởi động BE khi AI tắt và Swagger HTTP 200 đã pass, với datasource/Flyway bị loại khỏi smoke check. Các kiểm tra này không kết nối Supabase; kết nối thực và migration trên database dùng chung sẽ được xác nhận khi chủ dự án chạy BE.

Xác nhận ngày 14/10/2026: chủ dự án đã chạy BE và Flyway áp dụng V1 thành công lên Supabase development. Truy vấn chỉ đọc xác nhận version 1 có success=true và đủ 30 bảng nghiệp vụ, gồm ai_sessions. Từ thời điểm này không chỉnh sửa V1; dùng migration mới khi thay đổi schema. Việc tạo bảng chưa triển khai API AI/session.
