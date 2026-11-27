
package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Collection;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, UUID> {
    interface CheckoutAvailability {
        UUID getProductId();
        BigDecimal getAvailableQuantity();
    }

    @Query(value = """
            select p.id as "productId", coalesce(a.available_quantity,0) as "availableQuantity"
            from public.products p left join checkout_stock.availability a on a.product_id=p.id
            where p.id in (:ids) and p.unit_code='COMBO'
            """, nativeQuery = true)
    List<CheckoutAvailability> comboAvailability(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(value = """
            update public.inventory set reserved_quantity = reserved_quantity + :quantity, updated_at = :now
            where product_id = :productId and available_quantity >= :quantity
            """, nativeQuery = true)
    int reserve(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity,
                @Param("now") Instant now);
    @Modifying
    @Query(value = """
            update public.inventory set reserved_quantity = reserved_quantity - :quantity, updated_at = :now
            where product_id = :productId and reserved_quantity >= :quantity
            """, nativeQuery = true)
    int release(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity,
                @Param("now") Instant now);
    // Giao xong: hàng rời kho, trừ cả tồn thực tế và phần đang giữ.
    @Modifying
    @Query(value = """
            update public.inventory set on_hand_quantity = on_hand_quantity - :quantity,
                reserved_quantity = reserved_quantity - :quantity, updated_at = :now
            where product_id = :productId and reserved_quantity >= :quantity and on_hand_quantity >= :quantity
            """, nativeQuery = true)
    int ship(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity,
             @Param("now") Instant now);

    // Khách trả hàng: nhập lại tồn thực tế.
    @Modifying
    @Query(value = """
            update public.inventory set on_hand_quantity = on_hand_quantity + :quantity, updated_at = :now
            where product_id = :productId
            """, nativeQuery = true)
    int restock(@Param("productId") UUID productId, @Param("quantity") BigDecimal quantity,
                @Param("now") Instant now);
}
