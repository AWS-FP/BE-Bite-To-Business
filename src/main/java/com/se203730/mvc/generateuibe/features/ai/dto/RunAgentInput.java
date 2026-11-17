package com.se203730.mvc.generateuibe.features.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
        List<@NotNull @Valid ChatMessage> messages) {

    public record ChatMessage(
            @NotBlank
            @Size(max = 20)
            String role,
            @Size(max = 4000)
            String content) {
    }
}
