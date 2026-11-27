package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.logistics.entity.WarehouseStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {
    interface StockView {
        UUID getProductId();
        String getSku();
        String getName();
        String getUnit();
        String getImageUrl();
        java.math.BigDecimal getOnHand();
        java.math.BigDecimal getReserved();
        java.math.BigDecimal getAvailable();
    }
    @Query(value = "select exists(select 1 from public.products where id=:id and unit_code='COMBO')", nativeQuery = true)
    boolean isCombo(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            insert into public.inventory (product_id, on_hand_quantity, reserved_quantity, updated_by, created_at, updated_at)
            select id, 0, 0, :userId, :now, :now from public.products where id = :id
            on conflict (product_id) do nothing
            """, nativeQuery = true)
    void createIfMissing(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WarehouseStock s where s.productId = :id")
    Optional<WarehouseStock> findForUpdate(@Param("id") UUID id);

    @Query(value = """
            select p.id as "productId", p.sku, p.name_vi as name, p.unit_code as unit, p.image_url as "imageUrl",
                   coalesce(s.on_hand_quantity, 0) as "onHand", coalesce(s.reserved_quantity, 0) as reserved,
                   coalesce(s.available_quantity, 0) as available
            from public.products p left join public.inventory s on p.id = s.product_id
            left join catalog_search.ranked(cast(:keyword as text)) r on r.product_id=p.id
            where p.unit_code <> 'COMBO' and (cast(:keyword as text) is null or r.product_id is not null
               or (p.status <> 'PUBLISHED' and catalog_search.fold_v1(:keyword) <> '' and
                   (catalog_search.fold_v1(p.sku)=catalog_search.fold_v1(:keyword)
                    or to_tsvector('simple',p.catalog_terms) @@ plainto_tsquery('simple',catalog_search.label_v2(:keyword)))))
            order by r.relevance desc nulls last, p.name_vi, p.id
            """, countQuery = """
            select count(*) from public.products p
            left join catalog_search.ranked(cast(:keyword as text)) r on r.product_id=p.id
            where p.unit_code <> 'COMBO' and (cast(:keyword as text) is null or r.product_id is not null
               or (p.status <> 'PUBLISHED' and catalog_search.fold_v1(:keyword) <> '' and
                   (catalog_search.fold_v1(p.sku)=catalog_search.fold_v1(:keyword)
                    or to_tsvector('simple',p.catalog_terms) @@ plainto_tsquery('simple',catalog_search.label_v2(:keyword)))))
            """, nativeQuery = true)
    Page<StockView> search(@Param("keyword") String keyword, Pageable pageable);
}
