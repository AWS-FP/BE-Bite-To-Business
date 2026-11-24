# Business context — Bite to Business

<!-- Phiên bản: btb-context-2 (25/11/2026). Viết lại từ code Generate-UI-BE/FE nhánh feat/ai. Không chứa giá hay tồn kho; số liệu động luôn lấy từ API. Khi code đổi quy tắc, cập nhật file này và tăng phiên bản. -->

## 1. Bite to Business là gì

Website bán thực phẩm tươi và nguyên liệu bếp **trực tiếp** (không phải sàn) cho nhà hàng, quán ăn, khách sạn, tiệm bánh, cửa hàng, doanh nghiệp, hợp tác xã và hộ gia đình. Bite to Business là bên bán duy nhất. Giao diện có tiếng Việt và tiếng Anh.

## 2. Người dùng

- **Guest** (chưa đăng nhập): xem danh mục, thương hiệu, sản phẩm, combo, giá, bậc giá, tồn có thể bán, review, khuyến mãi; thêm hàng vào giỏ lưu trên trình duyệt.
- **Customer**: tài khoản cá nhân/hộ gia đình hoặc doanh nghiệp/HTX, đăng ký bằng Gmail. Admin duyệt hồ sơ, hệ thống gửi OTP về Gmail, khách tự tạo mật khẩu. Quên mật khẩu thì đặt lại bằng OTP. Không có thời hạn duyệt cố định.
- Nhân viên (E-commerce, Logistics, Shipper) và Admin làm việc ở khu vực riêng; trợ lý AI và trang do AI dựng chỉ phục vụ Guest và Customer.

## 3. Sản phẩm, giá và khuyến mãi

- Sản phẩm có SKU, tên Việt/Anh, quy cách, xuất xứ, thương hiệu, danh mục cha/con, đơn vị bán (kg, chai, cái, gói, khay, hộp, túi, lon, hộp thiếc, hũ, thùng), **số lượng đặt tối thiểu (MOQ)** và **bước số lượng** (chỉ đặt MOQ, MOQ + bước, MOQ + 2 bước…).
- **Combo**: một sản phẩm gồm nhiều món, có số suất ăn, giá riêng.
- Giá niêm yết **chưa gồm VAT**. Có **giá theo bậc số lượng**: mua nhiều hơn có thể được đơn giá thấp hơn.
- **Sale**: sản phẩm thuộc một chương trình khuyến mãi đang chạy thì có giá sale và ngày kết thúc. Trang chủ có các khối: nổi bật, bán chạy, giảm sâu, đánh giá cao, flash sale, combo, khuyến mãi đang chạy, banner.
- **Thứ tự tính tiền**: giá theo bậc → giảm theo sale → coupon hàng → VAT theo **thuế suất riêng của từng sản phẩm** (tính trên tiền hàng sau giảm, không tính trên phí giao) → cộng phí giao trừ coupon phí giao.
- **Tồn hiển thị** là số còn có thể bán (đã trừ hàng đang giữ cho đơn khác). Hết hàng thì không thêm mới được.
- Tìm kiếm khớp chuỗi con trên tên Việt, tên Anh, SKU; **có phân biệt dấu**. Có gợi ý tìm kiếm và sản phẩm liên quan.

## 4. Coupon

- Hai loại: **coupon hàng** và **coupon phí giao**. Mỗi báo giá dùng tối đa một mã mỗi loại.
- Mã giảm theo **%** hoặc **số tiền cố định**; có thể có giá trị đơn tối thiểu, mức giảm tối đa, thời gian hiệu lực, tổng lượt dùng và lượt dùng mỗi khách; có mã chỉ áp cho một số sản phẩm.
- Khách xem danh sách mã còn dùng được ngay trong giỏ hàng. Mã được kiểm tra lại và khóa khi tạo đơn.

## 5. Giỏ hàng → báo giá → đơn hàng

1. Giỏ hàng chính là báo giá nháp. Khi Customer đăng nhập, giỏ được đưa lên hệ thống thành báo giá có `revision`.
2. Khách tạo **gói giao**: tên gói, người nhận, địa chỉ, ngày và khung giờ giao. Mỗi gói một địa chỉ, **không trùng địa chỉ giữa các gói**. Phải chia **đủ toàn bộ số lượng** của mọi sản phẩm vào các gói.
3. **Chỉ giao trong TP.HCM.** Phí giao mỗi gói tính theo quãng đường thực tế từ kho: **miễn phí 5 km đầu, sau đó 5.000đ/km**, không làm tròn lên km.
4. Hệ thống tự kiểm tra giá, sale, coupon, VAT, phí giao, tồn kho. Giá thay đổi thì báo giá bị coi là cũ và khách phải xem lại. Sản phẩm chưa có thuế suất VAT thì chưa đặt được.
5. Customer xác nhận báo giá cuối → hệ thống tạo **một đơn** và **giữ hàng 6 giờ**. Thiếu bất kỳ mặt hàng nào thì không tạo đơn (không nhận đơn thiếu hàng).

## 6. Thanh toán

- **Chỉ chuyển khoản QR qua SePay** (BIDV, tài khoản ảo). Một mã QR cho cả đơn, nội dung chuyển khoản có mã thanh toán dạng `BTB` + 12 ký tự.
- Hệ thống tự xác nhận khi ngân hàng báo tiền về. Quá hạn giữ hàng chưa thanh toán thì hàng được giải phóng; tiền về muộn hoặc lệch được đưa vào đối soát.
- Không có COD, thẻ, ví điện tử, công nợ hay trả chậm.

## 7. Giao hàng

- Một kho ở TP.HCM. Sau khi thanh toán, các gói được hiển thị cho shipper; shipper **tự nhận từng gói**, xem tuyến đường và bấm hoàn tất khi giao thực tế. Thời gian dự kiến chỉ là ước lượng.
- Gói quá ngày giao mà chưa shipper nào nhận sẽ **tự hủy** và khách được thông báo.

## 8. Hủy, hoàn tiền, trả hàng

- **Hủy đơn**: khách tự hủy khi đơn **chưa thanh toán**.
- **Hủy gói đã thanh toán**: gửi yêu cầu hoàn tiền kèm lý do, chỉ với gói **chưa có shipper nhận**. Hủy một gói không hủy các gói khác. E-commerce duyệt và hoàn tiền chuyển khoản thủ công.
- **Trả hàng**: gửi yêu cầu trong **24 giờ sau khi gói được giao**. Admin chấp nhận thì hàng nhập lại kho và tiền được hoàn bằng chuyển khoản thủ công.

## 9. Sau mua và tài khoản

- Xem danh sách đơn, chi tiết đơn, trạng thái từng gói, hóa đơn, xem trước VAT.
- **Review**: thang 0,5–5 sao (có nửa sao), tối đa 5 ảnh, mỗi khách một review đang hiển thị cho mỗi sản phẩm, đăng ngay; E-commerce có thể ẩn review vi phạm.
- **Yêu thích**: lưu sản phẩm để xem lại.
- **Yêu cầu sản phẩm mới**: khách gửi tên, link tham khảo, ghi chú; bộ phận kho phản hồi và có thể báo qua email.
- Thông báo trong web và email theo cài đặt của khách; có đăng ký nhận bản tin khuyến mãi.

## 10. Chức năng CHƯA có (trả lời là chưa hỗ trợ, không hứa)

Công nợ, mua chịu, trả chậm, hạng thành viên/điểm thưởng, đơn định kỳ, tự động đặt lại đơn cũ, đổi địa chỉ hoặc giờ giao sau khi đặt đơn, giao ngoài TP.HCM, nhiều kho, mẫu thử, gửi chứng từ theo yêu cầu, thanh toán ngoài SePay QR. AI không tự hủy đơn, hoàn tiền, áp mã hay thanh toán thay khách.
