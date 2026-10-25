package com.se203730.mvc.generateuibe.features.admin.registration.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.auth.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.auth.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.auth.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.user.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationServiceTest {
    @Mock RegistrationRequestRepository registrations;
    @Mock AccountAccessService accounts;
    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;
    @Spy AuthProperties properties = new AuthProperties();
    @InjectMocks AdminRegistrationService service;

    @Test
    void statsUseDatabaseCountsAndRequireAdmin() {
        UUID adminId = UUID.randomUUID();
        when(registrations.countByStatus(RegistrationStatus.PENDING)).thenReturn(12L);
        when(registrations.countByStatus(RegistrationStatus.APPROVED)).thenReturn(8L);
        when(registrations.countByStatus(RegistrationStatus.REJECTED)).thenReturn(4L);
        var stats = service.stats(adminId);
        verify(accounts).requireAdmin(adminId);
        assertThat(stats.total()).isEqualTo(24);
        assertThat(stats.pending()).isEqualTo(12);
        assertThat(stats.approved()).isEqualTo(8);
        assertThat(stats.rejected()).isEqualTo(4);
    }

    @Test
    void unauthorizedCallerCannotReadStatistics() {
        UUID callerId = UUID.randomUUID();
        doThrow(new BusinessException(ErrorCode.ACCESS_DENIED)).when(accounts).requireAdmin(callerId);
        assertThatThrownBy(() -> service.stats(callerId)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(registrations);
    }

    @Test
    void exposesConfiguredOtpCooldownWithoutReturningAnyOtpOrToken() {
        UUID adminId = UUID.randomUUID(), id = UUID.randomUUID();
        RegistrationRequest registration = new RegistrationRequest();
        registration.setId(id); registration.setCustomerId(id); registration.setStatus(RegistrationStatus.APPROVED);
        registration.setOtpSentAt(Instant.parse("2026-10-25T00:00:00Z"));
        properties.setOtpResendCooldown(Duration.ofSeconds(90));
        User user = new User(); user.setId(id);
        when(registrations.findById(id)).thenReturn(Optional.of(registration));
        when(users.findById(id)).thenReturn(Optional.of(user));
        UserProfile profile = new UserProfile();
        profile.setDeliveryAddress("12 Nguyen Hue");
        profile.setWard("Phuong Sai Gon");
        profile.setProvince("Thanh pho Ho Chi Minh");
        profile.setDeliveryNote("Call before delivery");
        when(profiles.findById(id)).thenReturn(Optional.of(profile));
        var response = service.detail(adminId, id);
        assertThat(response.otpResendAvailableAt()).isEqualTo(Instant.parse("2026-10-25T00:01:30Z"));
        assertThat(response.deliveryNote()).isEqualTo("Call before delivery");
        assertThat(response.fullDeliveryAddress()).isEqualTo("12 Nguyen Hue, Phuong Sai Gon, Thanh pho Ho Chi Minh");
        registration.setOtpSentAt(null);
        assertThat(service.detail(adminId, id).otpResendAvailableAt()).isNull();
    }
}
