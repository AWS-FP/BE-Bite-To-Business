-- Mỗi sản phẩm có tối đa một bản chi tiết; sản phẩm chưa nhập nội dung có thể chưa có dòng.
-- Tên, SKU, xuất xứ ngắn, đơn vị bán, quy cách và MOQ vẫn lấy từ products.
CREATE TABLE public.product_details (
    product_id UUID PRIMARY KEY REFERENCES public.products (id) ON DELETE CASCADE,

    -- 1. Thông số kỹ thuật và logistics. Đơn vị cố định giúp BE/AI so sánh và tính toán.
    net_weight_kg NUMERIC(12, 3),
    tare_weight_kg NUMERIC(12, 3),
    volume_m3 NUMERIC(12, 6),
    length_cm NUMERIC(10, 2),
    width_cm NUMERIC(10, 2),
    height_cm NUMERIC(10, 2),
    technical_content_vi TEXT,
    technical_content_en TEXT,

    -- 2. Tiêu chuẩn bếp trưởng, hướng dẫn bảo quản và sơ chế.
    kitchen_storage_content_vi TEXT,
    kitchen_storage_content_en TEXT,

    -- 3. Nội dung giới thiệu hồ sơ VSATTP và chính sách hóa đơn VAT.
    -- Đây là nội dung hiển thị, không phải tỷ lệ VAT dùng để tính tiền hoặc hóa đơn thực tế.
    compliance_vat_content_vi TEXT,
    compliance_vat_content_en TEXT,

    -- 4. Thuyết minh nguồn gốc; không lưu lặp mã/tên xuất xứ ngắn trong products.origin.
    origin_content_vi TEXT,
    origin_content_en TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- CHECK vẫn cho phép NULL khi thông số không áp dụng hoặc Staff chưa nhập.
    CONSTRAINT ck_product_details_net_weight CHECK (net_weight_kg > 0),
    CONSTRAINT ck_product_details_tare_weight CHECK (tare_weight_kg >= 0),
    CONSTRAINT ck_product_details_volume CHECK (volume_m3 > 0),
    CONSTRAINT ck_product_details_length CHECK (length_cm > 0),
    CONSTRAINT ck_product_details_width CHECK (width_cm > 0),
    CONSTRAINT ck_product_details_height CHECK (height_cm > 0)
);


-- PRIMARY KEY(product_id) đã tạo index phục vụ truy vấn chi tiết và JOIN với products.
-- Thống nhất với các bảng hiện có: truy cập dữ liệu qua Backend, không mở policy trực tiếp cho FE.
ALTER TABLE public.product_details ENABLE ROW LEVEL SECURITY;

-- Giữ nguyên products.description_vi/en và dữ liệu cũ trong lần bổ sung này.
-- updated_at được service cập nhật khi Staff chỉnh sửa, giống các bảng nghiệp vụ hiện tại.

-- Hóa đơn VAT của đơn hàng; mỗi Order có tối đa một hóa đơn trong phạm vi demo.
-- Người mua, MST, email nhận, địa chỉ và tổng tiền lấy từ snapshot trong orders.
-- Dòng sản phẩm, số lượng, giá và VAT từng dòng lấy từ snapshot trong order_items.
-- Không đọc lại user_profiles, giá catalog hoặc thuế hiện hành khi dựng lại hóa đơn cũ.
CREATE TABLE public.invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE RESTRICT,

    -- Chưa phát hành thì có thể chưa cấp số hoặc tạo file PDF.
    invoice_number VARCHAR(50) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    pdf_url TEXT,
    issued_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    issued_at TIMESTAMPTZ,
    emailed_at TIMESTAMPTZ,

    -- Hủy giữ nguyên số/file đã xuất để tra cứu, không xóa lịch sử hóa đơn.
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_invoices_number_nonblank CHECK (
        invoice_number IS NULL OR BTRIM(invoice_number) <> ''
    ),
    CONSTRAINT ck_invoices_pdf_url_nonblank CHECK (
        pdf_url IS NULL OR BTRIM(pdf_url) <> ''
    ),
    CONSTRAINT ck_invoices_status CHECK (
        status IN ('DRAFT', 'ISSUED', 'CANCELLED')
    ),
    -- Đã phát hành phải có số, file PDF và ngày xuất.
    CONSTRAINT ck_invoices_issued_state CHECK (
        status <> 'ISSUED' OR (
            invoice_number IS NOT NULL AND pdf_url IS NOT NULL AND issued_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_invoices_draft_state CHECK (
        status <> 'DRAFT' OR (issued_at IS NULL AND emailed_at IS NULL)
    ),
    -- Hóa đơn đã xuất rồi hủy cũng phải giữ số và file gốc.
    CONSTRAINT ck_invoices_issued_details CHECK (
        issued_at IS NULL OR (invoice_number IS NOT NULL AND pdf_url IS NOT NULL)
    ),
    CONSTRAINT ck_invoices_cancelled_state CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL AND BTRIM(cancellation_reason) <> '')
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancellation_reason IS NULL)
    ),
    CONSTRAINT ck_invoices_email_time CHECK (
        emailed_at IS NULL OR (issued_at IS NOT NULL AND emailed_at >= issued_at)
    ),
    CONSTRAINT ck_invoices_cancellation_time CHECK (
        cancelled_at IS NULL OR cancelled_at >= COALESCE(issued_at, created_at)
    )
);

-- UNIQUE(order_id) và UNIQUE(invoice_number) đã có index, không tạo thêm index trùng.
CREATE INDEX ix_invoices_status_issued_at ON public.invoices (status, issued_at DESC);
CREATE INDEX ix_invoices_issued_by ON public.invoices (issued_by) WHERE issued_by IS NOT NULL;

ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;

-- Backend kiểm tra quyền xuất/xem hóa đơn, điều kiện đơn hàng và giữ nguyên snapshot đã dùng.
-- Khi phát hành: tạo PDF từ Order/items đã chốt rồi lưu số/file/issued_at và status = ISSUED.
-- emailed_at chỉ được ghi sau khi dịch vụ email chấp nhận gửi thành công.
-- Bảng này lưu hồ sơ xuất hóa đơn demo; việc tạo PDF và gửi email sẽ triển khai ở service.

-- Combo cũng là một sản phẩm: dùng chung tên, ảnh, trạng thái, giá và chương trình sale.
-- Thêm đơn vị COMBO trong migration mới, giữ nguyên V1 đã chạy.
ALTER TABLE public.products DROP CONSTRAINT ck_products_unit_code;
ALTER TABLE public.products ADD CONSTRAINT ck_products_unit_code CHECK (
    unit_code IN (
        'KG', 'BOTTLE', 'PIECE', 'PACK', 'TRAY', 'BOX',
        'BAG', 'CAN', 'TIN', 'TUB', 'CASE', 'COMBO'
    )
);

CREATE TABLE public.combos (
    product_id UUID PRIMARY KEY REFERENCES public.products (id) ON DELETE RESTRICT,
    -- Chỉ nhập khi combo có số suất cụ thể, ví dụ 50 suất cơm; combo thịt có thể để NULL.
    serving_count INTEGER,

    CONSTRAINT ck_combos_serving_count CHECK (serving_count > 0)
);

CREATE TABLE public.combo_items (
    combo_id UUID NOT NULL REFERENCES public.combos (product_id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    -- Số lượng trong MỘT combo; đơn vị lấy từ products.unit_code của thành phần.
    quantity NUMERIC(12, 3) NOT NULL,

    PRIMARY KEY (combo_id, product_id),
    CONSTRAINT ck_combo_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_combo_items_not_self CHECK (combo_id <> product_id)
);

-- PK ghép đã có index lấy thành phần theo combo; index này tìm combo chứa một sản phẩm.
CREATE INDEX ix_combo_items_product_id ON public.combo_items (product_id);

ALTER TABLE public.combos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.combo_items ENABLE ROW LEVEL SECURITY;

-- Quy tắc cần triển khai trong service, không được thực thi chỉ bằng hai bảng này:
-- E-commerce tạo/cập nhật sản phẩm đại diện, giá và thành phần trong cùng transaction.
-- Sản phẩm đại diện dùng đơn vị COMBO, MOQ = 1, quantity_step = 1; không nhập tồn riêng.
-- Chỉ publish combo có thành phần; không cho combo lồng combo hoặc dùng sản phẩm ngừng bán.
-- Kiểm tra quantity thành phần theo MOQ/bước số lượng của sản phẩm tương ứng.
-- Giá lấy từ product_prices của combo; không cộng thêm sale của các thành phần.
-- Giữ/trừ/hoàn kho theo quantity thành phần nhân với số combo, cộng cả lượng mua lẻ cùng SKU.
-- Khi thiếu thành phần thì hiển thị hết hàng; giữ kho cần transaction để tránh bán vượt tồn.
-- Khi chốt Order phải lưu snapshot thành phần để sửa combo không làm thay đổi đơn đã mua.
-- Snapshot lưu ở order_combo_items bên dưới; logic giá/kho sẽ triển khai trong service.

-- Thành phần combo tại thời điểm mua; không phụ thuộc cấu hình combo_items về sau.
CREATE TABLE public.order_combo_items (
    order_item_id UUID NOT NULL REFERENCES public.order_items (id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    sku VARCHAR(60) NOT NULL,
    name_vi VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    -- Lượng thành phần trong MỘT combo lúc mua, không phải tổng lượng của dòng Order.
    quantity_per_combo NUMERIC(12, 3) NOT NULL,

    PRIMARY KEY (order_item_id, product_id),
    CONSTRAINT ck_order_combo_items_snapshot_nonblank CHECK (
        BTRIM(sku) <> '' AND BTRIM(name_vi) <> ''
        AND BTRIM(name_en) <> '' AND BTRIM(unit_code) <> ''
    ),
    CONSTRAINT ck_order_combo_items_quantity CHECK (quantity_per_combo > 0)
);

-- PK lấy thành phần của dòng combo; index ngược tìm các dòng chứa một sản phẩm.
CREATE INDEX ix_order_combo_items_product_id ON public.order_combo_items (product_id);
ALTER TABLE public.order_combo_items ENABLE ROW LEVEL SECURITY;

-- Service chỉ tạo snapshot cho dòng Order là combo, không cho thành phần là combo lồng.
-- Khóa/đọc cấu hình combo nhất quán khi tạo Order/items/snapshot và giữ tồn trong transaction.
-- Tổng lượng thành phần = order_items.quantity * quantity_per_combo.
-- Lượng của một package = package_items.quantity * quantity_per_combo.
-- Chỉ phân bổ/hủy nguyên số combo; giữ nguyên snapshot khi hủy để tra cứu và hoàn/giải phóng kho.
-- Giá/VAT tính ở dòng combo trong order_items, không tính thành phần thành dòng tiền lần nữa.
-- Không sửa/xóa snapshot sau khi Order được xác nhận; không JOIN combo_items để đóng đơn cũ.

-- Lịch sử tin nhắn theo phiên AI; chủ phiên và loại tác vụ lấy từ ai_sessions.
CREATE TABLE public.ai_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES public.ai_sessions (id) ON DELETE CASCADE,
    -- Thứ tự do Backend cấp trong từng phiên; không chỉ dựa vào created_at.
    sequence_no BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    -- Phiên bản ngữ cảnh khi nhận prompt hoặc tạo phản hồi, không phải phiên bản UI.
    context_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ux_ai_messages_session_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT ck_ai_messages_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_ai_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_messages_content CHECK (BTRIM(content) <> ''),
    CONSTRAINT ck_ai_messages_context_version CHECK (context_version > 0)
);

-- UNIQUE(session_id, sequence_no) đã có index lấy lịch sử, phân trang theo thứ tự trong phiên.
ALTER TABLE public.ai_messages ENABLE ROW LEVEL SECURITY;

-- Backend kiểm tra chủ phiên (cả Guest), quyền và hạn phiên trước khi đọc/ghi tin nhắn.
-- Khóa dòng ai_sessions khi cấp sequence_no; không dùng MAX + 1 ngoài transaction có khóa.
-- Cập nhật context_version/updated_at của session cùng transaction khi nhận yêu cầu mới.
-- Phản hồi giữ version của yêu cầu; chỉ áp tác vụ/UI khi version còn khớp session hiện tại.
-- Streaming chỉ lưu tin hoàn chỉnh; chưa triển khai API chat, model hay tự cập nhật UI.
-- Không lưu system prompt, token đăng nhập hoặc dữ liệu bí mật vào hội thoại.
-- Lịch sử là dữ liệu hội thoại, không được dùng thay system prompt hoặc quyền gọi API.
-- UI JSON, tool calls và memory dài hạn sẽ thiết kế riêng khi triển khai AI.
