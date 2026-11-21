package com.se203730.mvc.generateuibe.features.ai.dto.response;

/** Gợi ý bản dịch; Staff vẫn phải tự chọn dùng và có thể sửa trước khi lưu. */
public record TranslateResponse(
        String translation,
        String source,
        String target) {
}
