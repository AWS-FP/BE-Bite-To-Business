package com.se203730.mvc.generateuibe.features.admin.dto.response;

public record RegistrationStatsResponse(
        long total,
        long pending,
        long approved,
        long rejected
) {
}
