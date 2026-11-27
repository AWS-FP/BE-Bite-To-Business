package com.se203730.mvc.generateuibe.features.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record RunAgentInput(
        @NotBlank
        @Size(max = 100)
        String threadId,
        @NotBlank
        @Size(max = 100)
        String runId,
        @NotEmpty
        @Size(max = 20)
        List<@NotNull @Valid ChatMessage> messages,
        @Valid
        AgentState state,
        /** chat (mặc định): trả lời kèm khối nhỏ; page: AI ghép cả trang thay trang mặc định. */
        @Pattern(regexp = "chat|page")
        String mode,
        @Valid StablePage stablePage) {

    public RunAgentInput(String threadId, String runId, List<ChatMessage> messages, AgentState state, String mode) {
        this(threadId, runId, messages, state, mode, null);
    }

    /** Client layout is untrusted: backend validates it and reloads all product facts/prices by public ID. */
    public record StablePage(@NotNull JsonNode layout,
            @NotEmpty @Size(max = 16) Map<String, @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String>> lists,
            @NotBlank @Pattern(regexp = "vi|en") String locale) {}

    public record ChatMessage(
            @NotBlank
            @Size(max = 20)
            String role,
            @Size(max = 4000)
            String content) {
    }

    /**
     * Trạng thái hiển thị FE gửi lại sau mỗi lượt (lấy từ event STATE_SNAPSHOT). Chỉ là gợi ý bố cục;
     * backend không dùng để quyết định quyền, giá hay tồn kho.
     */
    public record AgentState(
            @Pattern(regexp = "vi|en")
            String locale,
            @Pattern(regexp = "grid|table")
            String view,
            @Pattern(regexp = "compact|comfortable")
            String density,
            @Size(max = 100)
            String lastQuery,
            @Pattern(regexp = "relevance|name_asc|name_desc|newest|oldest|price_asc|most_reviewed")
            String lastSort,
            /**
             * ID ẩn danh của trình duyệt (cùng giá trị FE gửi kèm /api/ui-events) để Guest cũng có hồ sơ thói quen.
             * Chỉ dùng để đọc sự kiện của chính trình duyệt đó; không phải danh tính hay quyền.
             */
            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9-]*$")
            String anonId,
            Boolean personalizationEnabled) {
    }
}
