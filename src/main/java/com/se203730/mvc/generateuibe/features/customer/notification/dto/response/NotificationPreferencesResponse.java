package com.se203730.mvc.generateuibe.features.customer.notification.dto.response;

public record NotificationPreferencesResponse(
        boolean quoteApproval, boolean quoteExpiry, boolean deliveryEta, boolean sourcingReply) {
    public static NotificationPreferencesResponse defaults() {
        return new NotificationPreferencesResponse(true, true, true, true);
    }
}
