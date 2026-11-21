package com.se203730.mvc.generateuibe.common.outbox.client;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.time.Duration;

/** Bật bằng app.outbox.eventbridge.enabled=true; rule trên bus định tuyến tới SQS/Lambda theo detail-type. */
@Component
@ConditionalOnProperty(name = "app.outbox.eventbridge.enabled", havingValue = "true")
public class EventBridgeOutboxBrokerClient implements OutboxBrokerClient {
    private final EventBridgeClient client;
    private final String eventBusName;
    private final String source;

    public EventBridgeOutboxBrokerClient(
            @Value("${app.outbox.aws-region}") String region,
            @Value("${app.outbox.eventbridge.bus-name}") String eventBusName,
            @Value("${app.outbox.eventbridge.source}") String source) {
        this.eventBusName = eventBusName.trim();
        this.source = source.trim();
        this.client = EventBridgeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .build())
                .build();
    }

    @Override
    public String destination() {
        return "EVENTBRIDGE";
    }

    @Override
    public String publish(OutboxMessage message) {
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                .eventBusName(eventBusName)
                .source(source)
                .detailType(message.eventType())
                .detail(message.body())
                .build();
        PutEventsResponse response = client.putEvents(request -> request.entries(entry));
        PutEventsResultEntry result = response.entries().get(0);
        if (response.failedEntryCount() > 0 || result.eventId() == null) {
            throw new IllegalStateException("EventBridge rejected event: " + result.errorCode());
        }
        return result.eventId();
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
