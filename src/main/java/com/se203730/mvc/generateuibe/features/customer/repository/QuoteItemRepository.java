package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, UUID> {
    interface QuantityStep {
        UUID getProductId();
        BigDecimal getQuantityStep();
        BigDecimal getMinOrderQuantity();
    }

    @Query(value = """
        select p.id as "productId", p.quantity_step as "quantityStep",
            p.min_order_quantity as "minOrderQuantity"
        from public.quote_items qi join public.products p on p.id = qi.product_id
        where qi.quote_id = :quoteId
        """, nativeQuery = true)
    List<QuantityStep> findQuantitySteps(@Param("quoteId") UUID quoteId);
    List<QuoteItem> findAllByQuoteIdOrderByCreatedAtAsc(UUID quoteId);
    Optional<QuoteItem> findByQuoteIdAndProductId(UUID quoteId, UUID productId);
    Optional<QuoteItem> findByIdAndQuoteId(UUID id, UUID quoteId);

    @Query(value = """
        select exists (select 1 from public.quote_package_items i
            join public.quote_packages p on p.id = i.quote_package_id
            where i.quote_item_id = :id and p.cancelled_at is null)
        """, nativeQuery = true)
    boolean hasPackageAllocations(@Param("id") UUID id);
}
