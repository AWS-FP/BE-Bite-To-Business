-- Xóa dữ liệu mẫu: 6 đơn BTB-SEED-26-O* / BTB-DEMO-SALES-O* (không có sản phẩm, không giữ hàng)
-- và 8 báo giá BTB-SEED-26-Q*, cùng mọi dòng phụ thuộc. Xóa từ bảng con lên bảng cha.
CREATE TEMP TABLE seed_orders ON COMMIT DROP AS
SELECT id FROM public.orders
 WHERE order_number LIKE 'BTB-SEED-26-O%' OR order_number LIKE 'BTB-DEMO-SALES-O%';

CREATE TEMP TABLE seed_quotes ON COMMIT DROP AS
SELECT id FROM public.quotes WHERE quote_number LIKE 'BTB-SEED-26-Q%';

-- Đơn được tạo từ báo giá mẫu cũng là dữ liệu mẫu.
INSERT INTO seed_orders SELECT id FROM public.orders WHERE quote_id IN (SELECT id FROM seed_quotes)
    AND id NOT IN (SELECT id FROM seed_orders);

CREATE TEMP TABLE seed_packages ON COMMIT DROP AS
SELECT id FROM public.order_packages WHERE order_id IN (SELECT id FROM seed_orders);

DELETE FROM public.package_returns WHERE order_package_id IN (SELECT id FROM seed_packages);
DELETE FROM public.package_refunds WHERE order_package_id IN (SELECT id FROM seed_packages)
    OR payment_id IN (SELECT id FROM public.payments WHERE order_id IN (SELECT id FROM seed_orders));
DELETE FROM public.delivery_package_claims WHERE package_id IN (SELECT id FROM seed_packages);
DELETE FROM public.delivery_stops WHERE order_package_id IN (SELECT id FROM seed_packages);
DELETE FROM public.inventory_movements WHERE order_id IN (SELECT id FROM seed_orders)
    OR order_package_id IN (SELECT id FROM seed_packages);
DELETE FROM public.package_items WHERE order_package_id IN (SELECT id FROM seed_packages);
DELETE FROM public.order_combo_items WHERE order_item_id IN
    (SELECT id FROM public.order_items WHERE order_id IN (SELECT id FROM seed_orders));
DELETE FROM public.order_items WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM public.order_packages WHERE id IN (SELECT id FROM seed_packages);
DELETE FROM public.sepay_webhook_events WHERE payment_id IN
    (SELECT id FROM public.payments WHERE order_id IN (SELECT id FROM seed_orders));
DELETE FROM public.payments WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM public.delivery_trips WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM public.orders WHERE id IN (SELECT id FROM seed_orders);

DELETE FROM public.quote_address_email_outbox WHERE quote_package_id IN
    (SELECT id FROM public.quote_packages WHERE quote_id IN (SELECT id FROM seed_quotes));
DELETE FROM public.quote_address_reviews WHERE quote_package_id IN
    (SELECT id FROM public.quote_packages WHERE quote_id IN (SELECT id FROM seed_quotes));
DELETE FROM public.quote_revisions WHERE quote_id IN (SELECT id FROM seed_quotes);
-- quote_items / quote_packages / quote_package_items xóa theo ON DELETE CASCADE.
DELETE FROM public.quotes WHERE id IN (SELECT id FROM seed_quotes);
