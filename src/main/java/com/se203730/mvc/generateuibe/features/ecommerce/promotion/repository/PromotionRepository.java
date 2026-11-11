package com.se203730.mvc.generateuibe.features.ecommerce.promotion.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.ActiveSaleInfo;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.FlashSaleProductResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.PromotionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PromotionRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    /** The schema export has no promotion_type yet; don't query absent columns. */
    private boolean hasPromotionType() {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'promotions'
                      AND column_name = 'promotion_type'
                )
                """, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public List<PromotionResponse> findActiveStandardPromotions(int limit) {
        // Before the optional migration, every promotion is treated as STANDARD.
        String typeClause = hasPromotionType()
                ? " AND promotion_type = 'STANDARD' "
                : "";

        String sql = """
                SELECT id, name_vi, name_en, content_vi, content_en,
                       starts_at, ends_at
                FROM public.promotions
                WHERE status = 'PUBLISHED'
                  AND starts_at <= CURRENT_TIMESTAMP
                  AND ends_at > CURRENT_TIMESTAMP
                """ + typeClause + """
                ORDER BY ends_at ASC, id ASC
                LIMIT ?
                """;

        return jdbc.query(sql, (rs, rowNum) -> new PromotionResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name_vi"),
                rs.getString("name_en"),
                rs.getString("content_vi"),
                rs.getString("content_en"),
                asInstant(rs.getTimestamp("starts_at")),
                asInstant(rs.getTimestamp("ends_at"))
        ), limit);
    }

    public List<FlashSaleProductResponse> findActiveFlashSaleProducts(int limit) {
        if (!hasPromotionType()) {
            // Cannot distinguish FLASH_SALE from STANDARD without a type column.
            return List.of();
        }

        String sql = """
                SELECT p.id AS product_id, p.sku, p.name_vi, p.name_en,
                       p.image_url, p.unit_code,
                       pp.list_price, pp.sale_price,
                       GREATEST(inv.on_hand_quantity - inv.reserved_quantity, 0)
                           AS available_quantity,
                       promo.id AS promotion_id,
                       promo.name_vi AS promotion_name_vi,
                       promo.ends_at AS promotion_ends_at
                FROM public.products p
                JOIN public.product_prices pp ON pp.product_id = p.id
                JOIN public.inventory inv ON inv.product_id = p.id
                JOIN public.promotions promo ON promo.id = p.promotion_id
                WHERE p.status = 'PUBLISHED'
                  AND promo.status = 'PUBLISHED'
                  AND promo.promotion_type = 'FLASH_SALE'
                  AND promo.starts_at <= CURRENT_TIMESTAMP
                  AND promo.ends_at > CURRENT_TIMESTAMP
                  AND pp.sale_price IS NOT NULL
                  AND pp.sale_price >= 0
                  AND pp.sale_price < pp.list_price
                  AND inv.on_hand_quantity > inv.reserved_quantity
                ORDER BY promo.ends_at ASC, p.id ASC
                LIMIT ?
                """;

        return jdbc.query(sql, (rs, rowNum) -> new FlashSaleProductResponse(
                rs.getObject("product_id", UUID.class),
                rs.getString("sku"),
                rs.getString("name_vi"),
                rs.getString("name_en"),
                rs.getString("image_url"),
                rs.getString("unit_code"),
                rs.getBigDecimal("list_price"),
                rs.getBigDecimal("sale_price"),
                rs.getBigDecimal("available_quantity"),
                rs.getObject("promotion_id", UUID.class),
                rs.getString("promotion_name_vi"),
                asInstant(rs.getTimestamp("promotion_ends_at"))
        ), limit);
    }

    /** One query to obtain effective sale metadata for multiple product cards. */
    public Map<UUID, ActiveSaleInfo> findActiveSalesByProductIds(
            Collection<UUID> productIds
    ) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
                SELECT p.id AS product_id, pp.sale_price,
                       promo.id AS promotion_id,
                       promo.name_vi AS promotion_name_vi,
                       promo.ends_at AS promotion_ends_at
                FROM public.products p
                JOIN public.product_prices pp ON pp.product_id = p.id
                JOIN public.promotions promo ON promo.id = p.promotion_id
                WHERE p.id IN (:productIds)
                  AND p.status = 'PUBLISHED'
                  AND promo.status = 'PUBLISHED'
                  AND promo.starts_at <= CURRENT_TIMESTAMP
                  AND promo.ends_at > CURRENT_TIMESTAMP
                  AND pp.sale_price IS NOT NULL
                  AND pp.sale_price >= 0
                  AND pp.sale_price < pp.list_price
                """;

        var params = new MapSqlParameterSource("productIds", productIds);
        List<ActiveSaleInfo> rows = namedJdbc.query(sql, params,
                (rs, rowNum) -> new ActiveSaleInfo(
                        rs.getObject("product_id", UUID.class),
                        rs.getBigDecimal("sale_price"),
                        rs.getObject("promotion_id", UUID.class),
                        rs.getString("promotion_name_vi"),
                        asInstant(rs.getTimestamp("promotion_ends_at"))
                ));

        return rows.stream().collect(Collectors.toMap(
                ActiveSaleInfo::productId,
                item -> item
        ));
    }

    private static Instant asInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
