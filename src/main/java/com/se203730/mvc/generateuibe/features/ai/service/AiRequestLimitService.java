package com.se203730.mvc.generateuibe.features.ai.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ai.repository.AiRequestUsageRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRequestLimitService {
    private static final int HOURLY_LIMIT = 20;
    private final AiRequestUsageRepository requests;

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
}
