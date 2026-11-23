package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
public record ExecutivePromotionRequest(
    @NotBlank @Size(max=200) String nameVi,
    @NotBlank @Size(max=200) String nameEn,
    String contentVi,
    String contentEn,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime endsAt,
    String imageUrl,
    String promotionType
) {}
