package com.se203730.mvc.generateuibe.common.outbox.client;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.util.Map;

/** Bật bằng app.outbox.sqs.enabled=true; thông tin xác thực lấy từ IAM task role của ECS. */
@Component
@ConditionalOnProperty(name = "app.outbox.sqs.enabled", havingValue = "true")
public class SqsOutboxBrokerClient implements OutboxBrokerClient {
    private final SqsClient client;
    private final String queueUrl;
    private final boolean fifo;

    public SqsOutboxBrokerClient(
            @Value("${app.outbox.aws-region}") String region,
            @Value("${app.outbox.sqs.queue-url}") String queueUrl) {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("app.outbox.sqs.queue-url is required when SQS outbox is enabled");
        }
        this.queueUrl = queueUrl.trim();
        this.fifo = this.queueUrl.endsWith(".fifo");
        this.client = SqsClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
    }

    @Override
    public String destination() {
        return "SQS";
    }

    @Override
    public String publish(OutboxMessage message) {
        SendMessageRequest.Builder request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message.body())
                .messageAttributes(Map.of("eventType", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(message.eventType())
                        .build()));
        if (fifo) {
            // Giữ thứ tự theo aggregate; SQS FIFO tự loại trùng trong 5 phút theo event id.
            request.messageGroupId(message.aggregateId()).messageDeduplicationId(message.eventId());
        }
        return client.sendMessage(request.build()).messageId();
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
