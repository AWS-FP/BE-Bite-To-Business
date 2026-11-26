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
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Cửa vào của pipeline AI cho SSE:
 * <ol>
 *   <li>Chỉ Customer đã đăng nhập bằng mật khẩu, tài khoản đang hoạt động (AI cần ý định và thói quen gắn với
 *       đúng khách). Guest nhận 401 để FE chuyển sang trang đăng nhập; nhân viên/Admin nhận 403.</li>
 *   <li>Kiểm tra tin nhắn cuối là của user và không rỗng.</li>
 *   <li>Giữ một lượt trong hạn mức (AiRequestLimitService), rồi chạy GenerativeUiService trên thread pool riêng.</li>
 *   <li>Bọc kết quả thành sự kiện AG-UI: RUN_STARTED … RUN_FINISHED; lỗi giữa chừng thành RUN_ERROR
 *       (FE vẫn có trang mặc định).</li>
 * </ol>
 */
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
        if (userId == null || !(authentication instanceof JwtAuthenticationToken token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        AccountAccessService.requirePasswordLogin(token.getToken());
        if (accounts.requireActiveUser(userId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        RunAgentInput.ChatMessage message = input.messages().get(input.messages().size() - 1);
        if (!"user".equals(message.role()) || message.content() == null || message.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        UUID requestId = requestLimit.reserve(userId);
        // Pipeline gọi model tối đa 3 lượt (ý định, UI, thử lại UI), mỗi lượt timeout 40 giây.
        SseEmitter emitter = new SseEmitter(150_000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        try {
            executor.execute(() -> answer(input, userId, emitter, closed));
        } catch (RejectedExecutionException exception) {
            requestLimit.release(requestId);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return emitter;
    }

    private void answer(RunAgentInput input, UUID customerId, SseEmitter emitter, AtomicBoolean closed) {
        try {
            send(emitter, closed, Map.of("type", "RUN_STARTED", "threadId", input.threadId(), "runId", input.runId()));
            pipeline.run(input, customerId, event -> send(emitter, closed, event));
            send(emitter, closed, Map.of("type", "RUN_FINISHED", "threadId", input.threadId(), "runId", input.runId()));
            if (closed.compareAndSet(false, true)) emitter.complete();
        } catch (IOException exception) {
            closed.set(true);
            log.debug("AI stream closed before the response completed");
        } catch (Exception exception) {
            if (closed.get()) {
                log.debug("AI stream closed before the pipeline completed");
                return;
            }
            log.warn("AI pipeline failed: {}", exception.getClass().getSimpleName());
            try {
                send(emitter, closed, Map.of("type", "RUN_ERROR", "code", "AI_UNAVAILABLE",
                        "message", "Trợ lý hiện chưa thể phản hồi. Vui lòng thử lại."));
                if (closed.compareAndSet(false, true)) emitter.complete();
            } catch (IOException sendException) {
                closed.set(true);
                log.debug("AI stream closed before the error could be sent");
            }
        }
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, Map<String, ?> event) throws IOException {
        if (closed.get()) throw new IOException("AI stream is closed");
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException || exception.getMessage() != null
                    && exception.getMessage().startsWith("ResponseBodyEmitter has already completed")) {
                closed.set(true);
                throw new IOException("AI stream is closed", exception);
            }
            throw exception;
        }
    }
}
