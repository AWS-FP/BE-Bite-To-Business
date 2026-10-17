-- Hồ sơ ứng dụng gắn với tài khoản do Supabase Auth quản lý.
CREATE TABLE public.users (
    id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    role VARCHAR(30) NOT NULL,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_users_role CHECK (
        role IN (
            'ADMIN',
            'CUSTOMER',
            'ECOMMERCE_EXECUTIVE',
            'LOGISTICS_STAFF',
            'DELIVERY_STAFF'
        )
    ),
    CONSTRAINT ck_users_email_nonblank CHECK (BTRIM(email) <> '')
);

-- Email đăng nhập không phân biệt chữ hoa/chữ thường.
CREATE UNIQUE INDEX ux_users_email_ci ON public.users (LOWER(email));

-- Backend là nơi duy nhất được phép đọc/cập nhật hồ sơ người dùng.
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Thông tin đăng ký và hóa đơn riêng của Customer; staff không có dòng hồ sơ này.
CREATE TABLE public.user_profiles (
    user_id UUID PRIMARY KEY REFERENCES public.users (id) ON DELETE CASCADE,
    customer_type VARCHAR(50) NOT NULL,
    business_name VARCHAR(200) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    delivery_address TEXT NOT NULL,
    invoice_same_as_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    invoice_purchaser VARCHAR(200),
    invoice_tax_code VARCHAR(10),
    invoice_email VARCHAR(254),
    invoice_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_user_profiles_customer_type CHECK (
        customer_type IN (
            'HOUSEHOLD_INDIVIDUAL_BUSINESS',
            'BUSINESS_COOPERATIVE'
        )
    ),
    CONSTRAINT ck_user_profiles_tax_code CHECK (
        invoice_tax_code IS NULL OR invoice_tax_code ~ '^[0-9]{10}$'
    )
);

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;

-- Danh mục phân cấp; product sẽ gắn với danh mục con phù hợp.
CREATE TABLE public.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES public.categories (id) ON DELETE RESTRICT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name_vi VARCHAR(120) NOT NULL,
    name_en VARCHAR(120) NOT NULL,
    description_vi TEXT,
    description_en TEXT,
    image_url TEXT,
    cloudinary_public_id VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Hỗ trợ lấy danh mục theo cấp, lọc trạng thái và thứ tự hiển thị.
CREATE INDEX ix_categories_parent_active_sort_order
    ON public.categories (parent_id, is_active, sort_order);

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;

-- Thương hiệu có trang riêng, logo và mô tả dùng chung cho nhiều sản phẩm.
CREATE TABLE public.brands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description_vi TEXT,
    description_en TEXT,
    logo_url TEXT,
    cloudinary_public_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_brands_name_nonblank CHECK (BTRIM(name) <> '')
);

CREATE UNIQUE INDEX ux_brands_name_ci ON public.brands (LOWER(name));
ALTER TABLE public.brands ENABLE ROW LEVEL SECURITY;

-- Thông tin catalog; giá, tồn, khuyến mãi và đánh giá được quản lý riêng.
CREATE TABLE public.products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(60) NOT NULL UNIQUE,
    category_id UUID NOT NULL REFERENCES public.categories (id) ON DELETE RESTRICT,
    brand_id UUID REFERENCES public.brands (id) ON DELETE SET NULL,
    -- Tối đa một chương trình cho mỗi sản phẩm; FK thêm sau khi tạo promotions.
    promotion_id UUID,
    name_vi VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NOT NULL,
    description_vi TEXT,
    description_en TEXT,
    note TEXT,
    origin VARCHAR(150),
    package_spec_vi VARCHAR(200),
    package_spec_en VARCHAR(200),
    unit_code VARCHAR(20) NOT NULL,
    min_order_quantity NUMERIC(12, 3) NOT NULL,
    quantity_step NUMERIC(12, 3) NOT NULL,
    image_url TEXT,
    cloudinary_public_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_products_unit_code CHECK (
        unit_code IN (
            'KG', 'BOTTLE', 'PIECE', 'PACK', 'TRAY', 'BOX',
            'BAG', 'CAN', 'TIN', 'TUB', 'CASE'
        )
    ),
    CONSTRAINT ck_products_min_order_quantity CHECK (min_order_quantity > 0),
    CONSTRAINT ck_products_quantity_step CHECK (quantity_step > 0),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX ix_products_category_status ON public.products (category_id, status);
CREATE INDEX ix_products_promotion_id ON public.products (promotion_id)
    WHERE promotion_id IS NOT NULL;
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

-- Giá niêm yết và giá sale riêng từng sản phẩm, bằng VND trên đơn vị bán.
CREATE TABLE public.product_prices (
    product_id UUID PRIMARY KEY REFERENCES public.products (id) ON DELETE CASCADE,
    list_price NUMERIC(14, 0) NOT NULL,
    sale_price NUMERIC(14, 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_prices_list_price CHECK (list_price >= 0),
    CONSTRAINT ck_product_prices_sale_price CHECK (
        sale_price IS NULL OR (sale_price >= 0 AND sale_price < list_price)
    )
);

-- Khóa chính product_id đã có index để truy vấn và JOIN giá sản phẩm.
-- sale_price NULL nghĩa là không sale; index tìm ứng viên, không khẳng định sale có hiệu lực.
-- Backend chỉ áp sale khi promotion của sản phẩm PUBLISHED và còn thời gian hiệu lực.
-- Mức giảm suy từ list_price/sale_price; không lưu thêm discount_percent trùng ở products.
CREATE INDEX ix_product_prices_on_sale ON public.product_prices (product_id)
    WHERE sale_price IS NOT NULL;

ALTER TABLE public.product_prices ENABLE ROW LEVEL SECURITY;

-- Bậc giá theo số lượng; đơn giá tính bằng VND trên đơn vị bán của sản phẩm.
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

CREATE TABLE public.product_price_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE CASCADE,
    min_quantity NUMERIC(12, 3) NOT NULL,
    max_quantity NUMERIC(12, 3),
    unit_price NUMERIC(14, 0) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_price_tiers_min_quantity CHECK (min_quantity > 0),
    CONSTRAINT ck_product_price_tiers_quantity_range CHECK (
        max_quantity IS NULL OR max_quantity >= min_quantity
    ),
    CONSTRAINT ck_product_price_tiers_unit_price CHECK (unit_price >= 0),
    -- Hai đầu đều tính vào bậc giá; max_quantity NULL nghĩa là không giới hạn.
    CONSTRAINT ex_product_price_tiers_no_overlap EXCLUDE USING gist (
        product_id WITH =,
        numrange(min_quantity, max_quantity, '[]') WITH &&
    )
);

-- Hỗ trợ lấy các bậc giá của sản phẩm theo số lượng tăng dần.
CREATE UNIQUE INDEX ux_product_price_tiers_product_min_quantity
    ON public.product_price_tiers (product_id, min_quantity);

ALTER TABLE public.product_price_tiers ENABLE ROW LEVEL SECURITY;

-- Một kho: mỗi sản phẩm có một dòng tồn, tính theo đơn vị bán của sản phẩm.
CREATE TABLE public.inventory (
    product_id UUID PRIMARY KEY REFERENCES public.products (id) ON DELETE RESTRICT,
    on_hand_quantity NUMERIC(12, 3) NOT NULL DEFAULT 0,
    reserved_quantity NUMERIC(12, 3) NOT NULL DEFAULT 0,
    available_quantity NUMERIC(12, 3) GENERATED ALWAYS AS (
        on_hand_quantity - reserved_quantity
    ) STORED,
    updated_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_inventory_on_hand_quantity CHECK (on_hand_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_quantity CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_within_stock CHECK (
        reserved_quantity <= on_hand_quantity
    )
);

-- Khóa chính product_id đã có index để truy vấn và JOIN theo sản phẩm.
-- Backend giữ/giải phóng hàng; Staff không tự nhập reserved/available.
ALTER TABLE public.inventory ENABLE ROW LEVEL SECURITY;

-- E-commerce Executive tạo mã; backend kiểm tra role và điều kiện áp dụng.
CREATE TABLE public.coupons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(8) NOT NULL UNIQUE,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(14, 2) NOT NULL,
    min_order_amount NUMERIC(14, 0) NOT NULL DEFAULT 0,
    max_discount_amount NUMERIC(14, 0),
    starts_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at TIMESTAMPTZ NOT NULL,
    usage_limit INTEGER,
    usage_limit_per_user INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_coupons_code CHECK (
        CHAR_LENGTH(code) = 8 AND code ~ '^[A-Z0-9]{8}$'
    ),
    CONSTRAINT ck_coupons_discount_type CHECK (
        discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')
    ),
    CONSTRAINT ck_coupons_discount_value CHECK (
        discount_value > 0
        AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)
    ),
    CONSTRAINT ck_coupons_min_order_amount CHECK (min_order_amount >= 0),
    CONSTRAINT ck_coupons_max_discount_amount CHECK (
        max_discount_amount IS NULL OR max_discount_amount > 0
    ),
    CONSTRAINT ck_coupons_period CHECK (ends_at > starts_at),
    -- Giới hạn NULL nghĩa là không giới hạn; lượt giữ/dùng lấy từ orders.
    CONSTRAINT ck_coupons_usage_limit CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT ck_coupons_usage_limit_per_user CHECK (
        usage_limit_per_user IS NULL OR usage_limit_per_user > 0
    )
);

-- UNIQUE(code) đã tạo index tra mã; index này hỗ trợ lọc mã đang bật theo thời gian.
CREATE INDEX ix_coupons_active_period ON public.coupons (starts_at, ends_at)
    WHERE is_active = TRUE;

ALTER TABLE public.coupons ENABLE ROW LEVEL SECURITY;

-- Customer đề xuất sản phẩm chưa có; Logistics Staff tiếp nhận yêu cầu.
CREATE TABLE public.product_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    product_name VARCHAR(200) NOT NULL,
    reference_url TEXT,
    contact_email VARCHAR(254),
    notify_by_email BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    customer_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_requests_product_name CHECK (BTRIM(product_name) <> ''),
    CONSTRAINT ck_product_requests_reference_url CHECK (
        reference_url IS NULL OR BTRIM(reference_url) <> ''
    ),
    CONSTRAINT ck_product_requests_contact_email CHECK (
        contact_email IS NULL
        OR contact_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    ),
    CONSTRAINT ck_product_requests_status CHECK (
        status IN ('PENDING', 'REVIEWING', 'ADDED', 'REJECTED')
    )
);

-- Lịch sử của Customer và danh sách xử lý của Logistics Staff.
CREATE INDEX ix_product_requests_customer_created_at
    ON public.product_requests (customer_id, created_at DESC);
CREATE INDEX ix_product_requests_status_created_at
    ON public.product_requests (status, created_at DESC);

-- Backend kiểm tra role/chủ sở hữu; phản hồi gửi tới email tài khoản Customer.
-- Gửi yêu cầu không tự tạo sản phẩm; phản hồi của Staff nằm ở bảng riêng.
ALTER TABLE public.product_requests ENABLE ROW LEVEL SECURITY;

-- Logistics Staff phản hồi; lưu thông tin sản phẩm/email theo thời điểm gửi.
CREATE TABLE public.product_request_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES public.product_requests (id) ON DELETE CASCADE,
    responded_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    response_note TEXT NOT NULL,
    added_product_id UUID REFERENCES public.products (id) ON DELETE SET NULL,
    product_name VARCHAR(200),
    product_added_at TIMESTAMPTZ,
    selling_price NUMERIC(14, 0),
    available_quantity NUMERIC(12, 3),
    unit_code VARCHAR(20),
    recipient_email VARCHAR(254) NOT NULL,
    notified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_request_responses_note CHECK (BTRIM(response_note) <> ''),
    CONSTRAINT ck_product_request_responses_recipient_email CHECK (
        recipient_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    ),
    CONSTRAINT ck_product_request_responses_selling_price CHECK (selling_price >= 0),
    CONSTRAINT ck_product_request_responses_available_quantity CHECK (available_quantity >= 0),
    -- Phản hồi có sản phẩm phải lưu đủ thông tin để dựng form/email.
    CONSTRAINT ck_product_request_responses_product_details CHECK (
        added_product_id IS NULL OR (
            product_name IS NOT NULL AND BTRIM(product_name) <> ''
            AND product_added_at IS NOT NULL AND selling_price IS NOT NULL
            AND available_quantity IS NOT NULL
            AND unit_code IS NOT NULL AND BTRIM(unit_code) <> ''
        )
    )
);

CREATE INDEX ix_product_request_responses_request_created_at
    ON public.product_request_responses (request_id, created_at DESC);
-- Tra phản hồi có sản phẩm liên quan và chưa gửi email khi sản phẩm có hàng.
CREATE INDEX ix_product_request_responses_pending_notification
    ON public.product_request_responses (added_product_id)
    WHERE added_product_id IS NOT NULL AND notified_at IS NULL;

-- Backend kiểm tra Logistics Staff và lấy recipient_email từ Customer của request.
-- Form sản phẩm lấy dữ liệu catalog/giá/tồn; lưu snapshot và thời điểm gửi mail.
ALTER TABLE public.product_request_responses ENABLE ROW LEVEL SECURITY;

-- Quote là yêu cầu mua, chưa phải Order và chưa giữ tồn kho.
CREATE TABLE public.quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    -- Tối đa một coupon chọn/nhập; lưu nháp chưa tính là sử dụng.
    coupon_id UUID REFERENCES public.coupons (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    revision INTEGER NOT NULL DEFAULT 1,
    -- Cho phép để trống ở bản nháp; backend kiểm tra đủ thông tin khi gửi duyệt.
    contact_name VARCHAR(200),
    contact_phone VARCHAR(15),
    contact_email VARCHAR(254),
    company_name VARCHAR(200),
    note TEXT,
    customer_po_reference VARCHAR(100),
    -- Tổng theo giá niêm yết; backend tính các khoản giảm từ dòng hàng/ưu đãi.
    items_subtotal NUMERIC(16, 0) NOT NULL DEFAULT 0,
    volume_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    sale_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    coupon_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    shipping_fee NUMERIC(16, 0) NOT NULL DEFAULT 0,
    total_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        items_subtotal - volume_discount_amount - sale_discount_amount
        - coupon_discount_amount + vat_amount + shipping_fee
    ) STORED,
    reviewed_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    reviewed_revision INTEGER,
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    submitted_at TIMESTAMPTZ,
    -- Backend đặt hạn theo cấu hình; không mặc định cứng 24 giờ trong schema.
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_quotes_number_nonblank CHECK (BTRIM(quote_number) <> ''),
    CONSTRAINT ck_quotes_status CHECK (
        status IN (
            'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
            'ACCEPTED', 'EXPIRED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_quotes_revision CHECK (revision > 0),
    CONSTRAINT ck_quotes_reviewed_revision CHECK (
        reviewed_revision IS NULL
        OR (reviewed_revision > 0 AND reviewed_revision <= revision)
    ),
    CONSTRAINT ck_quotes_reviewed_state CHECK (
        status NOT IN ('APPROVED', 'REJECTED', 'ACCEPTED')
        OR (
            reviewed_revision IS NOT NULL AND reviewed_revision = revision
            AND reviewed_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_quotes_amounts_nonnegative CHECK (
        items_subtotal >= 0 AND volume_discount_amount >= 0
        AND sale_discount_amount >= 0 AND coupon_discount_amount >= 0
        AND vat_amount >= 0 AND shipping_fee >= 0
    ),
    CONSTRAINT ck_quotes_discounts_within_subtotal CHECK (
        volume_discount_amount + sale_discount_amount + coupon_discount_amount
        <= items_subtotal
    ),
    CONSTRAINT ck_quotes_coupon_discount_link CHECK (
        coupon_id IS NOT NULL OR coupon_discount_amount = 0
    ),
    CONSTRAINT ck_quotes_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
);

-- Mã Quote đã có unique index; các index dưới phục vụ lịch sử và hàng chờ duyệt.
CREATE INDEX ix_quotes_customer_created_at ON public.quotes (customer_id, created_at DESC);
CREATE INDEX ix_quotes_status_created_at ON public.quotes (status, created_at DESC);
CREATE INDEX ix_quotes_coupon_id ON public.quotes (coupon_id) WHERE coupon_id IS NOT NULL;

-- Backend kiểm tra Customer sở hữu Quote, quyền E-commerce và chuyển trạng thái.
-- Sửa nội dung phải tăng revision/gửi duyệt lại; nhận Quote không tự tạo Order.
-- Đổi/bỏ coupon phải tính lại tổng/phân bổ giảm vào dòng và tăng revision nếu cần duyệt lại.
-- Backend kiểm tra mã hiện hành, điều kiện, ngưỡng và giới hạn; Quote không giữ lượt coupon.
ALTER TABLE public.quotes ENABLE ROW LEVEL SECURITY;

-- Mỗi sản phẩm có một dòng trong Quote; giá/tên/đơn vị được lưu theo lần tính giá.
CREATE TABLE public.quote_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id UUID NOT NULL REFERENCES public.quotes (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    price_tier_id UUID REFERENCES public.product_price_tiers (id) ON DELETE SET NULL,
    sku VARCHAR(60) NOT NULL,
    name_vi VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    quantity NUMERIC(12, 3) NOT NULL,
    list_unit_price NUMERIC(14, 0) NOT NULL,
    volume_unit_price NUMERIC(14, 0) NOT NULL,
    -- Giá sau sale và volume; không có sale thì bằng volume_unit_price.
    sale_unit_price NUMERIC(14, 0) NOT NULL,
    coupon_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    -- Backend lấy tỷ lệ VAT demo từ cấu hình; không hard-code một mức thuế.
    vat_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    customer_note TEXT,
    line_subtotal NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * list_unit_price, 0)
    ) STORED,
    volume_discount_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * list_unit_price, 0) - ROUND(quantity * volume_unit_price, 0)
    ) STORED,
    sale_discount_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * volume_unit_price, 0) - ROUND(quantity * sale_unit_price, 0)
    ) STORED,
    vat_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND((ROUND(quantity * sale_unit_price, 0) - coupon_discount_amount) * vat_rate / 100, 0)
    ) STORED,
    line_total NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * sale_unit_price, 0) - coupon_discount_amount
        + ROUND((ROUND(quantity * sale_unit_price, 0) - coupon_discount_amount) * vat_rate / 100, 0)
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Dùng cho foreign key kiểm tra phân bổ thuộc đúng Quote.
    CONSTRAINT ux_quote_items_id_quote UNIQUE (id, quote_id),
    CONSTRAINT ux_quote_items_quote_product UNIQUE (quote_id, product_id),
    CONSTRAINT ck_quote_items_snapshot_nonblank CHECK (
        BTRIM(sku) <> '' AND BTRIM(name_vi) <> ''
        AND BTRIM(name_en) <> '' AND BTRIM(unit_code) <> ''
    ),
    CONSTRAINT ck_quote_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_quote_items_prices CHECK (
        list_unit_price >= 0
        AND volume_unit_price >= 0 AND volume_unit_price <= list_unit_price
        AND sale_unit_price >= 0 AND sale_unit_price <= volume_unit_price
    ),
    CONSTRAINT ck_quote_items_coupon_discount CHECK (
        coupon_discount_amount >= 0
        AND coupon_discount_amount <= ROUND(quantity * sale_unit_price, 0)
    ),
    CONSTRAINT ck_quote_items_vat_rate CHECK (vat_rate >= 0 AND vat_rate <= 100)
);

-- UNIQUE(quote_id, product_id) đã có index lấy các dòng của Quote.
CREATE INDEX ix_quote_items_product_id ON public.quote_items (product_id);

-- Backend kiểm tra quyền, MOQ/bước lượng và tier thuộc đúng sản phẩm.
-- Sửa dòng hàng phải tăng revision và cập nhật tổng Quote trong cùng transaction.
ALTER TABLE public.quote_items ENABLE ROW LEVEL SECURITY;

-- Order được tạo từ Quote đã duyệt sau khi Customer xác nhận và giữ đủ hàng.
CREATE TABLE public.orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    quote_id UUID NOT NULL UNIQUE REFERENCES public.quotes (id) ON DELETE RESTRICT,
    quote_revision INTEGER NOT NULL,
    customer_id UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    -- Một coupon/Order; snapshot không đổi khi E-commerce sửa mã/quy tắc hiện hành.
    coupon_id UUID REFERENCES public.coupons (id) ON DELETE RESTRICT,
    coupon_code VARCHAR(8),
    coupon_discount_type VARCHAR(20),
    coupon_discount_value NUMERIC(14, 2),
    coupon_redeemed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    -- Snapshot thông tin liên lạc tại thời điểm xác nhận mua.
    contact_name VARCHAR(200) NOT NULL,
    contact_phone VARCHAR(15) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    company_name VARCHAR(200),
    note TEXT,
    customer_po_reference VARCHAR(100),
    -- Snapshot hóa đơn từ thông tin Customer đã chọn, không đọc lại hồ sơ hiện tại.
    invoice_purchaser VARCHAR(200),
    invoice_tax_code VARCHAR(10),
    invoice_email VARCHAR(254),
    invoice_address TEXT,
    -- Snapshot tổng tiền; backend kiểm tra/tính lại trước khi xác nhận.
    items_subtotal NUMERIC(16, 0) NOT NULL DEFAULT 0,
    volume_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    sale_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    coupon_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    shipping_fee NUMERIC(16, 0) NOT NULL DEFAULT 0,
    total_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        items_subtotal - volume_discount_amount - sale_discount_amount
        - coupon_discount_amount + vat_amount + shipping_fee
    ) STORED,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processing_started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    cancellation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_orders_number_nonblank CHECK (BTRIM(order_number) <> ''),
    CONSTRAINT ck_orders_quote_revision CHECK (quote_revision > 0),
    CONSTRAINT ck_orders_status CHECK (
        status IN ('CONFIRMED', 'PROCESSING', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_orders_contact_nonblank CHECK (
        BTRIM(contact_name) <> '' AND BTRIM(contact_phone) <> ''
    ),
    CONSTRAINT ck_orders_contact_email CHECK (
        contact_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    ),
    CONSTRAINT ck_orders_invoice_tax_code CHECK (
        invoice_tax_code IS NULL OR invoice_tax_code ~ '^[0-9]{10}$'
    ),
    CONSTRAINT ck_orders_invoice_email CHECK (
        invoice_email IS NULL
        OR invoice_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    ),
    CONSTRAINT ck_orders_amounts_nonnegative CHECK (
        items_subtotal >= 0 AND volume_discount_amount >= 0
        AND sale_discount_amount >= 0 AND coupon_discount_amount >= 0
        AND vat_amount >= 0 AND shipping_fee >= 0
    ),
    CONSTRAINT ck_orders_discounts_within_subtotal CHECK (
        volume_discount_amount + sale_discount_amount + coupon_discount_amount
        <= items_subtotal
    ),
    CONSTRAINT ck_orders_coupon_snapshot CHECK (
        (coupon_id IS NULL AND coupon_code IS NULL AND coupon_discount_type IS NULL
            AND coupon_discount_value IS NULL AND coupon_redeemed_at IS NULL
            AND coupon_discount_amount = 0)
        OR (
            coupon_id IS NOT NULL AND coupon_code IS NOT NULL
            AND CHAR_LENGTH(coupon_code) = 8 AND coupon_code ~ '^[A-Z0-9]{8}$'
            AND coupon_discount_type IS NOT NULL
            AND coupon_discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')
            AND coupon_discount_value IS NOT NULL AND coupon_discount_value > 0
            AND (coupon_discount_type <> 'PERCENTAGE' OR coupon_discount_value <= 100)
        )
    ),
    CONSTRAINT ck_orders_coupon_redeemed_at CHECK (
        coupon_redeemed_at IS NULL OR coupon_redeemed_at >= confirmed_at
    ),
    CONSTRAINT ck_orders_processing_state CHECK (
        status <> 'PROCESSING' OR processing_started_at IS NOT NULL
    ),
    CONSTRAINT ck_orders_final_state CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL
            AND cancelled_by IS NULL AND cancellation_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('CONFIRMED', 'PROCESSING') AND completed_at IS NULL
            AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancellation_reason IS NULL)
    ),
    CONSTRAINT ck_orders_timestamps CHECK (
        (processing_started_at IS NULL OR processing_started_at >= confirmed_at)
        AND (completed_at IS NULL OR completed_at >= confirmed_at)
        AND (cancelled_at IS NULL OR cancelled_at >= confirmed_at)
    )
);

-- Mã Order và quote_id đã có unique index; mỗi Quote chỉ tạo một Order.
CREATE INDEX ix_orders_customer_created_at ON public.orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_status_created_at ON public.orders (status, created_at DESC);
-- Tra Order dùng/giữ coupon, đếm theo mã và Customer; hỗ trợ kiểm tra FK khi xóa coupon.
CREATE INDEX ix_orders_coupon_customer_redeemed_at
    ON public.orders (coupon_id, customer_id, coupon_redeemed_at) WHERE coupon_id IS NOT NULL;

-- Backend kiểm tra chủ sở hữu/revision Quote, quyền Customer và đủ mọi SKU.
-- Tạo Order, giữ tồn và chuyển Quote ACCEPTED trong cùng transaction.
-- Hủy chỉ trước khi chuyến bắt đầu; backend giải phóng tồn cùng thao tác hủy.
-- Backend xác nhận coupon hợp lệ và copy mã/loại/giá trị vào Order cùng snapshot tiền.
-- Lượt dùng ghi trực tiếp ở coupon_redeemed_at; không cần quote_coupons/coupon_redemptions.
-- Đề xuất quota: giữ lượt ở Order chưa thanh toán/chưa hủy; ghi đã dùng sau SUCCESS đầu tiên.
-- Khóa coupon khi kiểm tra quota/tạo Order; hủy trước thu tiền giải phóng lượt giữ.
-- IPN xác minh, ghi payment và coupon_redeemed_at trong transaction; chỉ ghi timestamp một lần.
-- Hủy/hoàn sau thu tiền giữ lịch sử dùng; chính sách trả lượt coupon cần chốt ở luồng hoàn tiền.
-- CHECK không xác minh payment/quota; các API giữ/ghi nhận lượt chưa triển khai.
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;

-- Snapshot dòng hàng khi xác nhận Order; không lấy lại giá/tên từ catalog.
CREATE TABLE public.order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
    quote_item_id UUID NOT NULL UNIQUE REFERENCES public.quote_items (id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    price_tier_id UUID REFERENCES public.product_price_tiers (id) ON DELETE SET NULL,
    sku VARCHAR(60) NOT NULL,
    name_vi VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NOT NULL,
    unit_code VARCHAR(20) NOT NULL,
    quantity NUMERIC(12, 3) NOT NULL,
    min_order_quantity NUMERIC(12, 3) NOT NULL,
    quantity_step NUMERIC(12, 3) NOT NULL,
    -- Giữ bậc đã áp dụng, kể cả khi tier hiện tại được sửa/xóa.
    tier_min_quantity NUMERIC(12, 3),
    tier_max_quantity NUMERIC(12, 3),
    list_unit_price NUMERIC(14, 0) NOT NULL,
    volume_unit_price NUMERIC(14, 0) NOT NULL,
    sale_unit_price NUMERIC(14, 0) NOT NULL,
    coupon_discount_amount NUMERIC(16, 0) NOT NULL DEFAULT 0,
    vat_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    customer_note TEXT,
    line_subtotal NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * list_unit_price, 0)
    ) STORED,
    volume_discount_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * list_unit_price, 0) - ROUND(quantity * volume_unit_price, 0)
    ) STORED,
    sale_discount_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * volume_unit_price, 0) - ROUND(quantity * sale_unit_price, 0)
    ) STORED,
    vat_amount NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND((ROUND(quantity * sale_unit_price, 0) - coupon_discount_amount) * vat_rate / 100, 0)
    ) STORED,
    line_total NUMERIC(16, 0) GENERATED ALWAYS AS (
        ROUND(quantity * sale_unit_price, 0) - coupon_discount_amount
        + ROUND((ROUND(quantity * sale_unit_price, 0) - coupon_discount_amount) * vat_rate / 100, 0)
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ux_order_items_order_product UNIQUE (order_id, product_id),
    -- Khóa đối chiếu cho phân bổ package thuộc cùng Order.
    CONSTRAINT ux_order_items_id_order UNIQUE (id, order_id),
    CONSTRAINT ck_order_items_snapshot_nonblank CHECK (
        BTRIM(sku) <> '' AND BTRIM(name_vi) <> ''
        AND BTRIM(name_en) <> '' AND BTRIM(unit_code) <> ''
    ),
    CONSTRAINT ck_order_items_quantity CHECK (
        min_order_quantity > 0 AND quantity_step > 0
        AND quantity > 0 AND quantity >= min_order_quantity
    ),
    CONSTRAINT ck_order_items_tier_range CHECK (
        (tier_min_quantity IS NULL AND tier_max_quantity IS NULL)
        OR (
            tier_min_quantity IS NOT NULL AND tier_min_quantity > 0
            AND quantity >= tier_min_quantity
            AND (tier_max_quantity IS NULL
                OR (tier_max_quantity >= tier_min_quantity AND quantity <= tier_max_quantity))
        )
    ),
    CONSTRAINT ck_order_items_tier_snapshot CHECK (
        price_tier_id IS NULL OR tier_min_quantity IS NOT NULL
    ),
    CONSTRAINT ck_order_items_prices CHECK (
        list_unit_price >= 0
        AND volume_unit_price >= 0 AND volume_unit_price <= list_unit_price
        AND sale_unit_price >= 0 AND sale_unit_price <= volume_unit_price
    ),
    CONSTRAINT ck_order_items_coupon_discount CHECK (
        coupon_discount_amount >= 0
        AND coupon_discount_amount <= ROUND(quantity * sale_unit_price, 0)
    ),
    CONSTRAINT ck_order_items_vat_rate CHECK (vat_rate >= 0 AND vat_rate <= 100)
);

-- UNIQUE(order_id, product_id) đã có index lấy dòng hàng; quote_item_id cũng có unique index.
CREATE INDEX ix_order_items_product_id ON public.order_items (product_id);

-- Backend lấy dòng từ đúng Quote của Order, kiểm tra bước lượng và lưu snapshot.
-- Tạo Order/items/giữ tồn trong cùng transaction; không sửa dòng sau xác nhận.
-- Hủy Order giữ nguyên dòng hàng để tra cứu, chỉ giải phóng lượng đã giữ.
ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;

-- Mỗi Customer chỉ lưu một lần yêu thích cho cùng sản phẩm.
CREATE TABLE public.product_favorites (
    customer_id UUID NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (customer_id, product_id)
);

-- Lấy danh sách yêu thích mới nhất của Customer và tra cứu theo sản phẩm.
CREATE INDEX ix_product_favorites_customer_created_at
    ON public.product_favorites (customer_id, created_at DESC);
CREATE INDEX ix_product_favorites_product_id
    ON public.product_favorites (product_id);

-- Backend chỉ cho Customer quản lý danh sách của chính mình.
ALTER TABLE public.product_favorites ENABLE ROW LEVEL SECURITY;

-- Mỗi dòng là một lần thử thanh toán; thử lại tạo dòng mới, giữ lịch sử cũ.
CREATE TABLE public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_number VARCHAR(50) NOT NULL UNIQUE,
    order_id UUID NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
    payment_method VARCHAR(20) NOT NULL,
    -- Cổng xử lý giao dịch; Visa/Mastercard là phương thức, không phải tên cổng.
    provider VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(100),
    amount NUMERIC(16, 0) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_result_code VARCHAR(50),
    provider_message TEXT,
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_payments_number_nonblank CHECK (BTRIM(payment_number) <> ''),
    CONSTRAINT ck_payments_method CHECK (
        payment_method IN ('MOMO', 'VISA', 'MASTERCARD')
    ),
    CONSTRAINT ck_payments_provider_nonblank CHECK (BTRIM(provider) <> ''),
    CONSTRAINT ck_payments_transaction_nonblank CHECK (
        provider_transaction_id IS NULL OR BTRIM(provider_transaction_id) <> ''
    ),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payments_status CHECK (
        status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_payments_paid_state CHECK (
        (status = 'SUCCESS' AND paid_at IS NOT NULL)
        OR (status <> 'SUCCESS' AND paid_at IS NULL)
    ),
    CONSTRAINT ck_payments_timestamps CHECK (
        (expires_at IS NULL OR expires_at > created_at)
        AND (paid_at IS NULL OR paid_at >= created_at)
    )
);

CREATE INDEX ix_payments_order_created_at ON public.payments (order_id, created_at DESC);
CREATE INDEX ix_payments_status_created_at ON public.payments (status, created_at DESC);

-- Không ghi trùng giao dịch từ cổng; mỗi Order chỉ có một lần thử đang chờ.
CREATE UNIQUE INDEX ux_payments_provider_transaction
    ON public.payments (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX ux_payments_order_pending
    ON public.payments (order_id) WHERE status = 'PENDING';

-- Backend lấy amount từ Order, kiểm tra chủ sở hữu, đơn chưa hủy/chưa thanh toán.
-- Chỉ cập nhật kết quả từ cổng đã xác minh; xử lý callback lặp trong transaction.
-- Thanh toán thành công không tự chuyển Order sang COMPLETED (đã giao hàng).
-- Không tạo giao dịch thu tiền cho Order tổng 0; không lưu số thẻ/CVV ở đây.
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;

-- Snapshot nhóm nhận hàng Customer đã chọn trong Quote; mỗi package một địa chỉ.
CREATE TABLE public.order_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_number VARCHAR(50) NOT NULL UNIQUE,
    order_id UUID NOT NULL REFERENCES public.orders (id) ON DELETE RESTRICT,
    package_name VARCHAR(200) NOT NULL,
    delivery_date DATE NOT NULL,
    delivery_address TEXT NOT NULL,
    created_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ux_order_packages_id_order UNIQUE (id, order_id),
    CONSTRAINT ck_order_packages_names_nonblank CHECK (
        BTRIM(package_number) <> '' AND BTRIM(package_name) <> ''
        AND BTRIM(delivery_address) <> ''
    ),
    CONSTRAINT ck_order_packages_cancelled_at CHECK (
        cancelled_at IS NULL OR cancelled_at >= created_at
    )
);

CREATE INDEX ix_order_packages_order_created_at
    ON public.order_packages (order_id, created_at DESC);
CREATE INDEX ix_order_packages_active_delivery_date
    ON public.order_packages (delivery_date, order_id) WHERE cancelled_at IS NULL;

-- delivery_date là ngày giao dự kiến do Customer chọn, không phải thời điểm giao xong.
-- Backend copy nhóm hàng/địa chỉ/ngày giao từ Quote khi tạo Order.
-- Mọi số lượng trong Quote phải được phân bổ đủ vào package trước bước tiếp theo.
-- Liên kết sản phẩm/số lượng nằm trong package_items, không nhét vào bảng này.
-- Tiến độ và assignment nằm ở Order/chuyến giao; bảng này không có status/note.
-- Hủy riêng package: ghi cancelled_at, giữ lịch sử; các package khác tiếp tục.
-- Trước chuyến chỉ giải phóng lượng giữ thuộc phần bị hủy; đưa nhu cầu về Quote DRAFT.
-- Quote DRAFT phải phân bổ lại đủ lượng hoặc xóa dòng trước bước thanh toán.
-- Hàng đã xuất giao không tự hoàn kho khi hủy; luồng nhận lại hàng cần thiết kế riêng.
ALTER TABLE public.order_packages ENABLE ROW LEVEL SECURITY;

-- Snapshot phân bổ dòng Order vào từng package; không lưu lại tên/giá/đơn vị/note.
CREATE TABLE public.package_items (
    order_package_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    -- Đối chiếu hai FK ghép để package và dòng hàng luôn thuộc cùng Order.
    order_id UUID NOT NULL,
    quantity NUMERIC(12, 3) NOT NULL,

    CONSTRAINT pk_package_items PRIMARY KEY (order_package_id, order_item_id),
    CONSTRAINT fk_package_items_package FOREIGN KEY (order_package_id, order_id)
        REFERENCES public.order_packages (id, order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_package_items_item FOREIGN KEY (order_item_id, order_id)
        REFERENCES public.order_items (id, order_id) ON DELETE RESTRICT,
    CONSTRAINT ck_package_items_quantity CHECK (quantity > 0)
);

-- PK lấy hàng trong package; index lấy các package chứa một dòng hàng.
CREATE INDEX ix_package_items_order_item_id ON public.package_items (order_item_id);
CREATE INDEX ix_package_items_order_id ON public.package_items (order_id);

-- Backend copy phân bổ Quote còn hiệu lực cùng Order/items/packages trong transaction.
-- Khóa Order/dòng hàng; kiểm tra tổng lượng phân bổ bằng quantity của order_items,
-- đúng bước lượng, quyền sở hữu và trạng thái package. CHECK không kiểm tra tổng nhiều dòng.
-- Hủy package giữ nguyên phân bổ để tính lượng giải phóng/hoàn và tra cứu lịch sử.
-- Không tự hoàn kho hay sửa Order snapshot khi Customer phân bổ lại Quote mới.
ALTER TABLE public.package_items ENABLE ROW LEVEL SECURITY;

-- Thông báo trong website cho Customer/Admin/Staff; mỗi dòng có một người nhận.
CREATE TABLE public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    notification_type VARCHAR(30) NOT NULL,
    title_vi VARCHAR(200) NOT NULL,
    title_en VARCHAR(200) NOT NULL,
    content_vi TEXT NOT NULL,
    content_en TEXT NOT NULL,
    -- Route trong website để mở tài nguyên liên quan; backend kiểm tra quyền khi mở.
    action_url TEXT,
    read_at TIMESTAMPTZ,
    is_read BOOLEAN GENERATED ALWAYS AS (read_at IS NOT NULL) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_notifications_type CHECK (
        notification_type IN (
            'ACCOUNT', 'QUOTE', 'ORDER', 'PAYMENT', 'DELIVERY',
            'PRODUCT_REQUEST', 'PROMOTION', 'SYSTEM'
        )
    ),
    CONSTRAINT ck_notifications_content_nonblank CHECK (
        BTRIM(title_vi) <> '' AND BTRIM(title_en) <> ''
        AND BTRIM(content_vi) <> '' AND BTRIM(content_en) <> ''
    ),
    CONSTRAINT ck_notifications_action_url CHECK (
        action_url IS NULL OR (
            (action_url = '/' OR action_url ~ '^/[^/]')
            AND action_url !~ '[[:space:]]'
            AND POSITION(CHR(92) IN action_url) = 0
        )
    ),
    CONSTRAINT ck_notifications_read_at CHECK (
        read_at IS NULL OR read_at >= created_at
    )
);

CREATE INDEX ix_notifications_recipient_created_at
    ON public.notifications (recipient_id, created_at DESC);
-- Lấy danh sách/đếm thông báo chưa đọc bằng read_at IS NULL.
CREATE INDEX ix_notifications_recipient_unread_created_at
    ON public.notifications (recipient_id, created_at DESC) WHERE read_at IS NULL;

-- Backend tạo thông báo; người nhận chỉ xem/đánh dấu đã đọc thông báo của mình.
-- Cập nhật read_at để đánh dấu đã đọc; is_read tự tính, không ghi trực tiếp.
-- Đây là thông báo trong website; hàng đợi gửi Gmail sẽ dùng bảng riêng.
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- Customer đánh giá theo sản phẩm; không giới hạn số review và không duyệt trước.
CREATE TABLE public.product_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    -- NUMERIC không giới hạn scale để kiểm tra đúng bước 0.5 trước khi làm tròn.
    rating NUMERIC NOT NULL,
    content TEXT,
    -- Xóa mềm để giữ người thực hiện/lý do; review bị xóa không tính vào rating.
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    deletion_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_reviews_rating CHECK (
        rating >= 0.5 AND rating <= 5 AND MOD(rating, 0.5) = 0
    ),
    CONSTRAINT ck_product_reviews_content CHECK (
        content IS NULL OR BTRIM(content) <> ''
    ),
    CONSTRAINT ck_product_reviews_deletion_state CHECK (
        deleted_at IS NOT NULL OR (deleted_by IS NULL AND deletion_reason IS NULL)
    ),
    CONSTRAINT ck_product_reviews_deletion_reason CHECK (
        deletion_reason IS NULL OR BTRIM(deletion_reason) <> ''
    ),
    CONSTRAINT ck_product_reviews_deleted_at CHECK (
        deleted_at IS NULL OR deleted_at >= created_at
    )
);

CREATE INDEX ix_product_reviews_product_created_at
    ON public.product_reviews (product_id, created_at DESC);
CREATE INDEX ix_product_reviews_customer_created_at
    ON public.product_reviews (customer_id, created_at DESC);

-- Backend chỉ cho Customer viết/sửa review của mình; Admin được xóa review.
-- Review công khai và AVG(rating)/COUNT chỉ lấy deleted_at IS NULL.
-- Giữ nguyên ngôn ngữ gốc; không yêu cầu Order đã mua và không UNIQUE(customer, product).
-- API công khai không trả email/phone hay metadata xóa; RLS giữ truy cập qua backend.
ALTER TABLE public.product_reviews ENABLE ROW LEVEL SECURITY;

-- Một review có thể có nhiều ảnh Cloudinary; không nhân đôi dòng review/rating.
CREATE TABLE public.review_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES public.product_reviews (id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    cloudinary_public_id VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_review_images_url_nonblank CHECK (BTRIM(image_url) <> ''),
    CONSTRAINT ck_review_images_public_id CHECK (
        cloudinary_public_id IS NULL OR BTRIM(cloudinary_public_id) <> ''
    ),
    CONSTRAINT ck_review_images_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX ix_review_images_review_sort_order
    ON public.review_images (review_id, sort_order, created_at);

-- Backend kiểm tra chủ sở hữu khi thêm/xóa ảnh và chỉ trả ảnh của review công khai.
-- Xóa mềm review giữ ảnh để tra cứu nhưng không công khai; xóa Cloudinary là thao tác riêng.
ALTER TABLE public.review_images ENABLE ROW LEVEL SECURITY;

-- Customer tạo nhóm nhận hàng khi lập Quote; chưa giữ tồn và chưa có Order.
CREATE TABLE public.quote_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id UUID NOT NULL REFERENCES public.quotes (id) ON DELETE CASCADE,
    package_name VARCHAR(200) NOT NULL,
    delivery_date DATE NOT NULL,
    delivery_address TEXT NOT NULL,
    created_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Dùng cho foreign key kiểm tra package và dòng hàng thuộc cùng Quote.
    CONSTRAINT ux_quote_packages_id_quote UNIQUE (id, quote_id),
    CONSTRAINT ck_quote_packages_details_nonblank CHECK (
        BTRIM(package_name) <> '' AND BTRIM(delivery_address) <> ''
    ),
    CONSTRAINT ck_quote_packages_cancelled_at CHECK (
        cancelled_at IS NULL OR cancelled_at >= created_at
    )
);

-- Danh sách package/dropdown của Quote; lọc cancelled_at IS NULL để chọn package.
CREATE INDEX ix_quote_packages_quote_created_at
    ON public.quote_packages (quote_id, created_at DESC);

-- Backend kiểm tra Customer sở hữu Quote và chỉ cho sửa khi Quote cho phép.
-- Sửa tên/địa chỉ/ngày giao hoặc phân bổ phải tăng revision/gửi duyệt lại nếu cần.
-- Hủy package giữ thời điểm hủy; lượng đã phân bổ trở lại phần chưa phân bổ của Quote.
-- quote_package_items sẽ lưu liên kết sản phẩm/số lượng; bước tiếp theo kiểm tra đủ lượng.
-- Khi xác nhận mua, copy package còn hiệu lực sang order_packages trong transaction.
ALTER TABLE public.quote_packages ENABLE ROW LEVEL SECURITY;

-- Một dòng Quote có thể chia lượng vào nhiều package; mỗi package có nhiều dòng hàng.
CREATE TABLE public.quote_package_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- quote_id dùng để ràng buộc package và dòng hàng thuộc cùng Quote.
    quote_id UUID NOT NULL,
    quote_package_id UUID NOT NULL,
    quote_item_id UUID NOT NULL,
    quantity NUMERIC(12, 3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_quote_package_items_package FOREIGN KEY (quote_package_id, quote_id)
        REFERENCES public.quote_packages (id, quote_id) ON DELETE CASCADE,
    CONSTRAINT fk_quote_package_items_item FOREIGN KEY (quote_item_id, quote_id)
        REFERENCES public.quote_items (id, quote_id) ON DELETE CASCADE,
    CONSTRAINT ux_quote_package_items_package_item UNIQUE (quote_package_id, quote_item_id),
    CONSTRAINT ck_quote_package_items_quantity CHECK (quantity > 0)
);

-- UNIQUE(package, item) hỗ trợ lấy hàng trong package; index này lấy các phân bổ của dòng.
CREATE INDEX ix_quote_package_items_item_id
    ON public.quote_package_items (quote_item_id);
CREATE INDEX ix_quote_package_items_quote_id
    ON public.quote_package_items (quote_id);

-- Không lưu lại product_id/tên/giá/note; lấy từ quote_items để tránh trùng dữ liệu.
-- Backend chỉ cho sửa Quote đúng chủ sở hữu/trạng thái và package chưa hủy.
-- Khóa Quote/dòng hàng trong transaction khi phân bổ: SUM lượng package còn hiệu lực
-- không vượt quantity của quote_items; sửa phải tăng revision và kiểm tra bước lượng.
-- Lưu nháp có thể còn thiếu; tiếp tục/submit/xác nhận mua yêu cầu SUM bằng đúng quantity.
-- Package bị hủy không tính vào SUM; giữ lịch sử phân bổ và cho Customer phân lại/xóa dòng.
-- Backend copy các phân bổ hợp lệ sang package_items khi xác nhận Order.
ALTER TABLE public.quote_package_items ENABLE ROW LEVEL SECURITY;

-- E-commerce quản lý chương trình sale; products liên kết trực tiếp, poster có bảng riêng.
CREATE TABLE public.promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name_vi VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NOT NULL,
    content_vi TEXT,
    content_en TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    revision INTEGER NOT NULL DEFAULT 1,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    published_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    published_revision INTEGER,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_promotions_names_nonblank CHECK (
        BTRIM(name_vi) <> '' AND BTRIM(name_en) <> ''
    ),
    CONSTRAINT ck_promotions_content CHECK (
        (content_vi IS NULL OR BTRIM(content_vi) <> '')
        AND (content_en IS NULL OR BTRIM(content_en) <> '')
    ),
    CONSTRAINT ck_promotions_status CHECK (
        status IN ('DRAFT', 'PUBLISHED', 'DISABLED', 'ARCHIVED')
    ),
    CONSTRAINT ck_promotions_revision CHECK (revision > 0),
    CONSTRAINT ck_promotions_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_promotions_publication_metadata CHECK (
        (published_at IS NULL AND published_by IS NULL AND published_revision IS NULL)
        OR (published_at IS NOT NULL AND published_revision IS NOT NULL
            AND published_revision > 0 AND published_revision <= revision)
    ),
    CONSTRAINT ck_promotions_published_state CHECK (
        status <> 'PUBLISHED' OR (
            published_at IS NOT NULL AND published_revision IS NOT NULL
            AND published_revision = revision
            AND content_vi IS NOT NULL AND content_en IS NOT NULL
        )
    ),
    CONSTRAINT ck_promotions_published_at CHECK (
        published_at IS NULL OR published_at >= created_at
    )
);

CREATE INDEX ix_promotions_status_created_at
    ON public.promotions (status, created_at DESC);
CREATE INDEX ix_promotions_published_period
    ON public.promotions (starts_at, ends_at) WHERE status = 'PUBLISHED';

-- Hiệu lực: PUBLISHED và starts_at <= thời gian backend < ends_at.
-- SCHEDULED/ACTIVE/ENDED là nhãn tính từ thời gian, không lưu thêm vào status.
-- Backend kiểm tra quyền E-commerce, nội dung/quy tắc, revision và poster nếu có.
-- Sửa nội dung/quy tắc đã Publish chuyển về DRAFT/tăng revision, duyệt lại trước Publish.
-- Một chương trình có nhiều sản phẩm; mỗi sản phẩm chỉ giữ một promotion_id, không cần bảng nối.
-- Backend ngăn ghi đè liên kết sang chương trình khác khi Staff chưa chủ động thay chương trình.
-- AI hỗ trợ cảnh báo; quyền, xung đột và giá được backend kiểm tra trong transaction.
-- Giá sale từng sản phẩm nằm ở product_prices; coupon vẫn là ưu đãi riêng khi tính Quote/Order.
-- ARCHIVED là ngừng/xóa mềm; không sửa snapshot giá của Order đã xác nhận.
ALTER TABLE public.promotions ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.products
    ADD CONSTRAINT fk_products_promotion FOREIGN KEY (promotion_id)
    REFERENCES public.promotions (id) ON DELETE RESTRICT;

-- Hồ sơ xét duyệt Customer; dữ liệu form nằm ở users/user_profiles để tránh lưu trùng.
CREATE TABLE public.registration_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE REFERENCES public.users (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    otp_sent_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_registration_requests_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_registration_requests_review_note CHECK (
        review_note IS NULL OR BTRIM(review_note) <> ''
    ),
    CONSTRAINT ck_registration_requests_reviewed_state CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND review_note IS NULL)
        OR (
            status IN ('APPROVED', 'REJECTED')
            AND reviewed_at IS NOT NULL AND reviewed_at >= submitted_at
        )
    ),
    CONSTRAINT ck_registration_requests_decision_note CHECK (
        status <> 'REJECTED' OR review_note IS NOT NULL
    ),
    CONSTRAINT ck_registration_requests_otp_state CHECK (
        otp_sent_at IS NULL OR (
            status = 'APPROVED' AND reviewed_at IS NOT NULL AND otp_sent_at >= reviewed_at
        )
    ),
    CONSTRAINT ck_registration_requests_activation CHECK (
        activated_at IS NULL OR (
            status = 'APPROVED' AND otp_sent_at IS NOT NULL AND activated_at >= otp_sent_at
        )
    ),
    CONSTRAINT ck_registration_requests_timestamps CHECK (
        submitted_at >= created_at AND (reviewed_at IS NULL OR reviewed_at >= created_at)
    )
);

-- customer_id UNIQUE đã có index; mỗi Customer có một hồ sơ đăng ký.
CREATE INDEX ix_registration_requests_status_submitted_at
    ON public.registration_requests (status, submitted_at DESC);

-- Backend tạo hồ sơ Auth/users/user_profiles chờ kích hoạt khi tiếp nhận đăng ký.
-- Chỉ Customer có request; một Admin duyệt/từ chối, role Staff không tự đăng ký.
-- Form gửi một lần, không sửa/gửi lại; backend ngăn trùng email/tài khoản.
-- Backend chỉ cho quyết định khi PENDING; không đổi lại quyết định đã duyệt/từ chối.
-- APPROVED chỉ là duyệt hồ sơ; chỉ gửi OTP sau duyệt và ghi otp_sent_at khi gửi thành công.
-- Sau OTP hợp lệ và tạo password xong mới ghi activated_at; mật khẩu/OTP do Auth quản lý.
-- API mua yêu cầu APPROVED + activated_at + email verified + không bị ban, không chỉ JWT.
-- API theo dõi hồ sơ chờ phải xác minh phiên đăng ký/chủ sở hữu; client không tự sửa quyết định.
ALTER TABLE public.registration_requests ENABLE ROW LEVEL SECURITY;

-- Một Order có một chuyến; Logistics chuẩn bị và phân công Delivery Staff.
CREATE TABLE public.delivery_trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES public.orders (id) ON DELETE RESTRICT,
    delivery_staff_id UUID REFERENCES public.users (id) ON DELETE RESTRICT,
    assigned_by UUID REFERENCES public.users (id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    -- Snapshot vị trí kho khi lập tuyến; không phải GPS thực tế.
    origin_latitude NUMERIC(9, 6) NOT NULL,
    origin_longitude NUMERIC(9, 6) NOT NULL,
    -- Tuyến Vietmap mã hóa; NULL khi chưa có tuyến hoặc dùng màn hình dự phòng.
    route_polyline TEXT,
    planned_departure_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_delivery_trips_status CHECK (
        status IN ('PREPARING', 'READY', 'IN_DELIVERY', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_delivery_trips_origin CHECK (
        origin_latitude BETWEEN -90 AND 90 AND origin_longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT ck_delivery_trips_route_nonblank CHECK (
        route_polyline IS NULL OR BTRIM(route_polyline) <> ''
    ),
    CONSTRAINT ck_delivery_trips_assignment CHECK (
        (delivery_staff_id IS NULL AND assigned_at IS NULL AND assigned_by IS NULL)
        OR (delivery_staff_id IS NOT NULL AND assigned_at IS NOT NULL)
    ),
    CONSTRAINT ck_delivery_trips_state CHECK (
        (status = 'PREPARING' AND started_at IS NULL AND completed_at IS NULL
            AND cancelled_at IS NULL)
        OR (status = 'READY' AND delivery_staff_id IS NOT NULL
            AND started_at IS NULL AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'IN_DELIVERY' AND delivery_staff_id IS NOT NULL
            AND started_at IS NOT NULL AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'COMPLETED' AND delivery_staff_id IS NOT NULL
            AND started_at IS NOT NULL AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND started_at IS NULL
            AND completed_at IS NULL AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_delivery_trips_timestamps CHECK (
        (assigned_at IS NULL OR assigned_at >= created_at)
        AND (started_at IS NULL OR (started_at >= created_at AND started_at >= assigned_at))
        AND (completed_at IS NULL OR completed_at >= started_at)
        AND (cancelled_at IS NULL OR cancelled_at >= created_at)
    )
);

-- Chặn phân công đồng thời nhiều chuyến chưa kết thúc cho cùng Delivery Staff.
CREATE UNIQUE INDEX ux_delivery_trips_staff_active
    ON public.delivery_trips (delivery_staff_id)
    WHERE delivery_staff_id IS NOT NULL AND status IN ('PREPARING', 'READY', 'IN_DELIVERY');
CREATE INDEX ix_delivery_trips_status_created_at
    ON public.delivery_trips (status, created_at DESC);
CREATE INDEX ix_delivery_trips_staff_created_at
    ON public.delivery_trips (delivery_staff_id, created_at DESC);

-- Backend kiểm tra role Delivery Staff/Logistics, ban và quyền trên từng chuyến.
-- Khóa Order/chuyến khi đổi phân công/trạng thái; không đổi Order nguồn sau khi tạo.
-- Chỉ bắt đầu khi đã thanh toán SUCCESS, Order chưa hủy và còn package cần giao.
-- Tuyến/điểm đủ dữ liệu trước khi READY; Order PROCESSING không đồng nghĩa đang giao.
-- Hoàn tất khi mọi package còn hiệu lực đã giao; cập nhật Order trong cùng transaction.
-- Vận tốc mô phỏng cố định 40 km/h trong backend, không lưu theo chuyến.
-- ETA tính từ tổng khoảng cách các chặng (km) / 40 (giờ), không lưu tổng/ETA trùng lặp.
-- Khi chưa xuất phát dùng planned_departure_at; sau xuất phát dùng started_at.
-- Hủy chuyến trước xuất phát; hủy trong chuyến/sau giao theo luồng riêng chưa chốt.
ALTER TABLE public.delivery_trips ENABLE ROW LEVEL SECURITY;

-- Mỗi package có một điểm giao; nhiều package cùng địa chỉ có thể gộp marker trên map.
CREATE TABLE public.delivery_stops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES public.delivery_trips (id) ON DELETE RESTRICT,
    order_package_id UUID NOT NULL UNIQUE REFERENCES public.order_packages (id) ON DELETE RESTRICT,
    stop_order INTEGER NOT NULL,
    destination_latitude NUMERIC(9, 6),
    destination_longitude NUMERIC(9, 6),
    -- Chặng từ kho/điểm trước tới điểm này theo thứ tự giao; không phải khoảng cách còn lại.
    leg_distance_km NUMERIC(12, 3),
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ux_delivery_stops_trip_order UNIQUE (trip_id, stop_order),
    CONSTRAINT ck_delivery_stops_order CHECK (stop_order > 0),
    CONSTRAINT ck_delivery_stops_coordinates CHECK (
        (destination_latitude IS NULL AND destination_longitude IS NULL)
        OR (destination_latitude IS NOT NULL AND destination_longitude IS NOT NULL
            AND destination_latitude BETWEEN -90 AND 90
            AND destination_longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT ck_delivery_stops_distance CHECK (
        leg_distance_km IS NULL OR leg_distance_km >= 0
    ),
    CONSTRAINT ck_delivery_stops_delivered_at CHECK (
        delivered_at IS NULL OR delivered_at >= created_at
    )
);

-- UNIQUE(trip_id, stop_order) hỗ trợ danh sách điểm; UNIQUE(package) hỗ trợ tra package.
CREATE INDEX ix_delivery_stops_trip_pending
    ON public.delivery_stops (trip_id, stop_order) WHERE delivered_at IS NULL;

-- Không chép order_id/địa chỉ/ngày giao/người nhận: JOIN trip, package và Order.
-- Backend kiểm tra package thuộc Order của trip; FK riêng không kiểm tra được điều này.
-- Không đổi Order nguồn của package/trip; mọi chỉnh tuyến khóa cùng bản ghi trip.
-- Package hủy lấy từ order_packages.cancelled_at, không lưu lại status/cancelled_at tại stop.
-- Khi bỏ điểm đã hủy, tính lại chặng/tuyến còn giao; giữ điểm đã giao để xem lịch sử.
-- Chỉ Delivery Staff được phân công xác nhận khi IN_DELIVERY, package chưa hủy.
-- delivered_at >= trip.started_at; xác nhận lặp không ghi lại thời điểm giao.
-- Địa chỉ/ngày giao khác nhau vẫn là các điểm riêng, kể cả trùng tọa độ.
-- ETA từng điểm = thời điểm xuất phát + tổng khoảng cách tới điểm (km) / 40 (giờ).
-- ETA qua không tự ghi delivered_at; không có GPS hoặc workflow giao thất bại.
ALTER TABLE public.delivery_stops ENABLE ROW LEVEL SECURITY;

-- Lịch sử biến động của kho duy nhất; một dòng ghi một thao tác trên một sản phẩm.
CREATE TABLE public.inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES public.inventory (product_id) ON DELETE RESTRICT,
    order_id UUID REFERENCES public.orders (id) ON DELETE RESTRICT,
    movement_type VARCHAR(20) NOT NULL,
    -- RECEIVE/RESERVE/SHIP/RELEASE: lượng dương; ADJUST: chênh lệch tăng (+)/giảm (-).
    quantity NUMERIC(12, 3) NOT NULL,
    reason TEXT,
    created_by UUID NOT NULL REFERENCES public.users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_inventory_movements_type CHECK (
        movement_type IN ('RECEIVE', 'RESERVE', 'SHIP', 'RELEASE', 'ADJUST')
    ),
    CONSTRAINT ck_inventory_movements_quantity CHECK (
        (movement_type = 'ADJUST' AND quantity <> 0)
        OR (movement_type IN ('RECEIVE', 'RESERVE', 'SHIP', 'RELEASE') AND quantity > 0)
    ),
    CONSTRAINT ck_inventory_movements_order CHECK (
        movement_type NOT IN ('RESERVE', 'SHIP', 'RELEASE') OR order_id IS NOT NULL
    ),
    CONSTRAINT ck_inventory_movements_reason CHECK (
        reason IS NULL OR BTRIM(reason) <> ''
    ),
    CONSTRAINT ck_inventory_movements_adjustment_reason CHECK (
        movement_type <> 'ADJUST' OR reason IS NOT NULL
    )
);

CREATE INDEX ix_inventory_movements_product_created_at
    ON public.inventory_movements (product_id, created_at DESC);
CREATE INDEX ix_inventory_movements_order_created_at
    ON public.inventory_movements (order_id, created_at DESC) WHERE order_id IS NOT NULL;
CREATE INDEX ix_inventory_movements_type_created_at
    ON public.inventory_movements (movement_type, created_at DESC);

-- Số lượng cùng đơn vị bán của sản phẩm; không lưu lại tên/đơn vị/giá hoặc số tồn trước/sau.
-- Khởi tạo inventory bằng 0 khi tạo SKU; đăng catalog không tự tạo biến động nhập hàng.
-- RECEIVE: on_hand + quantity; RESERVE: reserved + quantity.
-- SHIP: on_hand - quantity và reserved - quantity; RELEASE: chỉ reserved - quantity.
-- ADJUST: on_hand + quantity, không tự sửa lượng giữ; bắt buộc lý do kiểm kê/điều chỉnh.
-- Backend khóa dòng inventory và ghi movement/cập nhật tồn trong cùng transaction.
-- Giữ/xuất/giải phóng kiểm tra sản phẩm thuộc Order và lượng hợp lệ theo phần đã xử lý.
-- Kiểm tra Logistics cho thao tác kho; created_by lấy từ phiên của người khởi tạo thao tác.
-- Backend tạo id ổn định cho từng event, dùng lại khi retry; trùng id phải kiểm tra payload,
-- trả kết quả cũ nếu khớp và không cập nhật tồn lần nữa; payload khác báo xung đột.
-- Lịch sử chỉ ghi thêm; sửa sai bằng movement mới, không sửa/xóa dòng đã ghi nhận.
-- Hủy hàng chưa xuất chỉ RELEASE, không RECEIVE; hàng đã xuất chỉ nhập khi thực tế nhận lại.
-- INSERT movement không tự cập nhật inventory; API transaction chưa triển khai.
ALTER TABLE public.inventory_movements ENABLE ROW LEVEL SECURITY;

-- Phiên AI dùng chung cho chat/đặt nhanh theo tác vụ, hoặc E-commerce tạo poster.
CREATE TABLE public.ai_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.users (id) ON DELETE RESTRICT,
    session_type VARCHAR(30) NOT NULL,
    title VARCHAR(200),
    language_code VARCHAR(2) NOT NULL DEFAULT 'vi',
    -- Route frontend, không phải URL API/tool đã được gọi.
    current_route VARCHAR(255),
    context JSONB NOT NULL DEFAULT '{}'::JSONB,
    context_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,

    CONSTRAINT ck_ai_sessions_type CHECK (
        session_type IN ('SHOPPING_ASSISTANT', 'POSTER_DESIGN')
    ),
    CONSTRAINT ck_ai_sessions_poster_owner CHECK (
        session_type <> 'POSTER_DESIGN' OR user_id IS NOT NULL
    ),
    CONSTRAINT ck_ai_sessions_title CHECK (title IS NULL OR BTRIM(title) <> ''),
    CONSTRAINT ck_ai_sessions_language CHECK (language_code IN ('vi', 'en')),
    CONSTRAINT ck_ai_sessions_route CHECK (
        current_route IS NULL OR (BTRIM(current_route) = current_route AND current_route LIKE '/%')
    ),
    CONSTRAINT ck_ai_sessions_context_object CHECK (JSONB_TYPEOF(context) = 'object'),
    CONSTRAINT ck_ai_sessions_context_version CHECK (context_version > 0),
    CONSTRAINT ck_ai_sessions_status CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED')),
    CONSTRAINT ck_ai_sessions_timestamps CHECK (
        updated_at >= created_at
        AND (expires_at IS NULL OR expires_at > created_at)
        AND (closed_at IS NULL OR (closed_at >= created_at AND closed_at <= updated_at))
    ),
    CONSTRAINT ck_ai_sessions_closed_state CHECK (
        (status = 'CLOSED' AND closed_at IS NOT NULL)
        OR (status IN ('ACTIVE', 'EXPIRED') AND closed_at IS NULL)
    ),
    CONSTRAINT ck_ai_sessions_expired_state CHECK (
        status <> 'EXPIRED' OR (expires_at IS NOT NULL AND updated_at >= expires_at)
    )
);

CREATE INDEX ix_ai_sessions_user_updated_at
    ON public.ai_sessions (user_id, updated_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX ix_ai_sessions_status_expires_at
    ON public.ai_sessions (status, expires_at) WHERE expires_at IS NOT NULL;

-- context chỉ giữ intent/điều kiện/ID tham chiếu; không chép giá, tồn hoặc cả hội thoại.
-- Backend kiểm tra quyền, loại phiên, route allowlist và ID trong context trước khi dùng.
-- Guest user_id NULL vẫn phải có cơ chế xác minh chủ phiên riêng; ID không là quyền truy cập.
-- Chặn xóa chủ tài khoản khi còn phiên, tránh biến phiên riêng thành phiên Guest.
-- Mỗi yêu cầu/ngữ cảnh mới tăng context_version trong transaction và cập nhật updated_at;
-- chỉ áp phản hồi khi version còn khớp, không để kết quả đến trễ ghi đè tác vụ mới.
-- Đóng phiên ghi CLOSED/closed_at/updated_at cùng lúc. Hết hạn phải kiểm tra cả expires_at;
-- thời gian trôi không tự đổi status, worker/API đánh dấu EXPIRED sau khi tới hạn.
-- Bảng là nền tảng schema; chưa triển khai AI, API session, memory hay RLS policies.
ALTER TABLE public.ai_sessions ENABLE ROW LEVEL SECURITY;
