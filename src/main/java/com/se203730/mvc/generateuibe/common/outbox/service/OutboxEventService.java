package com.se203730.mvc.generateuibe.common.outbox.service;

import com.se203730.mvc.generateuibe.common.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Ghi event vào transactional outbox. Phải gọi bên trong transaction nghiệp vụ để event
 * chỉ tồn tại khi thay đổi nghiệp vụ đã commit.
 * Payload chỉ chứa ID và giá trị cần thiết; consumer tự đọc email/tên từ DB, không đưa dữ liệu mật vào event.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventService {
    private final OutboxEventRepository events;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.enabled:false}")
    private boolean enabled;
    @Value("${app.outbox.default-destination:SQS}")
    private String defaultDestination;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload) {
        record(eventType, aggregateType, aggregateId, payload, eventType + ":" + aggregateId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload,
            String idempotencyKey) {
        if (!enabled) {
            return;
        }
        String json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        events.append(eventType, aggregateType, aggregateId, json, idempotencyKey,
                defaultDestination.trim().toUpperCase(Locale.ROOT));
    }
}
