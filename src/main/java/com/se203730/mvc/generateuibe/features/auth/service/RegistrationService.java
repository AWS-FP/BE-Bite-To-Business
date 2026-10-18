package com.se203730.mvc.generateuibe.features.auth.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.user.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.auth.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.auth.dto.request.*;
import com.se203730.mvc.generateuibe.features.auth.dto.response.*;
import com.se203730.mvc.generateuibe.features.auth.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.user.enums.UserRole;
import com.se203730.mvc.generateuibe.features.auth.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Xử lý Guest gửi đăng ký, Customer xác minh OTP và tạo mật khẩu. Nghiệp vụ xét duyệt nằm trong AdminRegistrationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final RegistrationRequestRepository registrations;
    private final SupabaseAuthClient supabase;
    private final AccountAccessService accounts;
    private final AuthProperties properties;
    private final TransactionTemplate transaction;

    /** Tạo tài khoản chờ, lưu hồ sơ và yêu cầu PENDING; chỉ trả biên nhận, chưa cho phép đăng nhập mua hàng. */
    public RegistrationReceipt register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        // 1. Tạo tài khoản phía Supabase; dùng cùng UUID cho bảng users của ứng dụng.
        UUID authUserId = supabase.createPendingUser(request.email());
        try {
            // 2. Ba bản ghi nội bộ phải cùng thành công; lỗi một bước thì rollback toàn bộ transaction.
            return Objects.requireNonNull(transaction.execute(status -> {
                Instant now = Instant.now();
                User user = new User();
                user.setId(authUserId);
                user.setFirstName(request.firstName()); user.setLastName(request.lastName());
                user.setEmail(request.email()); user.setPhone(request.phone());
                user.setRole(UserRole.CUSTOMER); user.setEmailVerified(false); user.setBanned(false);
                user.setCreatedAt(now); user.setUpdatedAt(now);
                users.saveAndFlush(user);

                // Hồ sơ riêng lưu thông tin form; không lặp các cột tên/email đã có trong users.
                UserProfile profile = new UserProfile();
                profile.setUserId(authUserId); profile.setCustomerType(request.customerType());
                profile.setBusinessName(request.businessName()); profile.setProvince(request.province());
                profile.setDistrict(request.district()); profile.setWard(request.ward());
                profile.setDeliveryAddress(request.deliveryAddress());
                profile.setInvoiceSameAsDelivery(request.invoiceSameAsDelivery());
                profile.setInvoicePurchaser(request.invoicePurchaser()); profile.setInvoiceTaxCode(request.invoiceTaxCode());
                profile.setInvoiceEmail(request.invoiceEmail());
                profile.setInvoiceAddress(request.invoiceSameAsDelivery()
                        ? String.join(", ", request.deliveryAddress(), request.ward(), request.district(), request.province())
                        : request.invoiceAddress());
                profile.setCreatedAt(now); profile.setUpdatedAt(now);
                profiles.saveAndFlush(profile);

                // Gửi đăng ký chỉ tạo PENDING; Guest không thể tự chọn APPROVED hoặc role khác.
                RegistrationRequest registration = new RegistrationRequest();
                registration.setId(UUID.randomUUID()); registration.setCustomerId(authUserId);
                registration.setStatus(RegistrationStatus.PENDING);
                registration.setCreatedAt(now); registration.setSubmittedAt(now); registration.setUpdatedAt(now);
                registrations.saveAndFlush(registration);
                return new RegistrationReceipt(registration.getId(), registration.getStatus(), now);
            }));
        } catch (RuntimeException ex) {
            // DB transaction đã rollback; chỉ xóa Auth user vừa được chính lần đăng ký này tạo.
            try { supabase.deletePendingUser(authUserId); }
            catch (RuntimeException cleanupFailure) {
                log.error("Could not remove pending Auth user {} after registration rollback", authUserId);
            }
            throw ex;
        }
    }

    /** Kiểm tra thời hạn và xác minh OTP; chỉ đánh dấu email đã xác minh, chưa ghi nhận kích hoạt. */
    public OtpVerificationResponse verifyOtp(VerifyOtpRequest request) {
        return Objects.requireNonNull(transaction.execute(status -> {
            User user = users.findByEmailIgnoreCase(request.email())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
            RegistrationRequest registration = registrations.findByCustomerIdForUpdate(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));
            accounts.requireApprovedCustomer(registration);
            if (registration.getOtpSentAt() == null) throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
            if (Instant.now().isAfter(registration.getOtpSentAt().plus(properties.getOtpMaxAge())))
                throw new BusinessException(ErrorCode.OTP_EXPIRED);
            // Backend kiểm tra điều kiện nghiệp vụ; Supabase kiểm tra OTP có đúng và còn dùng được hay không.
            var session = supabase.verifyOtp(user.getEmail(), request.otp());
            if (session.user() == null || !user.getId().equals(session.user().id())
                    || session.user().emailConfirmedAt() == null || session.accessToken() == null)
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            user.setEmailVerified(true); user.setUpdatedAt(Instant.now()); users.saveAndFlush(user);
            // Không trả refresh token của phiên OTP; token này không qua được cổng API nghiệp vụ.
            return new OtpVerificationResponse(session.accessToken(), "Bearer",
                    Math.min(session.expiresIn(), properties.getPasswordSetupTtl().toSeconds()), true);
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
            if (!user.isEmailVerified() || registration.getOtpSentAt() == null)
                throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
            // Giới hạn tuổi phiên tạo mật khẩu; chấp nhận lệch một giây do thời gian JWT chỉ chính xác tới giây.
            if (jwt.getIssuedAt() == null || jwt.getIssuedAt().plusSeconds(1).isBefore(registration.getOtpSentAt())
                    || Instant.now().isAfter(jwt.getIssuedAt().plus(properties.getPasswordSetupTtl())))
                throw new BusinessException(ErrorCode.SETUP_SESSION_EXPIRED);
            var updated = supabase.setPassword(jwt.getTokenValue(), request.password());
            if (!id.equals(updated.id()) || updated.emailConfirmedAt() == null)
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            registration.setActivatedAt(Instant.now()); registration.setUpdatedAt(Instant.now());
            registrations.saveAndFlush(registration);
        });
    }

}
