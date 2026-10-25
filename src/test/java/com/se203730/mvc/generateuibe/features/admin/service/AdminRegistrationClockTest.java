package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.dto.request.ReviewRegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.notification.service.RegistrationMailService;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRegistrationClockTest {
    @Mock
    RegistrationRequestRepository registrations;

    @Mock
    AccountAccessService accounts;

    @Mock
    UserRepository users;

    @Mock
    UserProfileRepository profiles;

    @Mock
    SupabaseAuthClient supabase;

    @Mock
    RegistrationMailService mail;

    @Mock
    DatabaseClock clock;

    @Mock
    TransactionTemplate transaction;

    @Spy
    AuthProperties properties = new AuthProperties();

    @InjectMocks
    AdminRegistrationService service;

    private boolean transactionCompleted;

    @Test
    void rejectsUsingDatabaseTimeWhenBackendClockIsBehindSubmission() {
        RegistrationRequest registration = pendingRegistration();
        Instant databaseNow = registration.getSubmittedAt().plusSeconds(2);
        when(clock.now()).thenReturn(databaseNow);

        var response = service.review(UUID.randomUUID(), registration.getId(),
                new ReviewRegistrationRequest(ReviewRegistrationRequest.Decision.REJECT,
                        "  Missing information  "));

        assertThat(response.registration().status()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(response.registration().reviewedAt())
                .isAfterOrEqualTo(registration.getSubmittedAt());
        assertThat(response.registration().reviewedAt()).isEqualTo(databaseNow);
        assertThat(response.registration().reviewNote()).isEqualTo("Missing information");
        assertThat(response.otpSent()).isFalse();
        assertThat(response.rejectionEmailSent()).isTrue();
        verify(registrations).saveAndFlush(registration);
        verify(mail).sendRejection("clock-test@gmail.com", "Missing information");
        verifyNoInteractions(supabase);
    }

    @Test
    void rejectionEmailIsSentAfterTheDecisionTransactionCompletes() {
        RegistrationRequest registration = pendingRegistration();
        when(clock.now()).thenReturn(registration.getSubmittedAt().plusSeconds(2));
        doAnswer(invocation -> {
            assertThat(transactionCompleted).isTrue();
            assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
            return null;
        }).when(mail).sendRejection("clock-test@gmail.com", "Missing details");

        var response = service.review(UUID.randomUUID(), registration.getId(),
                new ReviewRegistrationRequest(ReviewRegistrationRequest.Decision.REJECT,
                        "Missing details"));

        assertThat(response.rejectionEmailSent()).isTrue();
        verifyNoInteractions(supabase);
    }

    @Test
    void emailFailureKeepsRejectedDecisionAndReportsNotificationFailure() {
        RegistrationRequest registration = pendingRegistration();
        when(clock.now()).thenReturn(registration.getSubmittedAt().plusSeconds(2));
        doThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED))
                .when(mail).sendRejection("clock-test@gmail.com", "Missing details");

        var response = service.review(UUID.randomUUID(), registration.getId(),
                new ReviewRegistrationRequest(ReviewRegistrationRequest.Decision.REJECT,
                        "Missing details"));

        assertThat(response.registration().status()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(response.registration().reviewNote()).isEqualTo("Missing details");
        assertThat(response.rejectionEmailSent()).isFalse();
        assertThat(response.otpSent()).isFalse();
        verify(registrations).saveAndFlush(registration);
        verifyNoInteractions(supabase);
    }

    @Test
    void approvalUsesDatabaseTimeAndRemainsApprovedWhenOtpDeliveryIsUnavailable() {
        RegistrationRequest registration = pendingRegistration();
        Instant databaseNow = registration.getSubmittedAt().plusSeconds(2);
        when(clock.now()).thenReturn(databaseNow);
        User customer = users.findById(registration.getCustomerId()).orElseThrow();
        when(accounts.requireApprovedCustomer(registration)).thenReturn(customer);
        when(supabase.generateOtp(customer.getEmail()))
                .thenThrow(new BusinessException(ErrorCode.AUTH_NOT_CONFIGURED));

        var response = service.review(UUID.randomUUID(), registration.getId(),
                new ReviewRegistrationRequest(ReviewRegistrationRequest.Decision.APPROVE, null));

        assertThat(response.registration().status()).isEqualTo(RegistrationStatus.APPROVED);
        assertThat(response.registration().reviewedAt()).isEqualTo(databaseNow);
        assertThat(response.registration().reviewedAt())
                .isAfterOrEqualTo(registration.getSubmittedAt());
        assertThat(response.otpSent()).isFalse();
        verifyNoInteractions(mail);
    }

    @Test
    void resendCooldownAndSentTimestampUseDatabaseTime() {
        RegistrationRequest registration = pendingRegistration();
        Instant databaseNow = registration.getSubmittedAt().plusSeconds(180);
        Instant sentAt = databaseNow.plusSeconds(1);
        registration.setStatus(RegistrationStatus.APPROVED);
        registration.setReviewedAt(registration.getSubmittedAt().plusSeconds(1));
        registration.setOtpSentAt(databaseNow.minusSeconds(61));
        when(clock.now()).thenReturn(databaseNow, sentAt);
        User customer = users.findById(registration.getCustomerId()).orElseThrow();
        when(accounts.requireApprovedCustomer(registration)).thenReturn(customer);
        when(supabase.generateOtp(customer.getEmail())).thenReturn(
                new SupabaseAuthClient.GeneratedOtp(customer.getId(), customer.getEmail(),
                        "012345"));

        var response = service.resendOtp(UUID.randomUUID(), registration.getId());

        assertThat(response.otpSentAt()).isEqualTo(sentAt);
        assertThat(response.otpSentAt()).isAfterOrEqualTo(response.reviewedAt());
        verify(mail).sendOtp(customer.getEmail(), "012345", 10);
    }

    private RegistrationRequest pendingRegistration() {
        UUID id = UUID.randomUUID();
        RegistrationRequest registration = new RegistrationRequest();
        registration.setId(id);
        registration.setCustomerId(id);
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setSubmittedAt(Instant.now().plusSeconds(90));
        when(registrations.findForUpdate(id)).thenReturn(Optional.of(registration));
        User customer = new User();
        customer.setId(id);
        customer.setEmail("clock-test@gmail.com");
        when(users.findById(id)).thenReturn(Optional.of(customer));
        when(profiles.findById(id)).thenReturn(Optional.of(new UserProfile()));
        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            transactionCompleted = false;
            Object result = callback.doInTransaction(new SimpleTransactionStatus());
            transactionCompleted = true;
            return result;
        });
        return registration;
    }
}
