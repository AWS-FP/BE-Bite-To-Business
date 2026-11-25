package com.se203730.mvc.generateuibe.features.ai.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput;
import com.se203730.mvc.generateuibe.features.ai.service.AgentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoint Generative UI: {@code POST /api/ai/agent} trả SSE theo sự kiện AG-UI.
 * Body là {@link com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput}: lịch sử hội thoại,
 * state hiển thị và mode ("chat" khối nhỏ / "page" cả trang). Chỉ Customer đã đăng nhập dùng được AI
 * (Guest nhận 401 và FE chuyển sang trang đăng nhập);
 * Customer được nhận diện từ cookie/JWT; kiểm tra role và giới hạn lượt nằm ở AgentService.
 */
@RestController
@RequestMapping("/api/ai")
public class AgentController {
    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/agent", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(
            @CurrentUserUUID(required = false)
            UUID userId,
            Authentication authentication,
            @Valid
            @RequestBody
            RunAgentInput input) {
        return agent.run(userId, authentication, input);
    }
}
