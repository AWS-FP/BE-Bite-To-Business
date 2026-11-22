package com.se203730.mvc.generateuibe.features.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lượt gọi model (intent, UI, trả lời); dùng để đo latency, token và lỗi. */
@Entity
@Table(name = "ai_model_requests", schema = "public")
@Getter
@NoArgsConstructor
public class AiModelRequest {
    @Id
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(nullable = false)
    private String status;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
