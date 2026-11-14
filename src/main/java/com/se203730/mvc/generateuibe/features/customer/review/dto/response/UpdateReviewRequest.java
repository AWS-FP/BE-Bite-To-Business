package com.se203730.mvc.generateuibe.features.customer.review.dto.response;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edits rating/text; photo additions and removals have separate endpoints. */
public record UpdateReviewRequest(
        @NotNull @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
        @Size(max = 2000) String content
) {}
