package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.admin.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.admin.dto.request.*;
import com.se203730.mvc.generateuibe.features.admin.dto.response.*;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationDuplicateAttempt;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationDuplicateAttemptRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Xử lý Guest gửi đăng ký, Customer xác minh OTP và tạo mật khẩu. Nghiệp vụ xét duyệt nằm trong AdminRegistrationService.
 */
@Service
@Slf4j
public class RegistrationService {
    @Autowired
    private UserRepository users;

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private RegistrationRequestRepository registrations;

    @Autowired
    private SupabaseAuthClient supabase;

    @Autowired
    private AccountAccessService accounts;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DatabaseClock clock;

    @Autowired
    private RegistrationDuplicateAttemptRepository duplicates;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Tạo tài khoản chờ, lưu hồ sơ và yêu cầu PENDING; chỉ trả biên nhận, chưa cho phép đăng nhập mua hàng.
     * Email/số điện thoại đã có tài khoản KHÔNG trả lỗi "đã tồn tại" (lỗi đó cho phép dò email nào đã đăng ký):
     * vẫn trả biên nhận như thường, ghi lại để admin thấy và tự động gửi mail từ chối.
     */
    public RegistrationReceipt register(RegisterRequest request) {
        String duplicate = duplicateField(request);
        if (duplicate != null)
            return acceptDuplicate(request, duplicate);
        // 1. Tạo tài khoản phía Supabase; dùng cùng UUID cho bảng users của ứng dụng.
        UUID authUserId;
        try {
            authUserId = supabase.createPendingUser(request.email());
        } catch (BusinessException ex) {
            // Supabase đã có email này (dù bảng users chưa có): xử lý như trùng, không báo lỗi riêng.
            if (ex.getErrorCode() == ErrorCode.EMAIL_ALREADY_EXISTS)
                return acceptDuplicate(request, "EMAIL");
            throw ex;
        }
        try {
            // 2. Ba bản ghi nội bộ phải cùng thành công; lỗi một bước thì rollback toàn bộ transaction.
            return Objects.requireNonNull(transaction.execute(status -> {
                Instant now = clock.now();
                User user = new User();
                user.setId(authUserId);
                user.setFirstName(request.firstName());
                user.setLastName(request.lastName());
                user.setEmail(request.email());
                user.setPhone(request.phone());
                user.setRole(UserRole.CUSTOMER);
                user.setEmailVerified(false);
                user.setBanned(false);
                user.setNewsletterSubscribed(Boolean.TRUE.equals(request.newsletterConsent()));
                user.setNewsletterConsentedAt(Boolean.TRUE.equals(request.newsletterConsent()) ? now : null);
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                users.saveAndFlush(user);

                // Hồ sơ riêng lưu thông tin form; không lặp các cột tên/email đã có trong users.
                UserProfile profile = new UserProfile();
                profile.setUserId(authUserId);
                profile.setCustomerType(request.customerType());
                profile.setBusinessName(request.businessName());
                profile.setProvince(request.province());
                profile.setDistrict(request.district());
                profile.setWard(request.ward());
                profile.setDeliveryAddress(request.deliveryAddress());
                profile.setDeliveryNote(request.deliveryNote());
                profile.setInvoiceSameAsDelivery(request.invoiceSameAsDelivery());
                profile.setInvoicePurchaser(request.invoicePurchaser());
                profile.setInvoiceTaxCode(request.invoiceTaxCode());
                profile.setInvoiceEmail(request.invoiceEmail());
                profile.setInvoiceAddress(request.invoiceSameAsDelivery()
                        ? profile.getFullDeliveryAddress()
                        : request.invoiceAddress());
                profile.setCreatedAt(now);
                profile.setUpdatedAt(now);
                profiles.saveAndFlush(profile);

                // Gửi đăng ký chỉ tạo PENDING; Guest không thể tự chọn APPROVED hoặc role khác.
                RegistrationRequest registration = new RegistrationRequest();
                registration.setId(UUID.randomUUID());
                registration.setCustomerId(authUserId);
                registration.setStatus(RegistrationStatus.PENDING);
                registration.setCreatedAt(now);
                registration.setSubmittedAt(now);
                registration.setUpdatedAt(now);
                registrations.saveAndFlush(registration);
                return new RegistrationReceipt(registration.getId(), registration.getStatus(), now);
            }));
        } catch (RuntimeException ex) {
            // DB transaction đã rollback; chỉ xóa Auth user vừa được chính lần đăng ký này tạo.
            try {
                supabase.deletePendingUser(authUserId);
            } catch (RuntimeException cleanupFailure) {
                log.error("Could not remove pending Auth user {} after registration rollback",
                        authUserId);
            }
            // Hai request cùng email/số điện thoại chạy song song: request thua cũng không được lộ lỗi trùng.
            if (isUserUniqueViolation(ex)) {
                String field = duplicateField(request);
                return acceptDuplicate(request, field == null ? "EMAIL" : field);
            }
            throw ex;
        }
    }

    private String duplicateField(RegisterRequest request) {
        boolean email = users.existsByEmailIgnoreCase(request.email());
        boolean phone = users.existsByPhone(request.phone());
        if (email && phone) return "EMAIL_AND_PHONE";
        if (email) return "EMAIL";
        if (phone) return "PHONE";
        return null;
    }

    private static boolean isUserUniqueViolation(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if ("ux_users_phone".equals(name) || "ux_users_email_ci".equals(name)) return true;
            }
            if (cause.getCause() == cause) break;
        }
        return false;
    }

    /**
     * Ghi lại đăng ký trùng và trả biên nhận giả có cùng dạng với biên nhận thật. Mail từ chối được gửi sau
     * vài phút bởi {@link RegistrationDuplicateService}; mỗi địa chỉ nhận tối đa một mail/24 giờ để không bị
     * lợi dụng spam hộp thư người khác.
     */
    private RegistrationReceipt acceptDuplicate(RegisterRequest request, String field) {
        Instant now = clock.now();
        RegistrationDuplicateAttempt attempt = new RegistrationDuplicateAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setEmail(request.email());
        attempt.setPhone(request.phone());
        attempt.setFirstName(request.firstName());
        attempt.setLastName(request.lastName());
        attempt.setDuplicateField(field);
        attempt.setExistingUserId(users.findByEmailIgnoreCase(request.email()).map(User::getId).orElse(null));
        boolean recentMail = duplicates.hasRecentRejectionMail(request.email(), now.minus(Duration.ofHours(24)));
        attempt.setEmailStatus(recentMail ? "SKIPPED" : "PENDING");
        attempt.setNextEmailAt(now.plusSeconds(180 + RANDOM.nextInt(421)));
        attempt.setCreatedAt(now);
        duplicates.saveAndFlush(attempt);
        // Không ghi email/số điện thoại vào log.
        log.info("Duplicate registration recorded; attemptId={}, field={}", attempt.getId(), field);
        // Đăng ký thật gọi Supabase nên chậm hơn; thêm độ trễ để thời gian phản hồi không lộ kết quả.
        try {
            Thread.sleep(250 + RANDOM.nextInt(500));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return new RegistrationReceipt(UUID.randomUUID(), RegistrationStatus.PENDING, now);
    }

    /** Kiểm tra thời hạn và xác minh OTP; chỉ đánh dấu email đã xác minh, chưa ghi nhận kích hoạt. */
    public OtpVerificationResponse verifyOtp(VerifyOtpRequest request) {
        return Objects.requireNonNull(transaction.execute(status -> {
            User user = users.findByEmailIgnoreCase(request.email())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
            RegistrationRequest registration = registrations.findByCustomerIdForUpdate(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));
            accounts.requireApprovedCustomer(registration);
            Instant generatedAt = registration.getOtpGeneratedAt();
            if (generatedAt == null)
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
            if (clock.now().isAfter(generatedAt.plus(properties.getOtpMaxAge())))
                throw new BusinessException(ErrorCode.OTP_EXPIRED);
            // Backend kiểm tra điều kiện nghiệp vụ; Supabase kiểm tra OTP có đúng và còn dùng được hay không.
            var session = supabase.verifyOtp(user.getEmail(), request.otp());
            if (session.accessToken() == null || session.accessToken().isBlank()
                    || !"bearer".equalsIgnoreCase(session.tokenType())
                    || session.expiresIn() <= 0) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            if (session.user() == null || !user.getId().equals(session.user().id())
                    || session.user().emailConfirmedAt() == null)
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            registration.setOtpDeliveryState("VERIFIED");
            registration.setUpdatedAt(clock.now());
            registrations.saveAndFlush(registration);
            user.setEmailVerified(true);
            user.setUpdatedAt(clock.now());
            users.saveAndFlush(user);
            // Không trả refresh token của phiên OTP; token này không qua được cổng API nghiệp vụ.
            return new OtpVerificationResponse(session.accessToken(), "Bearer",
                    Math.min(session.expiresIn(), properties.getPasswordSetupTtl().toSeconds()),
                    true);
        }));
    }

    /** Dùng phiên sau OTP để tạo mật khẩu; chỉ ghi activatedAt sau khi Supabase cập nhật thành công. */
    public void activate(Jwt jwt, SetPasswordRequest request) {
        if (!request.password().equals(request.confirmPassword()))
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        UUID id = AccountAccessService.subject(jwt);
        transaction.executeWithoutResult(status -> {
            RegistrationRequest registration = registrations.findByCustomerIdForUpdate(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));
            User user = accounts.requireApprovedCustomer(registration);
            if (!user.isEmailVerified() || registration.getOtpGeneratedAt() == null)
                throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
            // Giới hạn tuổi phiên tạo mật khẩu; chấp nhận lệch một giây do thời gian JWT chỉ chính xác tới giây.
            if (jwt.getIssuedAt() == null
                    || jwt.getIssuedAt().plusSeconds(1).isBefore(registration.getOtpGeneratedAt())
                    || clock.now()
                            .isAfter(jwt.getIssuedAt().plus(properties.getPasswordSetupTtl())))
                throw new BusinessException(ErrorCode.SETUP_SESSION_EXPIRED);
            var updated = supabase.setPassword(jwt.getTokenValue(), request.password());
            if (!id.equals(updated.id()) || updated.emailConfirmedAt() == null)
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            Instant activatedAt = clock.now();
            registration.setActivatedAt(activatedAt);
            registration.setStatus(RegistrationStatus.APPROVED);
            registration.setUpdatedAt(activatedAt);
            registrations.saveAndFlush(registration);
        });
    }

}
