package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class QuoteNotification {
    @Id
    private UUID id;
    private UUID recipientId;
    private String notificationType;
    private String titleVi;
    private String titleEn;
    private String contentVi;
    private String contentEn;
    private String actionUrl;
    private Instant createdAt;
}
