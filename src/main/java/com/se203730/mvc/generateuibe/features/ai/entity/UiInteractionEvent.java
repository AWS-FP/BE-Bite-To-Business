package com.se203730.mvc.generateuibe.features.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một sự kiện tương tác storefront/A2UI dùng để tổng hợp thói quen giao diện của khách. */
@Entity
@Table(name = "ui_interaction_events", schema = "public")
@Getter
@NoArgsConstructor
public class UiInteractionEvent {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "anon_id")
    private String anonId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String route;

    @Column(name = "surface_id")
    private String surfaceId;

    private String component;

    private String target;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
