# API Shipper (Delivery Staff)

Code nằm trong `features/delivery`, theo Controller → Service → Repository → JPA → JDBC.
Entity/repository đọc Order và package dùng chung nằm trong `features/customer`.
Các bảng đã có trong V1/V3; phần này không thêm hoặc sửa migration.

Tất cả endpoint cần `Authorization: Bearer <access_token>` của tài khoản `DELIVERY_STAFF`
đang hoạt động, đăng nhập bằng mật khẩu. BE lấy UUID từ JWT và kiểm tra role trong DB;
request không nhận `staffId`, role hoặc thời điểm giao từ FE.

## Endpoint

| Method | URL | Chức năng |
| --- | --- | --- |
| GET | `/api/delivery/trips?status=READY&page=0&size=10` | Danh sách chuyến được phân công, gồm cả lịch sử |
| GET | `/api/delivery/trips/current` | Chuyến PREPARING, READY hoặc IN_DELIVERY hiện tại |
| GET | `/api/delivery/trips/{tripId}` | Chi tiết Order, package, hàng hóa và tuyến |
| POST | `/api/delivery/trips/{tripId}/start` | Bắt đầu chuyến READY |
| POST | `/api/delivery/trips/{tripId}/stops/{stopId}/confirm-delivery` | Xác nhận đã bàn giao thực tế một package |
| POST | `/api/delivery/trips/{tripId}/complete` | Hoàn tất chuyến và Order sau khi giao đủ |

Ba POST không cần body. Mọi phản hồi thành công có HTTP 200, `Cache-Control: no-store`,
và khung `{ "status": 200, "message": "Success", "data": ... }`.
Danh sách dùng `data.content`, `page`, `size`, `totalElements`, `totalPages`;
`status` là bộ lọc tùy chọn, page bắt đầu từ 0, size từ 1 đến 100.
Chưa có chuyến hiện tại trả 404 `DELIVERY_TRIP_NOT_FOUND`; danh sách trống trả 200.

Chỉ xem/sửa chuyến được giao cho chính mình. ID chuyến của người khác cũng trả 404,
không tiết lộ chuyến đó có tồn tại hay không. Role khác trả 403 `ACCESS_DENIED`.

## Dữ liệu chi tiết

- `trip`: id, orderId, orderNumber, trạng thái chuyến/Order, các thời điểm dự kiến/thực tế.
- `recipientName`, `recipientPhone`, `deliveryNote`: snapshot `orders.contact_name`,
  `contact_phone`, `note`; không lấy lại thông tin từ hồ sơ Customer hiện hành.
- `origin`: tọa độ kho đã lưu trên chuyến; `routePolyline`: tuyến đã được Logistics chuẩn bị.
- `speedKmPerHour`: hằng số 40; `totalDistanceKm`, `estimatedDurationSeconds`,
  `estimatedCompletionAt`: tính từ các chặng của package còn hiệu lực.
- `activePackages`, `deliveredPackages`: số package còn hiệu lực và số đã bàn giao trong đó.
- `stops`: điểm dừng theo `stopOrder`, mã/tên package, địa chỉ, ngày giao dự kiến,
  tọa độ, `legDistanceKm`, `estimatedArrivalAt`, `deliveredAt`, `cancelledAt` và `items`.
- `items`: SKU, tên VI/EN, đơn vị, số lượng của riêng package và ghi chú dòng hàng;
  `comboComponents` chứa thành phần combo theo snapshot lúc đặt, đã nhân số combo
  phân bổ vào package. Không trả giá thanh toán, hóa đơn hay thông tin tài khoản.

Địa chỉ/ngày giao lấy từ `order_packages`; hàng từ `package_items` và `order_items`;
thành phần combo từ `order_combo_items`. BE kiểm tra mọi stop thực sự thuộc Order của chuyến.
Package đã hủy vẫn được giữ trong lịch sử nếu đã có stop, nhưng không cần xác nhận giao.

Trước xuất phát, ETA dùng `plannedDepartureAt`; sau xuất phát dùng `startedAt`.
ETA mỗi điểm = thời điểm xuất phát + tổng km tới điểm đó / 40 km/h.
Thời lượng làm tròn lên giây. Khoảng cách thiếu thì tổng/ETA tương ứng là null;
thiếu giờ xuất phát thì ETA là null. `deliveredAt` luôn là thời điểm bàn giao thực tế.
Map có thể dùng điểm dừng khi `routePolyline` null. BE không gọi Vietmap, không lấy GPS
và không tự hoàn tất khi ETA đã qua; FE dùng tọa độ/tuyến để hiển thị bản đồ.

## Quy tắc cập nhật

1. Bắt đầu: chuyến READY, Order PROCESSING/chưa hủy, có payment SUCCESS với số tiền
   bằng tổng Order, đúng VND và có paid_at. Tất cả package còn hiệu lực phải có hàng,
   stop, tọa độ và khoảng cách chặng. Chưa đủ dữ liệu thì từ chối xuất phát.
2. Bàn giao: chuyến IN_DELIVERY, Order PROCESSING, stop thuộc chuyến và package chưa hủy.
   Shipper tự xác nhận sau bàn giao; không khóa theo ETA hay bắt buộc đúng thứ tự dự kiến.
3. Hoàn tất: mọi package chưa hủy đều có stop đã bàn giao. Chuyến và Order chuyển
   COMPLETED cùng thời điểm, trong cùng transaction. Giao package cuối chưa tự hoàn tất;
   vẫn cần bấm nút Hoàn tất đơn hàng.
4. Gửi lặp start/confirm/complete đã thành công trả trạng thái hiện tại, không ghi lại
   thời điểm xuất phát/bàn giao/hoàn tất và không thay đổi tồn kho.
5. Command khóa Order trước, rồi khóa Trip và kiểm tra lại assignment. Start, confirm và
   complete đồng thời trên cùng chuyến được xử lý lần lượt. Luồng Logistics/thanh toán/hủy
   được triển khai sau phải khóa cùng Order trước khi sửa assignment, package hoặc thanh toán;
   sau đó khóa Trip nếu sửa chuyến. Không đổi Order nguồn của trip/package.
6. Lỗi khóa/ghi đồng thời được trả 409 `CONFLICT`, giữ khung lỗi thống nhất.

## Mã lỗi nghiệp vụ

| HTTP | Code | Ý nghĩa |
| --- | --- | --- |
| 404 | `DELIVERY_TRIP_NOT_FOUND` | Không có chuyến được phân công tương ứng |
| 404 | `DELIVERY_STOP_NOT_FOUND` | Stop không thuộc chuyến này |
| 409 | `DELIVERY_TRIP_INVALID_STATE` | Chuyến chưa ở trạng thái cho phép thao tác |
| 409 | `DELIVERY_ROUTE_NOT_READY` | Thiếu điểm giao, tọa độ, km hoặc hàng của package |
| 409 | `DELIVERY_NO_ACTIVE_PACKAGES` | Không còn package cần giao |
| 409 | `DELIVERY_PACKAGE_CANCELLED` | Package đã bị hủy |
| 409 | `DELIVERY_PACKAGES_NOT_DELIVERED` | Chưa bàn giao đủ package |
| 409 | `DELIVERY_DATA_INVALID` | Trip, stop và Order không nhất quán |
| 409 | `ORDER_PAYMENT_REQUIRED` | Chưa có thanh toán thành công đủ tổng Order |
| 409 | `ORDER_NOT_READY_FOR_DELIVERY` | Order chưa PROCESSING |
| 409 | `ORDER_CANCELLED` | Order đã hủy |

Lỗi request dùng `INVALID_REQUEST`/`VALIDATION_FAILED`. Khung lỗi có `status`, `code`,
`message`, `path`, `errors`, `timestamp` như các API khác.

## Phạm vi và kiểm tra

Phần này không tạo Order, thu tiền, nhập/xuất kho hoặc tạo/phân công chuyến. Các luồng đó
thuộc Customer/E-commerce/Logistics/payment và chưa có API trong code hiện tại.
Muốn chạy đủ luồng cần các bước trước tạo dữ liệu hợp lệ; không đưa API tạo chuyến
hoặc tự đánh dấu đã thanh toán vào quyền Shipper. Trường hợp Order tổng 0 vẫn chưa có
payment SUCCESS nên bị chặn xuất phát; cần chốt chính sách miễn thanh toán trước khi mở luồng đó.
Không triển khai giao thất bại, hủy trong chuyến, trả hàng/hoàn tiền hoặc notification/SSE.

Order entity hiện chỉ ánh xạ các cột cần đọc/cập nhật cho giao hàng; khi triển khai tạo
Order cần bổ sung đầy đủ snapshot và validation của luồng checkout. Không dùng entity này
để tạo một Order thiếu dữ liệu bắt buộc.

Kiểm tra build bằng `./mvnw.cmd -DskipTests compile`. Không tạo file test. Build không
chứng minh API đã chạy với Supabase; chưa chạy luồng giao hàng trên database trong lần code này.
