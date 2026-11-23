
package com.se203730.mvc.generateuibe.features.ecommerce.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.Product;
import org.springframework.data.domain.Page;

import java.util.List;

public record ProductPageResponse(
    List<ProductSummaryResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {

    public static ProductPageResponse from(Page<Product> result) {
        return new ProductPageResponse(
            result.getContent()
                .stream()
                .map(ProductSummaryResponse::from)
                .toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }
}
