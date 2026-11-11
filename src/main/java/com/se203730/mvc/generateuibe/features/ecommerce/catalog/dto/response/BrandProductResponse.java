package com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Product;

import java.math.BigDecimal;
import java.util.UUID;

/** Sản phẩm công khai thuộc một thương hiệu; giá và tồn kho nằm ở API riêng. */
public record BrandProductResponse(
        UUID id,
        UUID brandId,
        UUID categoryId,
        String sku,
        String nameVi,
        String nameEn,
        String descriptionVi,
        String descriptionEn,
        String origin,
        String packageSpecVi,
        String packageSpecEn,
        String unitCode,
        BigDecimal minOrderQuantity,
        BigDecimal quantityStep,
        String imageUrl,
        String cloudinaryPublicId
) {
    public static BrandProductResponse from(Product product) {
        return new BrandProductResponse(
                product.getId(),
                product.getBrandId(),
                product.getCategoryId(),
                product.getSku(),
                product.getNameVi(),
                product.getNameEn(),
                product.getDescriptionVi(),
                product.getDescriptionEn(),
                product.getOrigin(),
                product.getPackageSpecVi(),
                product.getPackageSpecEn(),
                product.getUnitCode(),
                product.getMinOrderQuantity(),
                product.getQuantityStep(),
                product.getImageUrl(),
                product.getCloudinaryPublicId()
        );
    }
}
