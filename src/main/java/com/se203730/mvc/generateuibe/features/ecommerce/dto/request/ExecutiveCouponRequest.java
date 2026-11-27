package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public record ExecutiveCouponRequest(
    @NotBlank @Pattern(regexp="[A-Za-z0-9]{8}") String code,
    @NotBlank String discountType,
    @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal discountValue,
    @NotNull @DecimalMin("0") BigDecimal minOrderAmount,
    @DecimalMin(value="0",inclusive=false) BigDecimal maxDiscountAmount,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime endsAt,
    @Positive Integer usageLimit,
    @Positive Integer usageLimitPerUser,
    @NotBlank String appliesTo,
    @NotBlank String productScope,
    List<@NotNull UUID> productIds,
    boolean isActive
) {}
