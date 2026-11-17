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
import com.se203730.mvc.generateuibe.features.admin.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.admin.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
        long pending = registrations.countByStatus(RegistrationStatus.PENDING)
                + registrations.countByStatus(RegistrationStatus.AWAITING_ACTIVATION);
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
                        registration.setStatus(RegistrationStatus.AWAITING_ACTIVATION);
                        registration.setOtpDeliveryState("QUEUED");
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

        return new RegistrationReviewResponse(reviewedRegistration, false, false);
    }

    /** Đưa yêu cầu gửi lại OTP vào hàng đợi cho hồ sơ chờ kích hoạt. */
    public RegistrationResponse resendOtp(UUID adminId, UUID requestId) {
        accounts.requireAdmin(adminId);
        return Objects.requireNonNull(transaction.execute(status -> {
            RegistrationRequest registration = findLockedRegistration(requestId);
            accounts.requireApprovedCustomer(registration);
            if ("QUEUED".equals(registration.getOtpDeliveryState())) {
                return toResponse(registration);
            }
            Instant now = clock.now();
            if (registration.getOtpSentAt() != null && now.isBefore(registration.getOtpSentAt()
                    .plus(properties.getOtpResendCooldown()))) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
            }
            registration.setOtpSentAt(null);
            registration.setOtpDeliveryState("QUEUED");
            registration.setUpdatedAt(now);
            registrations.saveAndFlush(registration);
            return toResponse(registration);
        }));
    }

    @Bean("registrationOtpScheduler")
    public ThreadPoolTaskScheduler registrationOtpScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("registration-otp-");
        return scheduler;
    }

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-task-");
        return scheduler;
    }

    @Scheduled(fixedDelayString = "${app.registration-otp.poll-ms:3000}", scheduler = "registrationOtpScheduler")
    public void processQueuedOtp() {
        for (RegistrationRequest registration : registrations.findTop10ByOtpDeliveryStateOrderByUpdatedAtAsc("QUEUED")) {
            try {
                sendQueuedOtp(registration.getId());
            } catch (Exception exception) {
                log.warn("OTP queue processing failed for registration {} ({}).", registration.getId(), exception.getClass().getSimpleName());
            }
        }
    }

    private void sendQueuedOtp(UUID requestId) {
        transaction.executeWithoutResult(transactionStatus -> {
            RegistrationRequest registration = findLockedRegistration(requestId);
            if (!"QUEUED".equals(registration.getOtpDeliveryState())) {
                return;
            }
            try {
                User customer = accounts.requireApprovedCustomer(registration);
                SupabaseAuthClient.GeneratedOtp generatedOtp = supabase.generateOtp(customer.getEmail());
                boolean sameCustomer = customer.getId().equals(generatedOtp.id());
                boolean sameEmail = customer.getEmail().equalsIgnoreCase(generatedOtp.email());
                String otp = generatedOtp.emailOtp();

                if (!sameCustomer || !sameEmail || otp == null || !otp.matches("[0-9]{6,10}")) {
                    throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                }

                mail.sendOtp(customer.getEmail(), otp, properties.getOtpMaxAge().toMinutes());
                Instant sentAt = clock.now();
                registration.setOtpSentAt(sentAt);
                registration.setOtpDeliveryState("SENT");
                registration.setUpdatedAt(sentAt);
            } catch (BusinessException exception) {
                log.warn("OTP delivery failed for registration {} ({}).", requestId, exception.getErrorCode());
                registration.setOtpDeliveryState("FAILED");
                registration.setUpdatedAt(clock.now());
            }
            registrations.saveAndFlush(registration);

        });
    }

    private RegistrationRequest findLockedRegistration(UUID requestId) {
        // Khóa ghi trong transaction để hai request không cùng thay đổi quyết định hoặc gửi OTP.
        return registrations.findForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
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
                registration.getOtpDeliveryState(),
                registration.getOtpSentAt() == null
                        ? null
                        : registration.getOtpSentAt().plus(properties.getOtpResendCooldown()),
                registration.getActivatedAt());
    }
}
