package com.se203730.mvc.generateuibe.features.customer.quote.repository;

import com.se203730.mvc.generateuibe.features.customer.quote.dto.response.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL tập trung ở đây để tận dụng các bảng Quote đã có, không cần Entity mới. */
@Repository
@RequiredArgsConstructor
public class QuoteRepository {
    private final JdbcTemplate jdbc;

    public record Header(UUID id, UUID customerId, String status) {}
    public record ProductPrice(
            UUID productId, String sku, String nameVi, String nameEn,
            String unitCode, BigDecimal moq, BigDecimal step,
            BigDecimal listPrice, BigDecimal activeSalePrice
    ) {}
    public record Tier(UUID id, BigDecimal unitPrice) {}

    public UUID create(UUID customerId) {
        UUID id = UUID.randomUUID();
        String number = "QT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbc.update("""
                INSERT INTO public.quotes (id, quote_number, customer_id, status)
                VALUES (?, ?, ?, 'DRAFT')
                """, id, number, customerId);
        return id;
    }

    public Optional<Header> findHeader(UUID quoteId, boolean lock) {
        String sql = "SELECT id, customer_id, status FROM public.quotes WHERE id = ?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> new Header(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getString("status")), quoteId).stream().findFirst();
    }

    public Optional<ProductPrice> publishedProduct(UUID productId) {
        return jdbc.query("""
                SELECT p.id, p.sku, p.name_vi, p.name_en, p.unit_code,
                       p.min_order_quantity, p.quantity_step, pp.list_price,
                       CASE WHEN promo.status = 'PUBLISHED'
                                     AND promo.starts_at <= CURRENT_TIMESTAMP
                                     AND promo.ends_at > CURRENT_TIMESTAMP
                            THEN pp.sale_price ELSE NULL END AS active_sale_price
                FROM public.products p
                LEFT JOIN public.product_prices pp ON pp.product_id = p.id
                LEFT JOIN public.promotions promo ON promo.id = p.promotion_id
                WHERE p.id = ? AND p.status = 'PUBLISHED'
                """, (rs, row) -> new ProductPrice(
                rs.getObject("id", UUID.class), rs.getString("sku"),
                rs.getString("name_vi"), rs.getString("name_en"),
                rs.getString("unit_code"), rs.getBigDecimal("min_order_quantity"),
                rs.getBigDecimal("quantity_step"), rs.getBigDecimal("list_price"),
                rs.getBigDecimal("active_sale_price")), productId).stream().findFirst();
    }

    public Optional<Tier> applicableTier(UUID productId, BigDecimal quantity) {
        return jdbc.query("""
                SELECT id, unit_price FROM public.product_price_tiers
                WHERE product_id = ? AND min_quantity <= ?
                  AND (max_quantity IS NULL OR max_quantity >= ?)
                ORDER BY min_quantity DESC, id ASC LIMIT 1
                """, (rs, row) -> new Tier(rs.getObject("id", UUID.class),
                rs.getBigDecimal("unit_price")), productId, quantity, quantity).stream().findFirst();
    }

    public Optional<UUID> itemProduct(UUID quoteId, UUID itemId) {
        return jdbc.query("SELECT product_id FROM public.quote_items WHERE quote_id = ? AND id = ?",
                (rs, row) -> rs.getObject(1, UUID.class), quoteId, itemId).stream().findFirst();
    }

    public record ItemData(UUID productId, BigDecimal quantity, String note) {}

    public Optional<ItemData> itemData(UUID quoteId, UUID itemId) {
        return jdbc.query("""
                SELECT product_id, quantity, customer_note FROM public.quote_items
                WHERE quote_id = ? AND id = ?
                """, (rs, row) -> new ItemData(
                rs.getObject("product_id", UUID.class),
                rs.getBigDecimal("quantity"), rs.getString("customer_note")),
                quoteId, itemId).stream().findFirst();
    }

    public boolean hasActivePackage(UUID quoteId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.quote_packages
                WHERE quote_id = ? AND cancelled_at IS NULL
                """, Integer.class, quoteId);
        return count != null && count > 0;
    }

    public boolean hasPastDeliveryDate(UUID quoteId, java.time.LocalDate currentDate) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.quote_packages
                WHERE quote_id = ? AND cancelled_at IS NULL AND delivery_date < ?
                """, Integer.class, quoteId, currentDate);
        return count != null && count > 0;
    }

    public boolean containsProduct(UUID quoteId, UUID productId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.quote_items WHERE quote_id = ? AND product_id = ?",
                Integer.class, quoteId, productId);
        return count != null && count > 0;
    }

    public UUID addItem(UUID quoteId, ProductPrice p, BigDecimal quantity,
                        UUID tierId, BigDecimal volume, BigDecimal sale, BigDecimal vatRate, String note) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.quote_items
                (id, quote_id, product_id, price_tier_id, sku, name_vi, name_en,
                 unit_code, quantity, list_unit_price, volume_unit_price,
                 sale_unit_price, vat_rate, customer_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, quoteId, p.productId(), tierId, p.sku(), p.nameVi(),
                p.nameEn(), p.unitCode(), quantity, p.listPrice(), volume, sale, vatRate, note);
        return id;
    }

    public void updateItem(UUID itemId, ProductPrice p, BigDecimal quantity,
                           UUID tierId, BigDecimal volume, BigDecimal sale, BigDecimal vatRate, String note) {
        jdbc.update("""
                UPDATE public.quote_items SET
                    price_tier_id = ?, sku = ?, name_vi = ?, name_en = ?, unit_code = ?,
                    quantity = ?, list_unit_price = ?, volume_unit_price = ?,
                    sale_unit_price = ?, vat_rate = ?, customer_note = ?, updated_at = clock_timestamp()
                WHERE id = ?
                """, tierId, p.sku(), p.nameVi(), p.nameEn(), p.unitCode(),
                quantity, p.listPrice(), volume, sale, vatRate, note, itemId);
    }

    public void deleteItem(UUID itemId) {
        jdbc.update("DELETE FROM public.quote_items WHERE id = ?", itemId);
    }

    public UUID createPackage(UUID quoteId, UUID customerId, String name,
                              java.time.LocalDate date, String address) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.quote_packages
                (id, quote_id, package_name, delivery_date, delivery_address, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, quoteId, name, date, address, customerId);
        return id;
    }

    public boolean activePackage(UUID quoteId, UUID packageId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.quote_packages
                WHERE quote_id = ? AND id = ? AND cancelled_at IS NULL
                """, Integer.class, quoteId, packageId);
        return count != null && count > 0;
    }

    public void updatePackage(UUID packageId, String name, java.time.LocalDate date, String address) {
        jdbc.update("""
                UPDATE public.quote_packages SET package_name = ?, delivery_date = ?,
                    delivery_address = ?, updated_at = clock_timestamp()
                WHERE id = ?
                """, name, date, address, packageId);
    }

    public void cancelPackage(UUID packageId) {
        jdbc.update("""
                UPDATE public.quote_packages
                SET cancelled_at = clock_timestamp(), updated_at = clock_timestamp()
                WHERE id = ?
                """, packageId);
    }

    public BigDecimal allocated(UUID quoteId, UUID itemId, UUID excludePackageId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(a.quantity), 0)
                FROM public.quote_package_items a
                JOIN public.quote_packages pkg ON pkg.id = a.quote_package_id
                WHERE a.quote_id = ? AND a.quote_item_id = ?
                  AND pkg.cancelled_at IS NULL
                  AND (?::uuid IS NULL OR a.quote_package_id <> ?::uuid)
                """, BigDecimal.class, quoteId, itemId, excludePackageId, excludePackageId);
    }

    public BigDecimal itemQuantity(UUID quoteId, UUID itemId) {
        return jdbc.query("""
                SELECT quantity FROM public.quote_items WHERE quote_id = ? AND id = ?
                """, (rs, row) -> rs.getBigDecimal(1), quoteId, itemId).stream().findFirst().orElse(null);
    }

    public void upsertAllocation(UUID quoteId, UUID packageId, UUID itemId, BigDecimal quantity) {
        jdbc.update("""
                INSERT INTO public.quote_package_items
                    (id, quote_id, quote_package_id, quote_item_id, quantity)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (quote_package_id, quote_item_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = clock_timestamp()
                """, UUID.randomUUID(), quoteId, packageId, itemId, quantity);
    }

    public void deleteAllocation(UUID quoteId, UUID packageId, UUID itemId) {
        jdbc.update("""
                DELETE FROM public.quote_package_items
                WHERE quote_id = ? AND quote_package_id = ? AND quote_item_id = ?
                """, quoteId, packageId, itemId);
    }

    public void saveContact(UUID quoteId, String name, String phone, String email,
                            String company, String reference, String note) {
        jdbc.update("""
                UPDATE public.quotes SET contact_name = ?, contact_phone = ?, contact_email = ?,
                    company_name = ?, customer_po_reference = ?, note = ?
                WHERE id = ?
                """, name, phone, email, company, reference, note, quoteId);
    }

    /** Cập nhật các tổng từ giá đã snapshot trong quote_items; cột tổng tiền do DB tự tính. */
    public void recalculate(UUID quoteId, boolean bumpRevision) {
        jdbc.update("""
                UPDATE public.quotes q SET
                    items_subtotal = COALESCE((SELECT SUM(line_subtotal)
                        FROM public.quote_items i WHERE i.quote_id = q.id), 0),
                    volume_discount_amount = COALESCE((SELECT SUM(volume_discount_amount)
                        FROM public.quote_items i WHERE i.quote_id = q.id), 0),
                    sale_discount_amount = COALESCE((SELECT SUM(sale_discount_amount)
                        FROM public.quote_items i WHERE i.quote_id = q.id), 0),
                    vat_amount = COALESCE((SELECT SUM(vat_amount)
                        FROM public.quote_items i WHERE i.quote_id = q.id), 0),
                    revision = revision + ?, updated_at = clock_timestamp()
                WHERE q.id = ?
                """, bumpRevision ? 1 : 0, quoteId);
    }

    public void submit(UUID quoteId) {
        jdbc.update("""
                UPDATE public.quotes
                SET status = 'PENDING_APPROVAL', submitted_at = clock_timestamp(),
                    revision = revision + 1, updated_at = clock_timestamp()
                WHERE id = ? AND status = 'DRAFT'
                """, quoteId);
    }

    public List<UUID> itemIds(UUID quoteId) {
        return jdbc.query("SELECT id FROM public.quote_items WHERE quote_id = ? ORDER BY created_at, id",
                (rs, row) -> rs.getObject(1, UUID.class), quoteId);
    }

    public boolean allItemsFullyAllocated(UUID quoteId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.quote_items i
                WHERE i.quote_id = ? AND i.quantity <> COALESCE((
                    SELECT SUM(a.quantity) FROM public.quote_package_items a
                    JOIN public.quote_packages pkg ON pkg.id = a.quote_package_id
                    WHERE a.quote_item_id = i.id AND pkg.cancelled_at IS NULL
                ), 0)
                """, Integer.class, quoteId);
        return count != null && count == 0;
    }

    public QuoteResponse detail(UUID quoteId) {
        List<QuoteResponse.Item> items = jdbc.query("""
                SELECT id, product_id, sku, name_vi, unit_code, quantity,
                       list_unit_price, volume_unit_price, sale_unit_price,
                       line_subtotal, line_total, customer_note
                FROM public.quote_items WHERE quote_id = ? ORDER BY created_at, id
                """, (rs, row) -> new QuoteResponse.Item(
                rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("sku"), rs.getString("name_vi"), rs.getString("unit_code"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("list_unit_price"),
                rs.getBigDecimal("volume_unit_price"), rs.getBigDecimal("sale_unit_price"),
                rs.getBigDecimal("line_subtotal"), rs.getBigDecimal("line_total"),
                rs.getString("customer_note")), quoteId);

        java.util.Map<UUID, java.util.List<QuoteResponse.Allocation>> allocationMap = new java.util.HashMap<>();
        jdbc.query("""
                SELECT a.quote_package_id, a.quote_item_id, a.quantity
                FROM public.quote_package_items a
                JOIN public.quote_packages pkg ON pkg.id = a.quote_package_id
                WHERE a.quote_id = ? AND pkg.cancelled_at IS NULL
                ORDER BY a.quote_package_id, a.quote_item_id
                """, rs -> {
            UUID packageId = rs.getObject("quote_package_id", UUID.class);
            allocationMap.computeIfAbsent(packageId, ignored -> new java.util.ArrayList<>())
                    .add(new QuoteResponse.Allocation(
                            rs.getObject("quote_item_id", UUID.class), rs.getBigDecimal("quantity")));
        }, quoteId);
        List<QuoteResponse.Package> packages = jdbc.query("""
                SELECT id, package_name, delivery_date, delivery_address
                FROM public.quote_packages
                WHERE quote_id = ? AND cancelled_at IS NULL ORDER BY created_at, id
                """, (rs, row) -> {
            UUID packageId = rs.getObject("id", UUID.class);
            return new QuoteResponse.Package(packageId, rs.getString("package_name"),
                    rs.getDate("delivery_date").toLocalDate(),
                    rs.getString("delivery_address"),
                    allocationMap.getOrDefault(packageId, List.of()));
        }, quoteId);

        return jdbc.queryForObject("""
                SELECT id, quote_number, status, revision, contact_name, contact_phone,
                    contact_email, company_name, customer_po_reference, note,
                    items_subtotal, volume_discount_amount, sale_discount_amount,
                    vat_amount, shipping_fee, total_amount, submitted_at
                FROM public.quotes WHERE id = ?
                """, (rs, row) -> {
            Timestamp submitted = rs.getTimestamp("submitted_at");
            Instant submittedAt = submitted == null ? null : submitted.toInstant();
            return new QuoteResponse(
                    rs.getObject("id", UUID.class), rs.getString("quote_number"),
                    rs.getString("status"), rs.getInt("revision"),
                    rs.getString("contact_name"), rs.getString("contact_phone"),
                    rs.getString("contact_email"), rs.getString("company_name"),
                    rs.getString("customer_po_reference"), rs.getString("note"),
                    rs.getBigDecimal("items_subtotal"),
                    rs.getBigDecimal("volume_discount_amount"),
                    rs.getBigDecimal("sale_discount_amount"),
                    rs.getBigDecimal("vat_amount"), rs.getBigDecimal("shipping_fee"),
                    rs.getBigDecimal("total_amount"), submittedAt, items, packages);
        }, quoteId);
    }
}
