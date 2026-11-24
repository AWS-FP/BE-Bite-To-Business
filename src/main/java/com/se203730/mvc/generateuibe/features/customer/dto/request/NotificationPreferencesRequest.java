package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferencesRequest(
        @NotNull Boolean quoteApproval,
        @NotNull Boolean quoteExpiry,
        @NotNull Boolean deliveryEta,
        @NotNull Boolean sourcingReply) {}
