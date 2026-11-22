package com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.request;
import jakarta.validation.constraints.*;
public record ExecutiveReviewDecisionRequest(
    @NotBlank String action,
    @Size(max=2000) String reason
) {}
