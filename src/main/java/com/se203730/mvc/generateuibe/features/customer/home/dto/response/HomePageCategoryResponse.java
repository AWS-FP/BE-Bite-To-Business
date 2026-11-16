package com.se203730.mvc.generateuibe.features.customer.home.dto.response;

import java.util.List;
import java.util.UUID;
public record HomePageCategoryResponse(
 UUID id, UUID parentId, String code, String nameVi, String nameEn,
 String descriptionVi, String descriptionEn, String imageSvg,
 int sortOrder, long productCount, List<HomePageCategoryResponse> children
) {}
