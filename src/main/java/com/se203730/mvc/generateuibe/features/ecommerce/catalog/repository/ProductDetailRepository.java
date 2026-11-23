
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.ProductDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductDetailRepository
    extends JpaRepository<ProductDetail, UUID> {

    interface NutritionFactRow {
        String getNutrient();

        String getAmount();
    }

    @Query(value = """
        select nutrient as nutrient, amount as amount
        from public.product_nutrition_facts
        where product_id = :productId
        order by sort_order
        """, nativeQuery = true)
    List<NutritionFactRow> findNutritionFacts(@Param("productId") UUID productId);
}
