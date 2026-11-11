package com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.ProductDetail;

import java.math.BigDecimal;

/** Dữ liệu tùy chọn từ product_details; không liên quan đến giá bán hay số VAT thực. */
public record ProductDetailsResponse(
        BigDecimal netWeightKg,
        BigDecimal tareWeightKg,
        BigDecimal volumeM3,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String technicalContentVi,
        String technicalContentEn,
        String kitchenStorageContentVi,
        String kitchenStorageContentEn,
        String complianceVatContentVi,
        String complianceVatContentEn,
        String originContentVi,
        String originContentEn
) {
    public static ProductDetailsResponse from(ProductDetail details) {
        return new ProductDetailsResponse(
                details.getNetWeightKg(),
                details.getTareWeightKg(),
                details.getVolumeM3(),
                details.getLengthCm(),
                details.getWidthCm(),
                details.getHeightCm(),
                details.getTechnicalContentVi(),
                details.getTechnicalContentEn(),
                details.getKitchenStorageContentVi(),
                details.getKitchenStorageContentEn(),
                details.getComplianceVatContentVi(),
                details.getComplianceVatContentEn(),
                details.getOriginContentVi(),
                details.getOriginContentEn()
        );
    }
}
