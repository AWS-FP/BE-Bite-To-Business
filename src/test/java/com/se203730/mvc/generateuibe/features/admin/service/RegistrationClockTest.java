package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.admin.dto.request.SetPasswordRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.VerifyOtpRequest;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationClockTest {
    @Mock
    UserRepository users;

    @Mock
    RegistrationRequestRepository registrations;

    @Mock
    AccountAccessService accounts;

    @Mock
    SupabaseAuthClient supabase;

    @Mock
    DatabaseClock clock;

    @Mock
    TransactionTemplate transaction;

    @Spy
    AuthProperties properties = new AuthProperties();

    @InjectMocks
    RegistrationService service;

    @Test
    void otpExpiresByDatabaseTimeEvenWhenBackendClockIsBehind() {
        UUID id = UUID.randomUUID();
        Instant sentAt = Instant.now().plusSeconds(90);
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOtpSentAt(sentAt);
        User user = new User();
        user.setId(id);
        when(users.findByEmailIgnoreCase("clock-test@gmail.com")).thenReturn(Optional.of(user));
        when(registrations.findByCustomerIdForUpdate(id)).thenReturn(Optional.of(registration));
        Instant databaseNow = sentAt.plus(properties.getOtpMaxAge()).plusSeconds(1);
        when(clock.now()).thenReturn(databaseNow);
        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        assertThatThrownBy(
                () -> service.verifyOtp(new VerifyOtpRequest("clock-test@gmail.com", "012345")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
        verifyNoInteractions(supabase);
    }

    @Test
    void passwordSetupExpiresByDatabaseTimeEvenWhenBackendClockIsBehind() {
        UUID id = UUID.randomUUID();
        Instant issuedAt = Instant.now().plusSeconds(90);
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(id.toString())
                .issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(3600)).build();
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOtpSentAt(issuedAt.minusSeconds(1));
        User user = new User();
        user.setEmailVerified(true);
        when(registrations.findByCustomerIdForUpdate(id)).thenReturn(Optional.of(registration));
        when(accounts.requireApprovedCustomer(registration)).thenReturn(user);
        Instant databaseNow = issuedAt.plus(properties.getPasswordSetupTtl()).plusSeconds(1);
        when(clock.now()).thenReturn(databaseNow);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(new SimpleTransactionStatus());
            return null;
        }).when(transaction).executeWithoutResult(any());

        assertThatThrownBy(
                () -> service.activate(jwt, new SetPasswordRequest("Password123", "Password123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.SETUP_SESSION_EXPIRED));
        verifyNoInteractions(supabase);
    }
}
