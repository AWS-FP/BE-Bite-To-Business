package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.config.security.AuthProperties;
import com.se203730.mvc.generateuibe.features.admin.dto.request.ReviewRegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.RegistrationResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.RegistrationReviewResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.RegistrationStatsResponse;
import com.se203730.mvc.generateuibe.features.admin.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.notification.service.RegistrationMailService;
import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.user.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.user.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.user.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Nghiệp vụ Admin xử lý yêu cầu đăng ký Customer.
 * Controller nhận request; service kiểm tra quyền/điều kiện và gọi repository để đọc, ghi dữ liệu.
 */
@Service
@Slf4j
public class AdminRegistrationService {
    @Autowired
    private UserRepository users;

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private RegistrationRequestRepository registrations;

    @Autowired
    private AccountAccessService accounts;

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

    /** Danh sách hồ sơ theo trạng thái, có phân trang và sắp xếp hồ sơ mới nhất trước. */
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> list(
            UUID adminId,
            RegistrationStatus status,
            String keyword,
            CustomerType customerType,
            Sort.Direction direction,
            int page,
            int size) {
        accounts.requireAdmin(adminId);

        Sort sort = Sort.by(direction, "submittedAt").and(Sort.by("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<RegistrationRequest> result = registrations
                .findAll(RegistrationFilters.matching(status, keyword, customerType), pageable);

        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RegistrationStatsResponse stats(UUID adminId) {
        accounts.requireAdmin(adminId);
        long pending = registrations.countByStatus(RegistrationStatus.PENDING);
        long approved = registrations.countByStatus(RegistrationStatus.APPROVED);
        long rejected = registrations.countByStatus(RegistrationStatus.REJECTED);
        return new RegistrationStatsResponse(pending + approved + rejected, pending, approved,
                rejected);
    }

    /** Ghép thông tin tài khoản, hồ sơ và yêu cầu đăng ký để Admin xem trước khi quyết định. */
    @Transactional(readOnly = true)
    public RegistrationResponse detail(UUID adminId, UUID requestId) {
        accounts.requireAdmin(adminId);

        RegistrationRequest registration = registrations.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return toResponse(registration);
    }

    /** Chỉ duyệt hoặc từ chối hồ sơ PENDING; quyết định đã xử lý không được thay đổi lần nữa. */
    public RegistrationReviewResponse review(
            UUID adminId,
            UUID requestId,
            ReviewRegistrationRequest request) {
        accounts.requireAdmin(adminId);

        if (request.decision() == ReviewRegistrationRequest.Decision.REJECT
                && request.note() == null) {
            throw new BusinessException(ErrorCode.REJECTION_NOTE_REQUIRED);
        }

        // Lưu quyết định trước khi gửi email; SMTP lỗi không làm mất kết quả xử lý.
        RegistrationResponse reviewedRegistration = Objects
                .requireNonNull(transaction.execute(transactionStatus -> {
                    RegistrationRequest registration = findLockedRegistration(requestId);

                    if (registration.getStatus() != RegistrationStatus.PENDING) {
                        throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_REVIEWED);
                    }

                    if (request.decision() == ReviewRegistrationRequest.Decision.APPROVE) {
                        registration.setStatus(RegistrationStatus.APPROVED);
                    } else {
                        registration.setStatus(RegistrationStatus.REJECTED);
                    }

                    Instant reviewedAt = clock.now();
                    registration.setReviewedAt(reviewedAt);
                    registration.setUpdatedAt(reviewedAt);
                    registration.setReviewNote(request.note());
                    registrations.saveAndFlush(registration);

                    return toResponse(registration);
                }));

        if (request.decision() == ReviewRegistrationRequest.Decision.REJECT) {
            try {
                mail.sendRejection(reviewedRegistration.customer().email(),
                        reviewedRegistration.reviewNote());
                return new RegistrationReviewResponse(reviewedRegistration, false, true);
            } catch (BusinessException exception) {
                if (exception.getErrorCode() != ErrorCode.EMAIL_DELIVERY_FAILED)
                    throw exception;
                log.warn("Registration {} rejected; notification email delivery failed", requestId);
                return new RegistrationReviewResponse(reviewedRegistration, false, false);
            }
        }

        try {
            RegistrationResponse registrationWithOtp = sendOtp(requestId);
            return new RegistrationReviewResponse(registrationWithOtp, true, false);
        } catch (BusinessException exception) {
            ErrorCode errorCode = exception.getErrorCode();
            if (!isOtpDeliveryFailure(errorCode)) {
                throw exception;
            }

            // Hồ sơ vẫn APPROVED nếu gửi OTP lỗi; Admin có thể dùng API gửi lại mà không duyệt lại.
            log.warn("Registration {} approved; OTP delivery needs retry ({})", requestId,
                    errorCode);
            return new RegistrationReviewResponse(reviewedRegistration, false, false);
        }
    }

    /** Admin gửi lại OTP cho hồ sơ đã duyệt nhưng chưa hoàn tất kích hoạt. */
    public RegistrationResponse resendOtp(UUID adminId, UUID requestId) {
        accounts.requireAdmin(adminId);
        return sendOtp(requestId);
    }

    private RegistrationResponse sendOtp(UUID requestId) {
        return Objects.requireNonNull(transaction.execute(transactionStatus -> {
            RegistrationRequest registration = findLockedRegistration(requestId);
            User customer = accounts.requireApprovedCustomer(registration);

            // Chặn gửi lại quá nhanh; thời gian chờ được lấy từ app.auth.otp-resend-cooldown.
            Instant now = clock.now();
            if (registration.getOtpSentAt() != null) {
                Instant nextAllowedSendAt = registration.getOtpSentAt()
                        .plus(properties.getOtpResendCooldown());
                if (now.isBefore(nextAllowedSendAt)) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
                }
            }

            SupabaseAuthClient.GeneratedOtp generatedOtp = supabase
                    .generateOtp(customer.getEmail());
            boolean sameCustomer = customer.getId().equals(generatedOtp.id());
            boolean sameEmail = customer.getEmail().equalsIgnoreCase(generatedOtp.email());
            String otp = generatedOtp.emailOtp();

            if (!sameCustomer || !sameEmail || otp == null || !otp.matches("[0-9]{6,10}")) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }

            // Gửi tới email tài khoản đã đăng ký; chỉ lưu otpSentAt sau khi SMTP chấp nhận gửi thư.
            mail.sendOtp(customer.getEmail(), otp, properties.getOtpMaxAge().toMinutes());
            Instant sentAt = clock.now();
            registration.setOtpSentAt(sentAt);
            registration.setUpdatedAt(sentAt);
            registrations.saveAndFlush(registration);

            return toResponse(registration);
        }));
    }

    private RegistrationRequest findLockedRegistration(UUID requestId) {
        // Khóa ghi trong transaction để hai request không cùng thay đổi quyết định hoặc gửi OTP.
        return registrations.findForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private boolean isOtpDeliveryFailure(ErrorCode errorCode) {
        return errorCode == ErrorCode.EMAIL_DELIVERY_FAILED
                || errorCode == ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE
                || errorCode == ErrorCode.AUTH_NOT_CONFIGURED
                // Quyết định APPROVED đã commit; bị giới hạn gửi mã không có nghĩa duyệt thất bại.
                || errorCode == ErrorCode.TOO_MANY_REQUESTS;
    }

    /** DTO trả đầy đủ form để Admin xem, không trả entity trực tiếp hoặc lộ OTP/token/mật khẩu. */
    private RegistrationResponse toResponse(RegistrationRequest registration) {
        UUID customerId = registration.getCustomerId();
        User customer = users.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = profiles.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return new RegistrationResponse(
                registration.getId(),
                UserResponse.from(customer),
                profile.getCustomerType(),
                profile.getBusinessName(),
                profile.getProvince(),
                profile.getDistrict(),
                profile.getWard(),
                profile.getDeliveryAddress(),
                profile.getFullDeliveryAddress(),
                profile.getDeliveryNote(),
                profile.getInvoiceSameAsDelivery(),
                profile.getInvoicePurchaser(),
                profile.getInvoiceTaxCode(),
                profile.getInvoiceEmail(),
                profile.getInvoiceAddress(),
                registration.getStatus(),
                registration.getReviewNote(),
                registration.getSubmittedAt(),
                registration.getReviewedAt(),
                registration.getOtpSentAt(),
                registration.getOtpSentAt() == null
                        ? null
                        : registration.getOtpSentAt().plus(properties.getOtpResendCooldown()),
                registration.getActivatedAt());
    }
}
