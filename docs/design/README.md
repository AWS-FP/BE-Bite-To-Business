# Tài liệu dự án MD Foods AI

Các tài liệu đang dùng trong workspace:

1. [Master Context](../../../PROJECT_MASTER_CONTEXT_V4_VI.md) — bối cảnh, vai trò, phạm vi và công nghệ.
2. [Business Requirements](../../../BUSINESS_REQUIREMENTS_V1_VI.md) — use case, nghiệp vụ, quy tắc và câu hỏi còn mở.
3. [Role Functions](../../../ROLE_FUNCTIONS_V1_VI.md) — quyền và chức năng của Guest, Customer, E-commerce Executive, Logistics Staff, Delivery Staff và Admin.
4. [Product Catalog](../../../PRODUCT_CATALOG_V1_VI.md) — nhóm thực phẩm, sản phẩm dự kiến và câu hỏi chi tiết cần chủ dự án điền.
5. [AI Website & A2UI Research](../../../AI_WEBSITE_A2UI_RESEARCH_V1_VI.md) — nghiên cứu tích hợp AI, cá nhân hóa và renderer A2UI.

MD Foods là bên bán trực tiếp. Một Customer có một tài khoản đại diện và đăng nhập bằng Gmail. Admin duyệt đăng ký Customer; hệ thống gửi OTP tới Gmail đăng ký, sau đó Customer tự tạo password và xác nhận. Admin tạo tài khoản Staff bằng Gmail/password trong màn hình quản trị và gán role E-commerce Executive, Logistics Staff hoặc Delivery Staff. Khi reset password, Admin kích hoạt gửi OTP để người dùng tự đặt lại; không gửi password qua email.

Mỗi Order tương ứng một chuyến giao và có thể có nhiều package; mỗi package có thể có nhiều điểm đến. Customer có thể hủy Order trước khi chuyến bắt đầu; hàng chưa giao được trả về lượng có thể bán. Không có use case giao thất bại hoặc đổi trả trong demo. Review không duyệt trước và hiển thị nguyên ngôn ngữ người viết; nội dung sản phẩm và poster phải có tiếng Việt lẫn tiếng Anh.

Tech stack hiện chọn Supabase PostgreSQL. Thiết kế dữ liệu sẽ được làm riêng, từng bảng một cùng chủ dự án.

