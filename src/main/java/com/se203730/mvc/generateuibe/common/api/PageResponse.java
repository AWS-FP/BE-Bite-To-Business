package com.se203730.mvc.generateuibe.common.api;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Phản hồi danh sách có phân trang, giúp frontend biết số trang, tổng bản ghi và dữ liệu của trang hiện tại.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(source.getContent(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
