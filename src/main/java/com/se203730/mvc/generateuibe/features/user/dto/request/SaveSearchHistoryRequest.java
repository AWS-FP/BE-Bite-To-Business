package com.se203730.mvc.generateuibe.features.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FE chỉ gửi từ khóa; userId và thời gian do backend quyết định. */
public record SaveSearchHistoryRequest(
        @NotBlank
        @Size(max = 160)
        String keyword
) {
    public SaveSearchHistoryRequest {
        if (keyword != null) {
            // Gộp các khoảng trắng, kể cả xuống dòng, thành một dấu cách.
            keyword = keyword.replaceAll("(?U)\\s+", " ").trim();
        }
    }
}
