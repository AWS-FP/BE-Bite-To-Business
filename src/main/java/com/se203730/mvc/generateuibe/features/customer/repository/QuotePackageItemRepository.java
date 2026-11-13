package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface QuotePackageItemRepository extends JpaRepository<QuotePackageItem, UUID> {
    List<QuotePackageItem> findAllByQuotePackageId(UUID packageId);
    void deleteAllByQuotePackageId(UUID packageId);

    @Query(value = """
        select coalesce(sum(qpi.quantity), 0) from public.quote_package_items qpi
        join public.quote_packages qp on qp.id = qpi.quote_package_id
        where qpi.quote_item_id = :itemId and qp.cancelled_at is null and qp.id <> :packageId
        """, nativeQuery = true)
    BigDecimal allocatedElsewhere(@Param("itemId") UUID itemId, @Param("packageId") UUID packageId);
}
