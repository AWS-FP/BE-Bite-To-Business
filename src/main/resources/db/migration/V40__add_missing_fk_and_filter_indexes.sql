-- Chỉ bổ sung index (additive, không đổi dữ liệu). Xem docs/database-normalization-audit.md.
-- PostgreSQL không tự tạo index cho cột khóa ngoại; các index dưới đây phục vụ
-- truy vấn lọc hiện có và kiểm tra FK khi xóa/cập nhật dòng cha.

-- Lọc/đếm theo thương hiệu (customer brand discovery) và ON DELETE SET NULL khi xóa brand.
CREATE INDEX IF NOT EXISTS ix_products_brand_status
    ON public.products (brand_id, status)
    WHERE brand_id IS NOT NULL;

-- Xóa bậc giá (ExecutiveProductService) kích hoạt ON DELETE SET NULL trên quote_items/order_items;
-- thiếu index sẽ quét toàn bảng dòng báo giá và dòng đơn hàng mỗi lần xóa.
CREATE INDEX IF NOT EXISTS ix_quote_items_price_tier_id
    ON public.quote_items (price_tier_id)
    WHERE price_tier_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_order_items_price_tier_id
    ON public.order_items (price_tier_id)
    WHERE price_tier_id IS NOT NULL;

-- Đếm lượt dùng mã freeship (CouponRepository.countUsedOrReserved), đối xứng với
-- ix_orders_coupon_customer_redeemed_at đã có cho mã giảm giá sản phẩm.
CREATE INDEX IF NOT EXISTS ix_orders_shipping_coupon_customer_redeemed_at
    ON public.orders (shipping_coupon_id, customer_id, shipping_coupon_redeemed_at)
    WHERE shipping_coupon_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_quotes_shipping_coupon_id
    ON public.quotes (shipping_coupon_id)
    WHERE shipping_coupon_id IS NOT NULL;

-- V32 xóa cột package_refunds.order_id nên ix_package_refunds_order mất theo;
-- truy vấn hoàn tiền theo kiện/đơn hiện join qua order_package_id.
CREATE INDEX IF NOT EXISTS ix_package_refunds_package_created_at
    ON public.package_refunds (order_package_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_package_refunds_payment_id
    ON public.package_refunds (payment_id);

-- Danh sách giao dịch SePay cần xem lại (findTop100ByResultOrderByReceivedAtDesc) và tra theo payment.
CREATE INDEX IF NOT EXISTS ix_sepay_event_result_received_at
    ON public.sepay_webhook_events (result, received_at DESC);
CREATE INDEX IF NOT EXISTS ix_sepay_event_payment_id
    ON public.sepay_webhook_events (payment_id)
    WHERE payment_id IS NOT NULL;

-- ON DELETE CASCADE từ promotions; khóa duy nhất hiện có bắt đầu bằng customer_id nên không dùng được.
CREATE INDEX IF NOT EXISTS ix_promotion_email_deliveries_promotion
    ON public.promotion_email_deliveries (promotion_id);

-- FK (quote_package_id, quote_id) RESTRICT: index hiện có bắt đầu bằng quote_id.
CREATE INDEX IF NOT EXISTS ix_quote_address_reviews_package
    ON public.quote_address_reviews (quote_package_id, reviewed_at DESC);

-- Mỗi khách chỉ có một đánh giá còn hiệu lực cho một sản phẩm (ReviewService.hasActiveReview).
-- Chỉ tạo khi dữ liệu hiện tại đã sạch để migration không chặn BE khởi động trên DB dùng chung.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.product_reviews
        WHERE deleted_at IS NULL
        GROUP BY customer_id, product_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE NOTICE 'V40: skipped ux_product_reviews_active_customer_product because duplicate active reviews exist';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS ux_product_reviews_active_customer_product
            ON public.product_reviews (customer_id, product_id)
            WHERE deleted_at IS NULL;
    END IF;
END $$;
