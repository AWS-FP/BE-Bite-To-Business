package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivationOtpResendService {
    private final RegistrationRequestRepository registrations;
    private final UserRepository users;
    private final AuthProperties properties;
    private final DatabaseClock clock;

    @Transactional
    public long requestForEmail(String email) {
        users.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() == UserRole.CUSTOMER && !user.isBanned())
                .flatMap(user -> registrations.findByCustomerIdForUpdate(user.getId()))
                .filter(row -> row.getStatus() == RegistrationStatus.AWAITING_ACTIVATION && row.getActivatedAt() == null)
                .ifPresent(this::queueIfDue);
        // Same public response for every Gmail, including addresses with no registration.
        return properties.getOtpResendCooldown().toSeconds();
    }

    Instant availableAt(RegistrationRequest row) {
        Instant attempt = row.getOtpResendRequestedAt();
        if (attempt == null) {
            attempt = "FAILED".equals(row.getOtpDeliveryState()) ? row.getUpdatedAt() : row.getOtpGeneratedAt();
        }
        return attempt == null ? null : attempt.plus(properties.getOtpResendCooldown());
    }

    // Both entry points hold a write lock on this registration until the queue update commits.
    boolean queueIfDue(RegistrationRequest row) {
        if ("QUEUED".equals(row.getOtpDeliveryState())) return true;
        Instant now = clock.now();
        Instant available = availableAt(row);
        if (available != null && now.isBefore(available)) return false;
        row.setOtpSentAt(null);
        row.setOtpResendRequestedAt(now);
        row.setOtpGeneratedAt(null);
        row.setOtpDeliveryState("QUEUED");
        row.setUpdatedAt(now);
        registrations.saveAndFlush(row);
        return true;
    }
}
