package com.se203730.mvc.generateuibe.features.logistics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateWarehouseProductRequest(
        @NotBlank @Size(max = 200) String nameVi,
        @NotBlank @Size(max = 200) String nameEn,
        @NotBlank @Size(max = 60) String sku,
        @NotNull UUID categoryId,
        @NotNull UUID brandId,
        @NotNull UUID distributorId,
        @NotBlank @Size(max = 150) String origin,
        @NotBlank String descriptionVi, @NotBlank String descriptionEn, String note,
        // Bước "Chi tiết sản phẩm": mọi thông tin đều bắt buộc.
        @NotBlank @Size(max = 200) String specVi, @NotBlank @Size(max = 200) String specEn,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal netWeight,
        @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal tareWeight,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 6) BigDecimal volume,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal length,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal width,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal height,
        @NotBlank String technicalVi, @NotBlank String technicalEn,
        @NotBlank String storageVi, @NotBlank String storageEn,
        @NotBlank @Pattern(regexp = "KG|BOTTLE|PIECE|PACK|TRAY|BOX|BAG|CAN|TIN|TUB|CASE") String unit,
        @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 0) BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vat,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal minOrder,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantityStep,
        @NotNull @Size(max = 100) List<@NotNull @Valid Tier> tiers,
        // Bảng thông tin dinh dưỡng (bắt buộc): lượng tham chiếu, ví dụ "100g", và các dòng thành phần/hàm lượng.
        @NotBlank @Size(max = 100) String nutritionBasis,
        @NotEmpty @Size(max = 40) List<@NotNull @Valid NutritionFact> nutritionFacts
) {
    public record Tier(
            @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal min,
            @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal max,
            @NotNull @DecimalMin("0") @Digits(integer = 14, fraction = 0) BigDecimal price
    ) {}

    // nutrient = tên tiếng Việt, nutrientEn = tên tiếng Anh; hàm lượng dùng chung cho hai ngôn ngữ.
    public record NutritionFact(
            @NotBlank @Size(max = 100) String nutrient,
            @NotBlank @Size(max = 100) String nutrientEn,
            @NotBlank @Size(max = 50) String amount
    ) {}

    public List<NutritionFact> nutritionFactsOrEmpty() {
        return nutritionFacts == null ? List.of() : nutritionFacts;
    }

    public String trimmedNutritionBasis() {
        return nutritionBasis == null || nutritionBasis.isBlank() ? null : nutritionBasis.strip();
    }
}
