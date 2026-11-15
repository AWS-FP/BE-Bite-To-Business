package com.se203730.mvc.generateuibe.features.customer.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, String notificationType, String titleVi, String titleEn,
        String contentVi, String contentEn, String actionUrl,
        boolean isRead, Instant readAt, Instant createdAt
) {}
