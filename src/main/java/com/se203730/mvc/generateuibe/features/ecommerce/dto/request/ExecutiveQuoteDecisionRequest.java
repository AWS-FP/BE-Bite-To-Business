package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;
import jakarta.validation.constraints.*;
public record ExecutiveQuoteDecisionRequest(
    @NotBlank String decision,
    @NotNull @Positive Integer expectedRevision,
    @Size(max=2000) String reason
) {}
