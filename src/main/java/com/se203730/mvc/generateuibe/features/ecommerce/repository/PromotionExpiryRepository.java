package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** An idempotent lifecycle transition, safe across multiple ECS replicas. */
@Repository
@RequiredArgsConstructor
public class PromotionExpiryRepository {
    private final JdbcTemplate jdbc;

    public int disableExpiredPublishedCampaigns() {
        // Do not modify product_prices: sale_price is shared across promotions.
        // Customer pricing and checkout use only currently active published promotions.
        return jdbc.update("""
            UPDATE public.promotions
               SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP
             WHERE status = 'PUBLISHED'
               AND ends_at <= CURRENT_TIMESTAMP
            """);
    }
}
