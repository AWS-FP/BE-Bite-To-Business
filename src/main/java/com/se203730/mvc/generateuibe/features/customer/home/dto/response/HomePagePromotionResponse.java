package com.se203730.mvc.generateuibe.features.customer.home.dto.response;

import java.time.Instant;
import java.util.UUID;
public record HomePagePromotionResponse(UUID id, String nameVi, String nameEn,
 String contentVi, String contentEn, Instant startsAt, Instant endsAt) {}
