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
            select s.* from public.inventory s join public.products p on p.id = s.product_id
            where :keyword is null or lower(p.name_vi) like lower(concat('%', :keyword, '%'))
                or lower(p.sku) like lower(concat('%', :keyword, '%'))
            order by p.name_vi, p.id
            """, countQuery = """
            select count(*) from public.inventory s join public.products p on p.id = s.product_id
            where :keyword is null or lower(p.name_vi) like lower(concat('%', :keyword, '%'))
                or lower(p.sku) like lower(concat('%', :keyword, '%'))
            """, nativeQuery = true)
    Page<WarehouseStock> search(@Param("keyword") String keyword, Pageable pageable);
}
