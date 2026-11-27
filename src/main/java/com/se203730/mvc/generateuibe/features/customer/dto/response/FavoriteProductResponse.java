package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Favorite product with the same live card data as the storefront. */
public record FavoriteProductResponse(
        UUID productId, String sku, String nameVi, String nameEn,
        String imageUrl, BigDecimal listPrice, Instant favoritedAt,
        String unitCode, BigDecimal salePrice, BigDecimal availableQuantity,
        UUID promotionId, String promotionNameVi, Instant promotionEndsAt,
        UUID categoryId, String categoryNameVi, String categoryNameEn,
        String packageSpecVi, String packageSpecEn, BigDecimal minOrderQuantity,
        BigDecimal quantityStep, BigDecimal vatRate, BigDecimal averageRating, long reviewCount
) {
    public FavoriteProductResponse(HomePageProductResponse product, Instant favoritedAt) {
        this(product.productId(), product.sku(), product.nameVi(), product.nameEn(),
                product.imageUrl(), product.listPrice(), favoritedAt,
                product.unitCode(), product.salePrice(), product.availableQuantity(),
                product.promotionId(), product.promotionNameVi(), product.promotionEndsAt(),
                product.categoryId(), product.categoryNameVi(), product.categoryNameEn(),
                product.packageSpecVi(), product.packageSpecEn(), product.minOrderQuantity(),
                product.quantityStep(), product.vatRate(), product.averageRating(), product.reviewCount());
    }
}
