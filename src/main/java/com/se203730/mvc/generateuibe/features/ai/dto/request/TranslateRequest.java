package com.se203730.mvc.generateuibe.features.ai.dto.request;

import com.se203730.mvc.generateuibe.features.ai.enums.TranslationField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Văn bản ở ngôn ngữ nguồn; đích luôn là ngôn ngữ còn lại (vi ↔ en). */
public record TranslateRequest(
        @NotBlank
        @Size(max = 2000)
        String text,
        @NotNull
        @Pattern(regexp = "vi|en")
        String source,
        @NotNull
        TranslationField field) {
}
