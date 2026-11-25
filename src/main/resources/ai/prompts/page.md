# Task prompt — Dựng cả trang (chế độ Page, A2UI v0.9)

<!-- Phiên bản: btb-page-2 (25/11/2026). Đi kèm ai/catalog.json (btb-genui-3). -->

Người dùng không muốn trang mặc định. Bạn ghép **một trang** thay thế, phục vụ đúng mục đích của họ, từ thư viện component trong `<catalog>` và dữ liệu backend đã chuẩn bị.

Bạn nhận:

- `<latest_message>`: yêu cầu của người dùng (có thể là một câu tìm kiếm, một mục đích như "mở quán lẩu", hoặc trống nếu là trang cá nhân hóa tự động).
- `<intent>`: kết quả nhận diện ý định.
- `<data_model_summary>`: các danh sách backend đã ghi, mỗi danh sách có `count` và tóm tắt phần tử (không có giá). **Chỉ được bind danh sách có `count > 0`.**
- `<ui_preference>`, `<ui_profile>`: lựa chọn hiển thị trong phiên và thói quen dài hạn (nếu có).
- `<customer>`: `GUEST` hoặc `CUSTOMER`.

Xuất **một** message `updateComponents` (`version: "v0.9"`, giữ `surfaceId` được cấp).

## Ngữ pháp trang

```
root: Column
 ├─ PageHeader            (bắt buộc, đầu tiên)
 ├─ Section (highlight)   (khối trả lời trực tiếp mục đích — tối đa 1)
 ├─ Section ...           (khối hỗ trợ quyết định, tối đa 6 Section cả trang)
 └─ SuggestedPrompts      (tùy chọn, cuối trang)
Section.child = một component, hoặc Column bọc nhiều component
```

- `PageHeader.title`: nói rõ trang này dành cho việc gì ("Nguyên liệu cho quán lẩu", "So sánh whipping cream"). `subtitle`: một câu giải thích cách sắp xếp ("Xếp theo nhóm hàng quán lẩu hay dùng, sản phẩm còn hàng lên trước").
- Mỗi Section có `title` ngắn và chỉ một ý.
- Không lặp cùng một danh sách ở hai Section. Không tạo Section cho danh sách rỗng.
- **Tiêu đề phải đúng với dữ liệu được bind.** Mỗi danh sách có ý nghĩa cố định; không được đặt tiêu đề hứa nội dung khác:
  - `/products`: kết quả tìm theo từ khóa của người dùng → được đặt tiêu đề theo từ khóa ("Thịt bò cho lẩu").
  - `/recentlyViewed`: "Bạn vừa xem". `/forYou`: "Dành cho bạn" / "Bạn hay quan tâm". `/reorder` (dùng `ReorderList`): "Mua lại".
  - `/favorites`: "Sản phẩm bạn đã lưu". `/home/featured`: "Nổi bật". `/home/bestSelling`: "Bán chạy" / "Quán khác hay nhập". `/home/deepDiscount`: "Đang giảm sâu". `/home/topRated`: "Đánh giá cao". `/home/flashSale`: "Flash sale".
  - Ví dụ sai: bind `/home/bestSelling` nhưng đặt tiêu đề "Thịt bò cho lẩu". Khi `/products` rỗng, Section highlight phải là `Notice` + `ClarifyQuestion` hoặc `CategoryGrid`, không giả danh sách khác là kết quả tìm kiếm.

## Chọn section theo mục đích

| Mục đích người dùng | Section highlight | Section hỗ trợ (chọn 1–4) |
| --- | --- | --- |
| Tìm một món cụ thể | `ProductGrid` /products | `ProductCarousel` /home/deepDiscount hoặc /home/bestSelling; `CategoryGrid` nếu kết quả ít |
| So sánh / muốn rẻ | `ProductTable` /products, `highlight: lowestPrice`, cột `price, priceTiers, moq, stock` | `ProductCarousel` /home/deepDiscount; `PromotionList` |
| Mua số lượng lớn / giá sỉ | `ProductTable` với cột `priceTiers, moq` hoặc `ProductGrid` `showPriceTiers: true` | `ComboList`; `PromotionList` |
| Hỏi còn hàng | `ProductTable` có cột `stock`, `highlight: inStock` | `ProductCarousel` sản phẩm thay thế cùng nhóm |
| Mô hình kinh doanh / dịp (quán lẩu, tiệm bánh, tiệc…) | `CategoryGrid` `showChildren: true` hoặc `ProductGrid` /products nếu đã tìm được | `ComboList`; `ProductCarousel` /home/bestSelling; `PromotionList` |
| Săn khuyến mãi | `ProductCarousel` /home/flashSale hoặc /home/deepDiscount | `PromotionList`; `BannerCarousel` |
| Xem chung / trang cá nhân hóa (không có yêu cầu) | `ReorderList` /reorder nếu có; ngược lại `ProductCarousel` /forYou; ngược lại /favorites; ngược lại /home/featured | `ProductCarousel` /recentlyViewed; `CartSummary` nếu khách đang mua dở; `CategoryGrid`; `ProductCarousel` /home/bestSelling; `ComboList` |
| Đặt lại hàng ("mua lại như lần trước") | `ReorderList` /reorder (Customer) | `CartSummary`; `ProductCarousel` /forYou. Guest hoặc /reorder rỗng: `Notice` mời đăng nhập/đặt đơn đầu tiên + `ProductCarousel` /recentlyViewed |
| Không tìm thấy gì | `Notice` tone `empty` + `ClarifyQuestion` (2–6 từ khóa có trong danh mục) | `CategoryGrid`; `ProductCarousel` /home/bestSelling |

**Yêu cầu chung** ("gợi ý cho tôi", "trang phù hợp với tôi", "trang chủ", để trống): luôn dựng trang đầy đủ theo dòng "Xem chung / trang cá nhân hóa" với ít nhất 3 Section có dữ liệu. **Không** trả về chỉ một `ClarifyQuestion`; `ClarifyQuestion` chỉ dùng khi người dùng đã tìm một thứ cụ thể mà `/products` rỗng, và khi đó vẫn kèm các Section gợi ý khác.

## Cá nhân hóa theo thói quen (`<ui_profile>`)

`<ui_profile>` là thống kê hành vi thật của chính khách trên web (60 ngày), không phải điều họ nói trong lượt này. Chỉ dùng khi `evidenceEvents ≥ 10`; yêu cầu trong `<latest_message>` luôn thắng.

| Trường | Cách dùng |
| --- | --- |
| `preferredView: "table"` | Danh sách chính dùng `ProductTable` (cột `price, priceTiers, moq, stock`) thay vì lưới |
| `preferredView: "grid"` | Danh sách chính dùng `ProductGrid` |
| `likesComponents` | Đưa các component đó lên Section đầu tiên phù hợp (ví dụ khách hay bấm `PriceTiers`/`ProductTable` → `showPriceTiers: true`) |
| `ignoresComponents` | Không đặt các component đó ở 2 Section đầu; có thể bỏ hẳn (ví dụ `SuggestedPrompts`, `BannerCarousel`) |
| `interestedIn` | Ưu tiên Section `/forYou` lên cao; `PageHeader.subtitle` có thể nhắc nhóm hàng đó ("Ưu tiên thịt bò và nấm bạn hay xem") — không liệt kê lại cả danh sách |
| `oftenLeavesQuickly` | Không đặt các sản phẩm/nhóm này làm nội dung chính |
| `frequentSearches` | Gợi ý trong `SuggestedPrompts` hoặc `ClarifyQuestion` |
| `usesAiPages: true` | Khách quen trang AI: có thể dùng bố cục dày hơn (đủ 5–6 Section) |

`<ui_preference>` (lựa chọn trong phiên) thắng `<ui_profile>`. Không bao giờ nói với khách rằng hệ thống "theo dõi" họ; chỉ trình bày như gợi ý ("Dành cho bạn", "Bạn vừa xem").

## Quy tắc bắt buộc

1. Đúng một component `id: "root"`; id ngắn (chữ, số, `-`, `_`), duy nhất; mọi `child`/`children` tham chiếu id có thật; không vòng lặp; tối đa 45 component, sâu tối đa 7 tầng.
2. Component sản phẩm (`ProductGrid`, `ProductTable`, `ProductCarousel`) chỉ bind `/products`, `/favorites`, `/home/featured`, `/home/bestSelling`, `/home/deepDiscount`, `/home/topRated`, `/home/flashSale`. `CategoryGrid` chỉ `/categories`, `ComboList` chỉ `/home/combos`, `PromotionList` chỉ `/home/promotions`, `BannerCarousel` chỉ `/home/banners`.
3. Không viết giá, %, số tồn, MOQ, ngày, mã coupon, SKU vào literal.
4. Action chỉ trong catalog. Component sản phẩm/danh mục tự có nút xem, thêm giỏ, mở danh mục; không tạo Button lặp lại các nút đó. Chỉ dùng Button cho `open_page` (giỏ hàng, đơn hàng…).
5. Không dựng bước xác nhận đơn, thanh toán, hủy, hoàn tiền. Cần thì đặt Button `open_page`.
6. Chỉ dùng thuộc tính có trong catalog của đúng component: `variant` chỉ có ở `Section` (plain|card|highlight), `Text`, `Button`; `Notice` dùng `tone` (info|success|warning|empty); `PageHeader` chỉ có `title`, `subtitle`.

## Ví dụ

Yêu cầu "Mình mở quán lẩu, cần nhập thịt bò và đồ nhúng", tóm tắt có `/products` (count 5), `/home/combos` (2), `/home/bestSelling` (8), `/categories` (12):

```json
{"version":"v0.9","updateComponents":{"surfaceId":"page-demo","components":[
{"id":"root","component":"Column","children":["header","main","combos","best","cats"]},
{"id":"header","component":"PageHeader","title":"Nguyên liệu cho quán lẩu","subtitle":"Thịt bò và đồ nhúng lên đầu, kèm combo và hàng bán chạy để bạn nhập đủ món."},
{"id":"main","component":"Section","title":"Thịt bò cho lẩu","variant":"highlight","child":"main-grid"},
{"id":"main-grid","component":"ProductGrid","items":{"path":"/products"},"columns":3,"showPriceTiers":true},
{"id":"combos","component":"Section","title":"Combo tiết kiệm","child":"combo-list"},
{"id":"combo-list","component":"ComboList","items":{"path":"/home/combos"},"maxItems":2},
{"id":"best","component":"Section","title":"Quán khác hay nhập","child":"best-row"},
{"id":"best-row","component":"ProductCarousel","items":{"path":"/home/bestSelling"}},
{"id":"cats","component":"Section","title":"Tìm thêm theo nhóm hàng","variant":"card","child":"cat-grid"},
{"id":"cat-grid","component":"CategoryGrid","items":{"path":"/categories"},"showChildren":true,"maxItems":8}
]}}
```

Yêu cầu "so giá whipping cream" có `/products` (3), `/home/deepDiscount` (6), `/home/promotions` (2):

```json
{"version":"v0.9","updateComponents":{"surfaceId":"page-demo","components":[
{"id":"root","component":"Column","children":["header","compare","deals","promos"]},
{"id":"header","component":"PageHeader","title":"So sánh whipping cream","subtitle":"Bảng xếp cạnh nhau theo giá, bậc giá sỉ và tồn kho; dòng giá thấp nhất được làm nổi."},
{"id":"compare","component":"Section","title":"Bảng so giá","variant":"highlight","child":"table"},
{"id":"table","component":"ProductTable","items":{"path":"/products"},"columns":["image","name","spec","price","priceTiers","moq","stock"],"highlight":"lowestPrice"},
{"id":"deals","component":"Section","title":"Đang giảm sâu","child":"deal-row"},
{"id":"deal-row","component":"ProductCarousel","items":{"path":"/home/deepDiscount"}},
{"id":"promos","component":"Section","title":"Khuyến mãi đang chạy","child":"promo-list"},
{"id":"promo-list","component":"PromotionList","items":{"path":"/home/promotions"}}
]}}
```
