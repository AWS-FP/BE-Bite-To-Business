/**
 * Module AI của Bite to Business: Generative UI theo A2UI v0.9, chạy model trên AWS Bedrock.
 *
 * <h2>Luồng một request ({@code POST /api/ai/agent}, SSE theo sự kiện AG-UI)</h2>
 * <pre>
 * AgentController ─► AgentService (kiểm tra Guest/Customer, giới hạn lượt, chạy nền)
 *   └─► GenerativeUiService
 *        1. Guardrail Bedrock kiểm tra câu người dùng (chặn nội dung ngoài phạm vi)
 *        2. classify(): Bedrock nhận diện ý định (prompts/intent.md) → JSON intent + slot
 *        3. UiProfileService: hồ sơ thói quen (long-term memory) từ ui_interaction_events
 *        4. Tool lấy dữ liệu thật qua service nghiệp vụ (không cho model chạm DB):
 *             ProductSearchTool (catalog/pricing), PageDataTool (trang chủ, danh mục, yêu thích,
 *             xem gần đây, dành cho bạn), ReorderTool (mua lại từ đơn đã hoàn tất)
 *        5. Bedrock chọn bố cục: chỉ sinh message A2UI updateComponents
 *             - mode "chat": khối nhỏ trong khung trợ lý (prompts/ui.md)
 *             - mode "page": ghép cả trang thay trang mặc định (prompts/page.md)
 *        6. A2uiValidator kiểm tra theo catalog; sai thì gửi lỗi cho model sửa 1 lần,
 *           vẫn sai hoặc Bedrock lỗi thì dùng bố cục mặc định (UI không bao giờ trắng)
 *        7. Phát sự kiện: TEXT_MESSAGE_*, CUSTOM "a2ui" [createSurface, updateDataModel, updateComponents],
 *           STATE_SNAPSHOT (trạng thái hiển thị + chẩn đoán)
 * </pre>
 *
 * <h2>Nguyên tắc an toàn</h2>
 * <ul>
 *   <li>Model chỉ quyết định <b>bố cục</b>. Giá, tồn, MOQ, khuyến mãi nằm trong data model do backend ghi
 *       ({@code updateDataModel}); model chỉ bind path như {@code {"path":"/products"}}, không viết số.</li>
 *   <li>Action trên giao diện chỉ thuộc allowlist của catalog; FE thực hiện qua router/giỏ hàng thật, backend
 *       kiểm tra lại giá/tồn/quyền khi tạo báo giá.</li>
 *   <li>Danh tính lấy từ JWT, không tin role/userId trong prompt. anonId chỉ dùng để đọc thói quen của chính
 *       trình duyệt đó.</li>
 * </ul>
 *
 * <h2>Tài nguyên (classpath)</h2>
 * <ul>
 *   <li>{@code ai/system-prompt.md}, {@code ai/business-context.md}: vai trò và nghiệp vụ (viết từ code).</li>
 *   <li>{@code ai/prompts/intent.md | ui.md | page.md}: prompt từng bước.</li>
 *   <li>{@code ai/catalog.json}: hợp đồng component/action/data model gửi cho model;
 *       {@code a2ui/catalog/bite-to-business.catalog.json}: JSON Schema catalog A2UI dùng để validate.</li>
 *   <li>{@code ai/examples/*.jsonl}: ví dụ few-shot (dataset 13, 16); tập EVAL không nằm ở đây.</li>
 * </ul>
 *
 * <h2>Chức năng AI khác</h2>
 * <ul>
 *   <li>{@code POST /api/ai/translate}: gợi ý dịch Việt ↔ Anh cho form sản phẩm (Logistics/E-commerce).</li>
 *   <li>{@code POST /api/ui-events}: nhận sự kiện tương tác (xem trang, back nhanh, bấm widget…) làm dữ liệu
 *       cho hồ sơ thói quen.</li>
 * </ul>
 */
package com.se203730.mvc.generateuibe.features.ai;
