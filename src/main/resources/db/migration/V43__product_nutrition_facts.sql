-- Bảng thông tin dinh dưỡng cho trang chi tiết sản phẩm (tùy chọn).
-- Mỗi thành phần là một dòng riêng (không lưu JSON) để giữ 1NF; thứ tự hiển thị theo sort_order.
-- Chỉ thêm bảng/cột mới, code cũ vẫn chạy được trên DB dùng chung.

-- Lượng tham chiếu của bảng, ví dụ "100g" -> "Giá trị ước tính trên 100g".
ALTER TABLE public.product_details
    ADD COLUMN nutrition_basis VARCHAR(100),
    ADD CONSTRAINT ck_product_details_nutrition_basis CHECK (
        nutrition_basis IS NULL OR BTRIM(nutrition_basis) <> ''
    );

CREATE TABLE public.product_nutrition_facts (
    product_id UUID NOT NULL REFERENCES public.products (id) ON DELETE CASCADE,
    sort_order SMALLINT NOT NULL,
    nutrient VARCHAR(100) NOT NULL,
    amount VARCHAR(50) NOT NULL,
    CONSTRAINT pk_product_nutrition_facts PRIMARY KEY (product_id, sort_order),
    CONSTRAINT ck_product_nutrition_facts_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_product_nutrition_facts_nonblank CHECK (
        BTRIM(nutrient) <> '' AND BTRIM(amount) <> ''
    )
);

-- Một thành phần chỉ xuất hiện một lần trong bảng của một sản phẩm.
CREATE UNIQUE INDEX ux_product_nutrition_facts_product_nutrient
    ON public.product_nutrition_facts (product_id, LOWER(BTRIM(nutrient)));

ALTER TABLE public.product_nutrition_facts ENABLE ROW LEVEL SECURITY;
