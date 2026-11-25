# Task prompt — Sinh giao diện A2UI v0.9 (tìm sản phẩm)

<!-- Phiên bản: btb-ui-1 (21/11/2026). Đi kèm ai/catalog.json (btb-genui-product-search-1) và a2ui/catalog/bite-to-business.catalog.json. -->

Bạn nhận:

- `<intent>`: kết quả bước nhận diện ý định.
- `<data_model_summary>`: tóm tắt data model backend đã ghi: `resultCount`, `query`, và cho mỗi sản phẩm `index`, `name`, `priceAvailable`, `inStock` (còn hàng để bán), `hasPriceTiers` (có từ 2 bậc giá); khi tra cứu thông tin còn có `origin`, `hasStorageGuide`, `hasNutrition`, `hasOriginStory`, `matchedOn`. Chỉ đưa vào `ProductFacts.sections` mục có dữ liệu (ví dụ không có `hasNutrition` thì không chọn `nutrition`). Không có giá trong tóm tắt; bạn không cần và không được viết giá.
- `<ui_preference>`: lựa chọn hiển thị người dùng nói trong phiên (nếu có).
- `<ui_profile>`: thói quen hiển thị dài hạn của khách (nếu có, `evidenceEvents ≥ 10`). Khi người dùng không nói rõ kiểu hiển thị: `preferredView: "table"` → dùng `ProductTable`; `"grid"` → `ProductGrid`; component trong `ignoresComponents` (ví dụ `SuggestedPrompts`) thì bỏ. Yêu cầu trong lượt và `<ui_preference>` luôn thắng.
- `<catalog>`: danh sách component, action và giới hạn.

Nhiệm vụ: chọn bố cục phù hợp nhất để người dùng hoàn thành việc đang làm, rồi xuất **một** message A2UI `updateComponents`:

```json
{
  "version": "v0.9",
  "updateComponents": {
    "surfaceId": "<giữ nguyên surfaceId được cấp>",
    "components": [
      {"id": "root", "component": "Column", "children": ["title", "results"]},
      {"id": "title", "component": "Text", "text": "Ba chỉ bò", "variant": "h3"},
      {"id": "results", "component": "ProductGrid", "items": {"path": "/products"}, "columns": 3}
    ]
  }
}
```

## Quy tắc bắt buộc

1. Phải có đúng một component `id: "root"`. Mọi id là duy nhất, viết thường, dạng `kebab-case`. Mọi id được tham chiếu trong `children`/`child` phải tồn tại; không có vòng lặp.
2. Chỉ dùng component trong catalog. Tối đa 25 component, sâu tối đa 6 tầng.
3. Danh sách sản phẩm luôn bind `{"path": "/products"}`. Một sản phẩm cụ thể bind `{"path": "/products/<index>"}` với index có trong tóm tắt. Text có thể bind `{"path": "/query/text"}`.
4. Không viết giá, số tồn, MOQ, % giảm, mã coupon, SKU hay mã đơn vào literal. Renderer lấy các giá trị đó từ data model.
5. Action chỉ dùng: `view_product`, `add_to_cart`, `refine_search`, `send_prompt`, `open_page`. ProductGrid/ProductTable/ProductCard tự có nút xem và thêm vào giỏ; không cần tạo Button riêng cho từng sản phẩm.
6. Text ngắn (≤ 300 ký tự), cùng ngôn ngữ người dùng, không hứa hẹn ("giá tốt nhất", "giao trong 2 giờ") khi không có dữ liệu.

## Chọn bố cục

| Tình huống | Bố cục nên dùng |
| --- | --- |
| Tìm chung, ≥ 2 kết quả | `Text` tiêu đề + `ProductGrid` (3 cột) + `SuggestedPrompts` |
| Người dùng muốn dạng bảng, so sánh, hoặc hỏi giá/MOQ cho nhiều sản phẩm | `ProductTable` với cột phù hợp (`name, spec, price, priceTiers, moq, stock`); `highlight: "lowestPrice"` khi người dùng ưu tiên giá |
| Hỏi giá sỉ theo số lượng | Có `priceTiers` (bảng) hoặc `showPriceTiers: true` (lưới); 1 kết quả thì `ProductCard` + `PriceTiers` |
| Hỏi còn hàng | Lưới/bảng có cột `stock`; 1 kết quả thì `ProductCard` + `InventoryBadge` |
| Hỏi thông tin sản phẩm (`ASK_PRODUCT_INFO`: bảo quản, xuất xứ, dinh dưỡng, làm từ gì) | `ProductFacts` cho sản phẩm đầu (`/products/0`) với `sections` theo `info_topics`; nhiều kết quả thì thêm `ProductTable` (cột `name, origin, price, stock`) để chọn sản phẩm khác |
| Tìm theo thuộc tính (`attributes`: đông lạnh, xuất xứ Úc, giàu đạm…) | `ProductTable` có cột `origin` khi lọc xuất xứ, hoặc `ProductGrid`; tiêu đề nêu điều kiện ("Hàng đông lạnh xuất xứ Úc"); tóm tắt có `matchedOn` cho biết sản phẩm khớp điều kiện nào |
| Đúng 1 kết quả | `ProductCard` (`/products/0`) với `variant: "detailed"` |
| 0 kết quả (`resultCount = 0`) | `Notice` tone `empty` + `ClarifyQuestion` hoặc `SuggestedPrompts` gợi ý từ khóa khác; không render ProductGrid |
| Tất cả sản phẩm không có giá (`priceAvailable=false`) | Thêm `Notice` tone `info`: giá chưa cập nhật |
| Muốn thêm vào giỏ | Hiển thị sản phẩm với `showAddToCart: true` và `Notice` nhắc người dùng tự bấm thêm, kiểm tra số lượng trong giỏ |
| `needs_clarification = true` | `ClarifyQuestion` với 2–6 lựa chọn. Mỗi lựa chọn là từ khóa tìm kiếm ngắn, viết có dấu (vd "ba chỉ bò", "viên thả lẩu"); khi có kết quả thì ưu tiên lấy cụm từ trong tên sản phẩm của tóm tắt |
| Người dùng nói "gọn" / "chi tiết" | `density: "compact"` / `"comfortable"`; ProductCard `variant` tương ứng |

Gợi ý trong `SuggestedPrompts` phải là việc hệ thống làm được (xem dạng bảng, xem bậc giá, sắp xếp mới nhất, mở giỏ hàng), không gợi ý công nợ, đơn định kỳ hay hủy đơn.
