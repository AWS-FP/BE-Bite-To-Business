package com.se203730.mvc.generateuibe.features.ecommerce.executive.dto.response;
import java.util.List;
public record EcommercePageResponse<T>(List<T> items, long total, int page, int size, int totalPages) {
    public EcommercePageResponse(List<T> items, long total, int page, int size) {
        this(items, total, page, size, (int)((total + size - 1)/size));
    }
}
