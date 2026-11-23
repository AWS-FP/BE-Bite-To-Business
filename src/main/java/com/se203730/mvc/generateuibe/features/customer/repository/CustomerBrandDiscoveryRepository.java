package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.Brand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/** Read-only storefront discovery queries. No changes to the Ecommerce catalog module. */
public interface CustomerBrandDiscoveryRepository extends Repository<Brand, UUID> {

    @Query("""
        select p.brandId, p.categoryId, count(p.id)
        from Product p
        where p.status = 'PUBLISHED' and p.brandId is not null
        group by p.brandId, p.categoryId
        """)
    List<Object[]> findPublishedCountsByBrandAndCategory();

    @Query(value = """
        select b.*
        from public.brands b
        join (
            select p.brand_id, count(distinct p.category_id) as shared_categories
            from public.products p
            where p.status = 'PUBLISHED'
              and p.brand_id is not null
              and p.brand_id <> :brandId
              and p.category_id in (
                  select distinct source.category_id
                  from public.products source
                  where source.brand_id = :brandId and source.status = 'PUBLISHED'
              )
            group by p.brand_id
        ) shared on shared.brand_id = b.id
        order by shared.shared_categories desc, lower(b.name) asc, b.id asc
        """, nativeQuery = true)
    List<Brand> findRelatedPublishedBrands(@Param("brandId") UUID brandId, Pageable pageable);
}
