package com.se203730.mvc.generateuibe.common.outbox.service;

import com.se203730.mvc.generateuibe.common.outbox.client.OutboxBrokerClient;
import com.se203730.mvc.generateuibe.common.outbox.client.OutboxBrokerClient.OutboxMessage;
import com.se203730.mvc.generateuibe.common.outbox.repository.OutboxEventRepository;
import com.se203730.mvc.generateuibe.common.outbox.repository.OutboxEventRepository.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Relay đọc outbox_events và đẩy lên SQS/EventBridge. Chỉ giữ kết nối DB lúc nhận lô và lúc đánh dấu,
 * không giữ trong lúc gọi AWS. Nhiều task ECS chạy cùng lúc vẫn an toàn nhờ FOR UPDATE SKIP LOCKED.
 * Tắt mặc định; bật bằng app.outbox.relay.enabled=true khi đã có SQS/EventBridge.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.outbox.relay.enabled", havingValue = "true")
public class OutboxRelayService {
    private final OutboxEventRepository events;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Map<String, OutboxBrokerClient> clients;

    @Value("${app.outbox.relay.batch-size:20}")
    private int batchSize;
    @Value("${app.outbox.relay.stale-claim-seconds:300}")
    private int staleClaimSeconds;
    @Value("${app.outbox.retention-days:14}")
    private int retentionDays;

    public OutboxRelayService(OutboxEventRepository events, TransactionTemplate transactions,
            ObjectMapper objectMapper, List<OutboxBrokerClient> clients) {
        this.events = events;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clients = clients.stream()
                .collect(Collectors.toUnmodifiableMap(OutboxBrokerClient::destination, Function.identity()));
        if (this.clients.isEmpty()) {
            log.warn("Outbox relay is enabled but no SQS/EventBridge client is enabled; events will be retried");
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.poll-ms:2000}")
    public void publishPending() {
        UUID claimId = UUID.randomUUID();
        List<Event> batch = transactions.execute(status -> events.claimBatch(claimId, batchSize, staleClaimSeconds));
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (Event event : batch) {
            publish(event, claimId);
        }
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void cleanup() {
        int outbox = events.deletePublishedBefore(retentionDays);
        int processed = events.deleteProcessedBefore(retentionDays * 2);
        log.info("Outbox cleanup removed {} published events and {} processed markers", outbox, processed);
    }

    private void publish(Event event, UUID claimId) {
        OutboxBrokerClient client = clients.get(event.destination());
        try {
            if (client == null) {
                throw new IllegalStateException("No broker client for destination " + event.destination());
            }
            String messageId = client.publish(new OutboxMessage(event.id().toString(), event.eventType(),
                    event.aggregateId().toString(), envelope(event)));
            events.markPublished(event.id(), claimId, messageId);
        } catch (RuntimeException exception) {
            long delay = Math.min(3600L, 15L << Math.min(event.attempts(), 8));
            events.retryLater(event.id(), claimId, delay, exception.getClass().getSimpleName() + ": "
                    + String.valueOf(exception.getMessage()));
            log.warn("Outbox publish failed; id={}, type={}, attempt={}, error={}", event.id(), event.eventType(),
                    event.attempts(), exception.getClass().getSimpleName());
        }
    }

    /** Envelope chung cho mọi consumer; eventId dùng làm khóa chống trùng trong processed_events. */
    private String envelope(Event event) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", event.id().toString());
        root.put("eventType", event.eventType());
        root.put("eventVersion", event.eventVersion());
        root.put("aggregateType", event.aggregateType());
        root.put("aggregateId", event.aggregateId().toString());
        root.put("occurredAt", event.occurredAt() == null ? null : event.occurredAt().toString());
        root.set("payload", objectMapper.readTree(event.payloadJson()));
        return objectMapper.writeValueAsString(root);
    }
}
