package com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Brand;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Category;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Product;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.ProductDetail;

import java.math.BigDecimal;
import java.util.UUID;

/** JSON cho trang chi tiết sản phẩm (không chứa dữ liệu quản trị). */
public record ProductDetailResponse(
        UUID id,
        UUID brandId,
        String brandName,
        String brandLogoUrl,
        UUID categoryId,
        String categoryNameVi,
        String categoryNameEn,
        String sku,
        String nameVi,
        String nameEn,
        String descriptionVi,
        String descriptionEn,
        String note,
        String origin,
        String packageSpecVi,
        String packageSpecEn,
        String unitCode,
        BigDecimal minOrderQuantity,
        BigDecimal quantityStep,
        String imageUrl,
        String cloudinaryPublicId,
        ProductDetailsResponse details
) {
    public static ProductDetailResponse from(
            Product product,
            Brand brand,
            Category category,
            ProductDetail details
    ) {
        return new ProductDetailResponse(
                product.getId(),
                product.getBrandId(),
                brand == null ? null : brand.getName(),
                brand == null ? null : brand.getLogoUrl(),
                product.getCategoryId(),
                category == null ? null : category.getNameVi(),
                category == null ? null : category.getNameEn(),
                product.getSku(),
                product.getNameVi(),
                product.getNameEn(),
                product.getDescriptionVi(),
                product.getDescriptionEn(),
                product.getNote(),
                product.getOrigin(),
                product.getPackageSpecVi(),
                product.getPackageSpecEn(),
                product.getUnitCode(),
                product.getMinOrderQuantity(),
                product.getQuantityStep(),
                product.getImageUrl(),
                product.getCloudinaryPublicId(),
                details == null ? null : ProductDetailsResponse.from(details)
        );
    }
}
