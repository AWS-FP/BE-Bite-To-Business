package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExecutiveComboRequest(
    @NotBlank @Size(max=200) String nameVi,
    @NotBlank @Size(max=200) String nameEn,
    @Size(max=5000) String descriptionVi,
    @Size(max=5000) String descriptionEn,
    @NotNull UUID categoryId,
    @Positive Integer servingCount,
    @NotNull @Positive @Digits(integer=14,fraction=0) BigDecimal listPrice,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal vatRate,
    @Size(max=2048) String imageUrl,
    @Size(max=255) String cloudinaryPublicId,
    @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items,
    @Valid Details details
) {
    public record Details(
        @Positive @Digits(integer=9,fraction=3) BigDecimal netWeightKg,
        @DecimalMin("0") @Digits(integer=9,fraction=3) BigDecimal tareWeightKg,
        @Positive @Digits(integer=12,fraction=2) BigDecimal volumeCm3,
        @Positive @Digits(integer=8,fraction=2) BigDecimal lengthCm,
        @Positive @Digits(integer=8,fraction=2) BigDecimal widthCm,
        @Positive @Digits(integer=8,fraction=2) BigDecimal heightCm,
        @Size(max=2000) String technicalContentVi,
        @Size(max=2000) String technicalContentEn,
        @Size(max=2000) String kitchenStorageContentVi,
        @Size(max=2000) String kitchenStorageContentEn,
        @Size(max=2000) String originContentVi,
        @Size(max=2000) String originContentEn
    ) {}
    public record Item(@NotNull UUID productId,
                       @NotNull @Positive @Digits(integer=9,fraction=3) BigDecimal quantity) {}
}
