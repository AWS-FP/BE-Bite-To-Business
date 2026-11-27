package com.se203730.mvc.generateuibe.features.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lượt dùng trợ lý AI của Customer, để giới hạn 50 lượt/giờ (AiRequestLimitService). */
@Entity
@Table(name = "ai_request_usage", schema = "public")
@Getter
@NoArgsConstructor
public class AiRequestUsage {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
}
