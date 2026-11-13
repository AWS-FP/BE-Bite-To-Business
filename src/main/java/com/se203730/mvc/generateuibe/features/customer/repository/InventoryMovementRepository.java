package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    interface Balance {
        UUID getProductId();
        BigDecimal getReserved();
        BigDecimal getReleased();
        BigDecimal getShipped();
    }

    @Query(value = """
            select product_id as "productId",
                sum(case when movement_type = 'RESERVE' then quantity else 0 end) as reserved,
                sum(case when movement_type = 'RELEASE' then quantity else 0 end) as released,
                sum(case when movement_type = 'SHIP' then quantity else 0 end) as shipped
            from public.inventory_movements
            where order_id = :orderId and movement_type in ('RESERVE', 'RELEASE', 'SHIP')
            group by product_id order by product_id
            """, nativeQuery = true)
    List<Balance> findBalances(@Param("orderId") UUID orderId);
}
