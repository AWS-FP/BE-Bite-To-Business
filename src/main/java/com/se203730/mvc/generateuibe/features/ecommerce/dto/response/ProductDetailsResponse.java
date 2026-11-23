package com.se203730.mvc.generateuibe.features.ecommerce.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductDetail;

import java.math.BigDecimal;
import java.util.List;

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
        String originContentEn,
        String nutritionBasis,
        List<NutritionFact> nutritionFacts
) {
    /** Một dòng của bảng thông tin dinh dưỡng: thành phần và hàm lượng. */
    public record NutritionFact(String nutrient, String amount) {}

    public static ProductDetailsResponse from(
            ProductDetail details,
            List<NutritionFact> nutritionFacts
    ) {
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
                details.getOriginContentEn(),
                details.getNutritionBasis(),
                List.copyOf(nutritionFacts)
        );
    }
}
