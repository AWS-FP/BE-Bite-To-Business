package com.se203730.mvc.generateuibe.features.customer.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderCreationRepository {
    private final EntityManager entityManager;

    public void insertOrder(UUID id, UUID quoteId, Instant now) {
        entityManager.createNativeQuery("""
                insert into public.orders (id, order_number, quote_id, quote_revision, customer_id,
                    coupon_id, coupon_code, coupon_discount_type, coupon_discount_value,
                    shipping_coupon_id, shipping_coupon_code, shipping_coupon_discount_type, shipping_coupon_discount_value,
                    contact_name, contact_phone, contact_email, company_name, note, customer_po_reference,
                    items_subtotal, volume_discount_amount, sale_discount_amount, coupon_discount_amount,
                    vat_amount, shipping_fee, shipping_coupon_discount_amount,
                    reservation_started_at, reservation_expires_at, reservation_managed,
                    confirmed_at, created_at, updated_at)
                select :id, 'ORD-' || cast(:id as text), q.id, q.revision, q.customer_id,
                    c.id, c.code, c.discount_type, c.discount_value,
                    s.id, s.code, s.discount_type, s.discount_value,
                    q.contact_name, q.contact_phone, q.contact_email, q.company_name, q.note, q.customer_po_reference,
                    q.items_subtotal, q.volume_discount_amount, q.sale_discount_amount, q.coupon_discount_amount,
                    q.vat_amount, q.shipping_fee, q.shipping_coupon_discount_amount,
                    :now, cast(:now as timestamptz) + interval '1 hour', true, :now, :now, :now
                from public.quotes q
                left join public.coupons c on c.id = q.coupon_id
                left join public.coupons s on s.id = q.shipping_coupon_id
                where q.id = :quoteId
                """).setParameter("id", id).setParameter("quoteId", quoteId).setParameter("now", now).executeUpdate();
    }

    public void insertItems(UUID orderId, UUID quoteId) {
        entityManager.createNativeQuery("""
                insert into public.order_items (id, order_id, quote_item_id, product_id, price_tier_id,
                    sku, name_vi, name_en, unit_code, quantity, min_order_quantity, quantity_step,
                    tier_min_quantity, tier_max_quantity, list_unit_price, volume_unit_price, sale_unit_price,
                    coupon_discount_amount, vat_rate, customer_note)
                select qi.id, :orderId, qi.id, qi.product_id, qi.price_tier_id,
                    qi.sku, qi.name_vi, qi.name_en, qi.unit_code, qi.quantity, p.min_order_quantity, p.quantity_step,
                    tier.min_quantity, tier.max_quantity, qi.list_unit_price, qi.volume_unit_price, qi.sale_unit_price,
                    qi.coupon_discount_amount, qi.vat_rate, qi.customer_note
                from public.quote_items qi join public.products p on p.id = qi.product_id
                left join public.product_price_tiers tier on tier.id = qi.price_tier_id
                where qi.quote_id = :quoteId
                """).setParameter("orderId", orderId).setParameter("quoteId", quoteId).executeUpdate();
    }

    public void insertPackage(UUID orderId, UUID packageId, BigDecimal merchandise, BigDecimal productCoupon,
                              BigDecimal vat, BigDecimal shippingCoupon) {
        entityManager.createNativeQuery("""
                insert into public.order_packages (id, package_number, order_id, package_name, delivery_date,
                    delivery_address, created_by, recipient_name, recipient_phone, delivery_time_slot,
                    delivery_note, city, latitude, longitude, distance_metres, address_status,
                    merchandise_amount, product_coupon_discount_amount, vat_amount, shipping_fee,
                    shipping_coupon_discount_amount)
                select qp.id, 'PKG-' || cast(qp.id as text), :orderId, qp.package_name, qp.delivery_date,
                    qp.delivery_address, qp.created_by, qp.recipient_name, qp.recipient_phone, qp.delivery_time_slot,
                    qp.delivery_note, qp.city, qp.latitude, qp.longitude, qp.distance_metres, qp.address_status,
                    :merchandise, :productCoupon, :vat, qp.shipping_fee, :shippingCoupon
                from public.quote_packages qp where qp.id = :packageId and qp.cancelled_at is null
                """).setParameter("orderId", orderId).setParameter("packageId", packageId)
                .setParameter("merchandise", merchandise).setParameter("productCoupon", productCoupon)
                .setParameter("vat", vat).setParameter("shippingCoupon", shippingCoupon).executeUpdate();
    }

    public void insertPackageItems(UUID orderId, UUID packageId) {
        entityManager.createNativeQuery("""
                insert into public.package_items (order_package_id, order_item_id, order_id, quantity)
                select quote_package_id, quote_item_id, :orderId, quantity
                from public.quote_package_items where quote_package_id = :packageId
                """).setParameter("orderId", orderId).setParameter("packageId", packageId).executeUpdate();
    }
}
