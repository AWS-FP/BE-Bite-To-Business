package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivationOtpResendServiceTest {
    private final RegistrationRequestRepository registrations = mock(RegistrationRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DatabaseClock clock = mock(DatabaseClock.class);
    private final AuthProperties properties = new AuthProperties();
    private final ActivationOtpResendService service = new ActivationOtpResendService(registrations, users, properties, clock);
    private final String email = "customer@gmail.com";

    private RegistrationRequest customerRegistration() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.CUSTOMER);
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        RegistrationRequest row = new RegistrationRequest();
        when(registrations.findByCustomerIdForUpdate(user.getId())).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void adminAndCustomerShareCooldownAndOnlyQueueOnce() {
        properties.setOtpResendCooldown(Duration.ofSeconds(75));
        Instant sent = Instant.parse("2026-01-01T00:00:00Z");
        var row = customerRegistration();
        row.setStatus(RegistrationStatus.AWAITING_ACTIVATION);
        row.setOtpDeliveryState("SENT");
        row.setOtpGeneratedAt(sent);
        when(clock.now()).thenReturn(sent.plusSeconds(74), sent.plusSeconds(75));
        assertEquals(75, service.requestForEmail(email));
        assertEquals("SENT", row.getOtpDeliveryState());
        service.requestForEmail(email);
        assertTrue(service.queueIfDue(row));
        assertEquals("QUEUED", row.getOtpDeliveryState());
        assertEquals(RegistrationStatus.AWAITING_ACTIVATION, row.getStatus());
        verify(registrations, times(1)).saveAndFlush(row);
    }

    @Test
    void unknownPendingRejectedAndActivatedAccountsDoNotQueueMail() {
        long cooldown = service.requestForEmail(email);
        var row = customerRegistration();
        for (var status : new RegistrationStatus[]{RegistrationStatus.PENDING, RegistrationStatus.REJECTED, RegistrationStatus.APPROVED}) {
            row.setStatus(status);
            assertEquals(cooldown, service.requestForEmail(email));
        }
        verify(registrations, never()).saveAndFlush(any());
    }
}
