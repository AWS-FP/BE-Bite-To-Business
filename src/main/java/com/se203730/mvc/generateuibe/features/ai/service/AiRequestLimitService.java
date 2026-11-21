package com.se203730.mvc.generateuibe.features.ai.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ai.repository.AiRequestUsageRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRequestLimitService {
    private static final int HOURLY_LIMIT = 20;
    private final AiRequestUsageRepository requests;
    private final Map<UUID, Instant> guestRequests = new HashMap<>();

    public AiRequestLimitService(AiRequestUsageRepository requests) {
        this.requests = requests;
    }

    @Transactional
    public UUID reserve(UUID userId) {
        requests.lockUser(userId);
        requests.deleteExpired(userId);
        if (requests.countRecent(userId) >= HOURLY_LIMIT) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
        }
        UUID requestId = UUID.randomUUID();
        requests.recordRequest(requestId, userId);
        return requestId;
    }

    @Transactional
    public void release(UUID requestId) {
        requests.deleteById(requestId);
    }

    public synchronized UUID reserveGuest() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        guestRequests.values().removeIf(time -> !time.isAfter(cutoff));
        if (guestRequests.size() >= HOURLY_LIMIT) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
        }
        UUID requestId = UUID.randomUUID();
        guestRequests.put(requestId, Instant.now());
        return requestId;
    }

    public synchronized void releaseGuest(UUID requestId) {
        guestRequests.remove(requestId);
    }
}
