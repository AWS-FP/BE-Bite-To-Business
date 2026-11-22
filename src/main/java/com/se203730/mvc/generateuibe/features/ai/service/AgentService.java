package com.se203730.mvc.generateuibe.features.ai.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput;
import com.se203730.mvc.generateuibe.features.ai.service.orchestration.GenerativeUiService;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class AgentService {
    private final AccountAccessService accounts;
    private final GenerativeUiService pipeline;
    private final AiRequestLimitService requestLimit;
    private final ThreadPoolTaskExecutor executor;

    public AgentService(AccountAccessService accounts, GenerativeUiService pipeline,
            AiRequestLimitService requestLimit, @Qualifier("agentExecutor") ThreadPoolTaskExecutor executor) {
        this.accounts = accounts;
        this.pipeline = pipeline;
        this.requestLimit = requestLimit;
        this.executor = executor;
    }

    public SseEmitter run(UUID userId, Authentication authentication, RunAgentInput input) {
        if (userId != null) {
            if (!(authentication instanceof JwtAuthenticationToken)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            JwtAuthenticationToken token = (JwtAuthenticationToken) authentication;
            AccountAccessService.requirePasswordLogin(token.getToken());
            if (accounts.requireActiveUser(userId).getRole() != UserRole.CUSTOMER) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
        RunAgentInput.ChatMessage message = input.messages().get(input.messages().size() - 1);
        if (!"user".equals(message.role()) || message.content() == null || message.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        UUID requestId = userId == null ? requestLimit.reserveGuest() : requestLimit.reserve(userId);
        // Pipeline gọi model tối đa 3 lượt (ý định, UI, thử lại UI), mỗi lượt timeout 40 giây.
        SseEmitter emitter = new SseEmitter(150_000L);
        try {
            executor.execute(() -> answer(input, message.content(), emitter));
        } catch (RejectedExecutionException exception) {
            if (userId == null) {
                requestLimit.releaseGuest(requestId);
            } else {
                requestLimit.release(requestId);
            }
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return emitter;
    }

    private void answer(RunAgentInput input, String prompt, SseEmitter emitter) {
        try {
            send(emitter, Map.of("type", "RUN_STARTED", "threadId", input.threadId(), "runId", input.runId()));
            pipeline.run(input, event -> send(emitter, event));
            send(emitter, Map.of("type", "RUN_FINISHED", "threadId", input.threadId(), "runId", input.runId()));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } catch (Exception exception) {
            log.warn("AI pipeline failed: {}", exception.getClass().getSimpleName());
            try {
                send(emitter, Map.of("type", "RUN_ERROR", "code", "AI_UNAVAILABLE",
                        "message", "Trợ lý hiện chưa thể phản hồi. Vui lòng thử lại."));
                emitter.complete();
            } catch (IOException sendException) {
                emitter.completeWithError(sendException);
            }
        }
    }

    private void send(SseEmitter emitter, Map<String, ?> event) throws IOException {
        emitter.send(SseEmitter.event().data(event));
    }
}
