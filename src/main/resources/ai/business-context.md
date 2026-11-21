# Business context — Bite to Business

<!-- Phiên bản: btb-context-1 (21/11/2026). Nguồn: code Generate-UI-BE/FE nhánh feat/ai. Không chứa giá hay tồn kho; số liệu động luôn lấy từ API. -->

## Ai dùng website

- **Guest** (chưa đăng nhập): xem danh mục, thương hiệu, sản phẩm, giá, bậc giá, tồn khả dụng và review; thêm sản phẩm vào giỏ lưu trên trình duyệt.
- **Customer** (đã được Admin duyệt và kích hoạt): tạo báo giá từ giỏ, chia gói giao, áp coupon, đặt hàng, thanh toán, theo dõi đơn, viết review, lưu yêu thích, gửi yêu cầu sản phẩm mới.
- Trợ lý AI hiện phục vụ Guest và Customer. Nhân viên (E-commerce, Logistics, Shipper) và Admin không dùng trợ lý này.

## Đăng ký tài khoản

Customer điền form đăng ký bằng Gmail (cá nhân/hộ gia đình hoặc doanh nghiệp/HTX). Admin duyệt hoặc từ chối. Khi được duyệt, hệ thống gửi OTP về Gmail; Customer nhập OTP rồi tự tạo mật khẩu. Không có thời hạn duyệt cố định. Trợ lý không tra được trạng thái hồ sơ.

## Sản phẩm và giá

- Mỗi sản phẩm có SKU, đơn vị bán (kg, chai, cái, gói, khay, hộp, túi, lon, hộp thiếc, hũ, thùng), quy cách, số lượng đặt tối thiểu (MOQ) và bước số lượng.
- Giá niêm yết **chưa gồm VAT**. Giá theo bậc số lượng: mua nhiều hơn có thể được đơn giá thấp hơn.
- Thứ tự tính: giá theo bậc số lượng → giảm theo chương trình sale (nếu đang hiệu lực) → coupon hàng → VAT theo thuế suất riêng của từng sản phẩm. VAT không tính trên phí giao.
- Tồn hiển thị là số còn có thể bán (đã trừ hàng đang giữ). Hết hàng thì không thêm mới được.

## Giỏ hàng, báo giá và đơn hàng

1. Giỏ hàng là báo giá nháp. Khi Customer đăng nhập, giỏ được chuyển thành báo giá trên hệ thống.
2. Customer tạo gói giao (tên, địa chỉ, ngày giờ giao) và chia đủ toàn bộ số lượng của mọi sản phẩm vào các gói.
3. Phí giao tính riêng cho từng gói theo quãng đường thực tế từ kho: **miễn phí 5 km đầu, sau đó 5.000đ/km**, không làm tròn lên km.
4. Mỗi báo giá dùng tối đa **một coupon hàng** và **một coupon phí giao**.
5. Hệ thống tự kiểm tra giá, ưu đãi, VAT, phí giao và tồn kho. **Không có bước nhân viên duyệt báo giá.** Customer xem báo giá cuối và xác nhận.
6. Khi xác nhận, hệ thống tạo một đơn và **giữ hàng 1 giờ**. Nếu thiếu bất kỳ mặt hàng nào thì không tạo đơn (không nhận đơn thiếu hàng).
7. Thanh toán **chỉ bằng chuyển khoản QR qua SePay**, một mã QR cho cả đơn. Quá 1 giờ chưa thanh toán thì hàng được giải phóng. Không có COD, thẻ, ví điện tử, công nợ hay trả chậm.

## Giao hàng

Một kho. Sau khi thanh toán được xác nhận, các gói được giao cho shipper; shipper tự nhận từng gói, xem tuyến và bấm hoàn tất khi giao thực tế. Thời gian dự kiến chỉ mang tính ước lượng.

## Sau mua

- Customer xem danh sách đơn, chi tiết đơn, trạng thái từng gói, hóa đơn và xem trước VAT.
- Customer gửi yêu cầu hoàn tiền cho đơn đã thanh toán thành công, kèm lý do. Hoàn tiền không tự động; nhân viên xử lý.
- Review: thang 5 sao, có thể kèm ảnh, đăng ngay không cần duyệt.
- Customer có thể gửi yêu cầu bán thêm sản phẩm chưa có; bộ phận kho phản hồi qua email.

## Chức năng CHƯA có (trả lời là chưa hỗ trợ)

Hạn mức công nợ, mua chịu, hạng thành viên/điểm thưởng, đơn định kỳ, đặt lại đơn cũ tự động, đổi địa chỉ hoặc giờ giao sau khi đặt đơn, gửi tài liệu chứng nhận theo yêu cầu, mẫu thử, nhiều kho, giao theo chi nhánh tự động, thanh toán ngoài SePay QR. Trợ lý không tự hủy đơn hay hoàn tiền.
