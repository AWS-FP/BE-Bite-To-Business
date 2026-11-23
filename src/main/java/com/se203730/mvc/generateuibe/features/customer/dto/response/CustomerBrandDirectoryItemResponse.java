package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.Brand;
import java.util.List;
import java.util.UUID;

/** Public storefront directory item. Counts and categories come from published products only. */
public record CustomerBrandDirectoryItemResponse(
    UUID id,
    String name,
    String descriptionVi,
    String descriptionEn,
    String logoUrl,
    long productCount,
    List<UUID> categoryIds
) {
    public static CustomerBrandDirectoryItemResponse from(Brand brand, long productCount, List<UUID> categories) {
        return new CustomerBrandDirectoryItemResponse(
            brand.getId(), brand.getName(), brand.getDescriptionVi(), brand.getDescriptionEn(),
            brand.getLogoUrl(), productCount, List.copyOf(categories)
        );
    }
}
