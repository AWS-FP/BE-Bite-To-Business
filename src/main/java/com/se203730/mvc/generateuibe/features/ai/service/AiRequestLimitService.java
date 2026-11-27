package com.se203730.mvc.generateuibe.features.ai.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ai.repository.AiRequestUsageRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giới hạn số lượt AI mỗi giờ của từng Customer để kiểm soát chi phí Bedrock. Đếm trong DB (bền qua nhiều
 * instance); lượt được giữ trước khi gọi model và trả lại nếu không chạy được. AI chỉ dành cho Customer đã
 * đăng nhập nên không còn hạn mức Guest.
 */
@Service
public class AiRequestLimitService {
    private static final int HOURLY_LIMIT = 50;
    private final AiRequestUsageRepository requests;

    public AiRequestLimitService(AiRequestUsageRepository requests) {
        this.requests = requests;
    }

    /** Giữ một lượt cho Customer; đủ 50 lượt trong 1 giờ thì báo AI_RATE_LIMIT_EXCEEDED (429). */
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

    /** Trả lại lượt khi request không chạy được (ví dụ thread pool đầy). */
    @Transactional
    public void release(UUID requestId) {
        requests.deleteById(requestId);
    }
}
