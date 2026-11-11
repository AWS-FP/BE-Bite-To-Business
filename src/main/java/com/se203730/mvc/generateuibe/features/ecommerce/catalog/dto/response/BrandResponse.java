package com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Brand;

import java.util.UUID;

/** Thông tin thương hiệu dùng chung cho danh sách và trang chi tiết. */
public record BrandResponse(
        UUID id,
        String name,
        String descriptionVi,
        String descriptionEn,
        String logoUrl,
        String cloudinaryPublicId
) {
    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescriptionVi(),
                brand.getDescriptionEn(),
                brand.getLogoUrl(),
                brand.getCloudinaryPublicId()
        );
    }
}
