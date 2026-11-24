package com.se203730.mvc.generateuibe.features.ai.service;

import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ai.dto.request.UiEventBatchRequest;
import com.se203730.mvc.generateuibe.features.ai.dto.request.UiEventBatchRequest.UiEvent;
import com.se203730.mvc.generateuibe.features.ai.repository.UiInteractionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Lưu tín hiệu tương tác cho cá nhân hóa giao diện. Chỉ ghi cho Guest (anonId) và Customer;
 * tài khoản nhân viên bị bỏ qua. Không bao giờ làm lỗi thao tác của người dùng: dữ liệu sai thì bỏ.
 */
@Service
@Slf4j
public class UiEventService {
    private static final int MAX_VALUE_LENGTH = 2_000;
    private static final Duration MAX_AGE = Duration.ofDays(1);
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration RETENTION = Duration.ofDays(90);

    private final UiInteractionEventRepository events;
    private final AccountAccessService accounts;
    private final ObjectMapper mapper;

    public UiEventService(UiInteractionEventRepository events, AccountAccessService accounts, ObjectMapper mapper) {
        this.events = events;
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @Transactional
    public int record(UUID userId, UiEventBatchRequest request) {
        String anonId = request.anonId() == null || request.anonId().isBlank() ? null : request.anonId();
        if (userId != null && accounts.requireActiveUser(userId).getRole() != UserRole.CUSTOMER) {
            return 0;
        }
        if (userId == null && anonId == null) {
            return 0;
        }
        Instant now = Instant.now();
        int saved = 0;
        for (UiEvent event : request.events()) {
            if (event.occurredAt().isBefore(now.minus(MAX_AGE)) || event.occurredAt().isAfter(now.plus(MAX_CLOCK_SKEW))) {
                continue;
            }
            String value = json(event.value());
            if (value == null) {
                continue;
            }
            saved += events.insertIgnoringDuplicate(event.eventId(), userId, userId == null ? anonId : null,
                    event.type(), event.route(), event.surfaceId(), event.component(), event.target(), value,
                    event.occurredAt());
        }
        return saved;
    }

    /** Dọn sự kiện quá 90 ngày, chạy mỗi ngày lúc 3 giờ sáng. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOld() {
        int removed = events.deleteReceivedBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Removed {} old UI interaction events", removed);
        }
    }

    private String json(Map<String, Object> value) {
        String text = mapper.writeValueAsString(value == null ? Map.of() : value);
        return text.length() > MAX_VALUE_LENGTH ? null : text;
    }
}
