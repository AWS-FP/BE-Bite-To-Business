package com.se203730.mvc.generateuibe.features.ai.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lô sự kiện tương tác FE gửi mỗi 10 giây hoặc khi rời trang. */
public record UiEventBatchRequest(
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9-]*$")
        String anonId,
        @NotEmpty
        @Size(max = 50)
        List<@NotNull @Valid UiEvent> events) {

    public record UiEvent(
            @NotNull
            UUID eventId,
            @NotBlank
            @Pattern(regexp = "page_view|page_leave|quick_back|search|add_to_cart|assistant_prompt|a2ui_render|a2ui_action|a2ui_rejected")
            String type,
            @NotBlank
            @Size(max = 255)
            @Pattern(regexp = "^/.*")
            String route,
            @Size(max = 64)
            String surfaceId,
            @Size(max = 64)
            String component,
            @Size(max = 100)
            String target,
            Map<String, Object> value,
            @NotNull
            Instant occurredAt) {
    }
}
