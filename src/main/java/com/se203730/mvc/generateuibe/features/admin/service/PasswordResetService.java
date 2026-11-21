package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.admin.dto.request.ForgotPasswordRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.SetPasswordRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.request.VerifyOtpRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.OtpVerificationResponse;
import com.se203730.mvc.generateuibe.features.admin.entity.PasswordResetRequest;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.PasswordResetRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Quên mật khẩu tự phục vụ: gửi OTP tới Gmail tài khoản, xác minh OTP, rồi người dùng tự đặt mật khẩu mới.
 * Supabase tạo và kiểm tra OTP; backend kiểm tra điều kiện tài khoản, thời hạn và giới hạn gửi lại.
 * Đặt lại mật khẩu không gỡ cấm: tài khoản bị cấm vẫn bị chặn khi đăng nhập.
 */
@Service
@Slf4j
public class PasswordResetService {
    @Autowired
    private UserRepository users;

    @Autowired
    private RegistrationRequestRepository registrations;

    @Autowired
    private PasswordResetRequestRepository resets;

    @Autowired
    private SupabaseAuthClient supabase;

    @Autowired
    private RegistrationMailService mail;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DatabaseClock clock;

    /**
     * Gửi OTP nếu Gmail thuộc tài khoản đã kích hoạt. Gmail không tồn tại, chưa kích hoạt hoặc đang trong
     * thời gian chờ gửi lại đều kết thúc im lặng để response không cho biết tài khoản có tồn tại hay không.
     */
    public void requestReset(ForgotPasswordRequest request) {
        Optional<User> account = users.findByEmailIgnoreCase(request.email());
        if (account.isEmpty() || !canResetPassword(account.get())) {
            return;
        }
        UUID userId = account.get().getId();

        PendingOtp pending = transaction.execute(status -> {
            User user = users.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            Instant now = clock.now();
            PasswordResetRequest reset = resets.findById(userId).orElse(null);
            if (reset != null && now.isBefore(reset.getOtpGeneratedAt().plus(properties.getOtpResendCooldown()))) {
                return null;
            }

            SupabaseAuthClient.GeneratedOtp generated = supabase.generateOtp(user.getEmail());
            String otp = generated.emailOtp();
            if (!userId.equals(generated.id()) || !user.getEmail().equalsIgnoreCase(generated.email())
                    || otp == null || !otp.matches("[0-9]{6,10}")) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            if (reset == null) {
                reset = new PasswordResetRequest();
                reset.setUserId(userId);
                reset.setCreatedAt(now);
            }
            reset.setOtpGeneratedAt(now);
            reset.setOtpSentAt(null);
            reset.setVerifiedAt(null);
            reset.setCompletedAt(null);
            reset.setUpdatedAt(now);
            resets.saveAndFlush(reset);
            return new PendingOtp(user.getEmail(), otp, now);
        });
        if (pending == null) {
            return;
        }

        // Gửi email ngoài transaction để không giữ khóa dòng users trong lúc chờ SMTP.
        mail.sendPasswordResetOtp(pending.email(), pending.otp(), properties.getOtpMaxAge().toMinutes());

        transaction.executeWithoutResult(status -> {
            users.findByIdForUpdate(userId);
            resets.findById(userId)
                    .filter(reset -> pending.generatedAt().equals(reset.getOtpGeneratedAt()))
                    .ifPresent(reset -> {
                        Instant sentAt = clock.now();
                        reset.setOtpSentAt(sentAt);
                        reset.setUpdatedAt(sentAt);
                        resets.saveAndFlush(reset);
                    });
        });
    }

    /** Xác minh OTP đặt lại mật khẩu; trả access token ngắn hạn chỉ dùng cho bước đặt mật khẩu mới. */
    public OtpVerificationResponse verifyOtp(VerifyOtpRequest request) {
        User account = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
        UUID userId = account.getId();

        return Objects.requireNonNull(transaction.execute(status -> {
            User user = users.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
            if (!canResetPassword(user)) {
                throw new BusinessException(ErrorCode.INVALID_OTP);
            }
            PasswordResetRequest reset = resets.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
            if (reset.getOtpSentAt() == null || reset.getCompletedAt() != null) {
                throw new BusinessException(ErrorCode.INVALID_OTP);
            }
            if (clock.now().isAfter(reset.getOtpGeneratedAt().plus(properties.getOtpMaxAge()))) {
                throw new BusinessException(ErrorCode.OTP_EXPIRED);
            }

            // Supabase kiểm tra mã đúng và chỉ dùng một lần.
            SupabaseAuthClient.Session session = supabase.verifyOtp(user.getEmail(), request.otp());
            if (session.accessToken() == null || session.accessToken().isBlank()
                    || !"bearer".equalsIgnoreCase(session.tokenType()) || session.expiresIn() <= 0) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            if (session.user() == null || !userId.equals(session.user().id())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }

            Instant verifiedAt = clock.now();
            reset.setVerifiedAt(verifiedAt);
            reset.setUpdatedAt(verifiedAt);
            resets.saveAndFlush(reset);
            // Không trả refresh token; phiên sau OTP không có amr password nên không gọi được API nghiệp vụ.
            return new OtpVerificationResponse(session.accessToken(), "Bearer",
                    Math.min(session.expiresIn(), properties.getPasswordSetupTtl().toSeconds()), true);
        }));
    }

    /** Đặt mật khẩu mới bằng phiên sau OTP, rồi thu hồi mọi phiên cũ của tài khoản. */
    public void resetPassword(Jwt jwt, SetPasswordRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        UUID userId = AccountAccessService.subject(jwt);

        transaction.executeWithoutResult(status -> {
            User user = users.findByIdForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            if (!canResetPassword(user)) {
                throw new BusinessException(ErrorCode.SETUP_SESSION_EXPIRED);
            }
            PasswordResetRequest reset = resets.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SETUP_SESSION_EXPIRED));
            if (reset.getVerifiedAt() == null || reset.getCompletedAt() != null) {
                throw new BusinessException(ErrorCode.SETUP_SESSION_EXPIRED);
            }
            // Token phải được cấp sau lần gửi OTP này và còn trong thời hạn đặt mật khẩu; lệch 1 giây do iat làm tròn.
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt == null || issuedAt.plusSeconds(1).isBefore(reset.getOtpGeneratedAt())
                    || clock.now().isAfter(issuedAt.plus(properties.getPasswordSetupTtl()))) {
                throw new BusinessException(ErrorCode.SETUP_SESSION_EXPIRED);
            }

            SupabaseAuthClient.SupabaseUser updated = supabase.setPassword(jwt.getTokenValue(), request.password());
            if (!userId.equals(updated.id())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            Instant completedAt = clock.now();
            reset.setCompletedAt(completedAt);
            reset.setUpdatedAt(completedAt);
            resets.saveAndFlush(reset);
        });

        try {
            supabase.logoutAllSessions(jwt.getTokenValue());
        } catch (BusinessException exception) {
            log.warn("Could not revoke sessions after password reset ({})", exception.getErrorCode());
        }
    }

    /** Staff/Admin đã xác minh email, hoặc Customer đã được duyệt và kích hoạt. Chưa kích hoạt thì dùng luồng OTP đăng ký. */
    private boolean canResetPassword(User user) {
        if (!user.isEmailVerified()) {
            return false;
        }
        if (user.getRole() != UserRole.CUSTOMER) {
            return true;
        }
        RegistrationRequest registration = registrations.findByCustomerId(user.getId()).orElse(null);
        return registration != null && registration.getStatus() == RegistrationStatus.APPROVED
                && registration.getActivatedAt() != null;
    }

    private record PendingOtp(String email, String otp, Instant generatedAt) {
        @Override
        public String toString() {
            return "PendingOtp[redacted]";
        }
    }
}
