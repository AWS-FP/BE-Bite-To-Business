package com.se203730.mvc.generateuibe.features.customer.home.dto.response;

import java.util.UUID;
public record HomePageBannerResponse(UUID id,String titleVi,String titleEn,
 String descriptionVi,String descriptionEn,String imageUrl,String targetUrl,int sortOrder) {}
