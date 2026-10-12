# Bảng package_items

Lưu số lượng của từng dòng Order được phân bổ vào một package. Một dòng hàng có thể nằm trong nhiều package và một package chứa nhiều dòng hàng.

| Cột | Kiểu | Ý nghĩa |
| --- | --- | --- |
| order_package_id | UUID, NOT NULL | Package nhận hàng |
| order_item_id | UUID, NOT NULL | Dòng hàng đã xác nhận trong Order |
| order_id | UUID, NOT NULL | Cột đối chiếu để database đảm bảo hai liên kết thuộc cùng Order |
| quantity | NUMERIC(12,3), NOT NULL | Số lượng của dòng hàng trong package, phải lớn hơn 0 |

Khóa chính ghép `(order_package_id, order_item_id)` đảm bảo không phân bổ trùng cùng dòng hàng trong cùng package. Không thêm `id`, tên sản phẩm, đơn vị, giá hoặc note; lấy các thông tin đó từ `order_items`.

Hai khóa ngoại ghép `(order_package_id, order_id)` và `(order_item_id, order_id)` tham chiếu khóa UNIQUE `(id, order_id)` ở hai bảng cha. Index bổ sung: `order_item_id` để tìm package theo dòng hàng và `order_id` để lấy toàn bộ phân bổ của đơn. Khóa chính đã hỗ trợ tìm hàng theo package.

## Đánh đổi chuẩn hóa

Ba cột nghiệp vụ là package, dòng hàng và số lượng. `order_id` là dữ liệu lặp có chủ ý: có thể suy ra từ mỗi bảng cha, nên bảng phân bổ không hoàn toàn đạt 3NF/BCNF nếu xét các phụ thuộc đó. Đổi lại, hai khóa ngoại ghép chặn liên kết chéo Order ngay tại database, không cần trigger. Giá trị lặp luôn được khóa ngoại kiểm tra, không thể tự đi lệch khỏi bảng cha.

## Luồng tạo và hủy

- Khi chuyển Quote thành Order, backend copy các phân bổ của package Quote còn hiệu lực vào bảng này, trong cùng transaction tạo Order/items/packages và giữ tồn.
- Backend khóa dữ liệu liên quan, kiểm tra quyền, trạng thái, bước số lượng và tổng phân bổ bằng đúng số lượng mỗi `order_item`. SQL CHECK chỉ kiểm tra lượng từng dòng dương; không tự kiểm tra tổng nhiều dòng.
- Khi hủy package, giữ dòng phân bổ để biết chính xác hàng/số lượng bị hủy. Chỉ giải phóng tồn đã giữ hoặc hoàn tồn khi đúng bước nghiệp vụ; không mặc định hàng đang giao/đã giao tự về kho.
- Phân bổ lại Quote không sửa snapshot của Order đã xác nhận.
- RLS đã bật; policy/API quyền truy cập chưa được triển khai trong bước schema này.

Schema nằm trong [V1__init_schema.sql](../../src/main/resources/db/migration/V1__init_schema.sql). Đây là bản nháp đang xây dựng; thay đổi này chưa chạy lên Supabase. Nếu V1 đã được áp dụng trên một môi trường, phải dùng migration mới cho môi trường đó.
