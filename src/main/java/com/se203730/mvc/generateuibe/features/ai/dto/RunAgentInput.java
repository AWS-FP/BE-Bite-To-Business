package com.se203730.mvc.generateuibe.features.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

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
        AgentState state) {

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
            @Pattern(regexp = "name_asc|name_desc|newest|oldest")
            String lastSort) {
    }
}
