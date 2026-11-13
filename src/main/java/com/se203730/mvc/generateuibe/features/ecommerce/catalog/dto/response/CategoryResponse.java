
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Category;

import java.util.List;
import java.util.UUID;

/**
 * DTO trả về thông tin danh mục cho FE.
 */
public record CategoryResponse(
    UUID id,
    UUID parentId,
    String code,
    String nameVi,
    String nameEn,
    String descriptionVi,
    String descriptionEn,
    String imageSvg,
    int sortOrder,
    List<CategoryResponse> children
) {

    public static CategoryResponse from(
        Category category,
        List<CategoryResponse> children
    ) {
        return new CategoryResponse(
            category.getId(),
            category.getParentId(),
            category.getCode(),
            category.getNameVi(),
            category.getNameEn(),
            category.getDescriptionVi(),
            category.getDescriptionEn(),
            category.getImageSvg(),
            category.getSortOrder(),
            List.copyOf(children)
        );
    }
}
