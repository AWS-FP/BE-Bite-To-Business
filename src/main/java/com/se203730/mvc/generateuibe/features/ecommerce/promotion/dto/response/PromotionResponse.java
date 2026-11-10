package com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A published, currently active STANDARD promotion for the Home page. */
public record PromotionResponse(
        UUID id,
        String nameVi,
        String nameEn,
        String contentVi,
        String contentEn,
        Instant startsAt,
        Instant endsAt
) {}
