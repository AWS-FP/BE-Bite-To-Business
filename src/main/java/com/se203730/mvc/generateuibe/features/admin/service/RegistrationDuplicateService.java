package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.dto.response.RegistrationDuplicateResponse;
import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationDuplicateAttempt;
import com.se203730.mvc.generateuibe.features.admin.repository.RegistrationDuplicateAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Đăng ký trùng email/số điện thoại: danh sách cho admin và job tự động gửi mail từ chối.
 * Nội dung mail không nói trường nào bị trùng, để mail cũng không thành công cụ dò tài khoản.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationDuplicateService {
    static final String REJECTION_REASON = "Thông tin đăng ký này không thể được chấp nhận. "
            + "Nếu bạn đã có tài khoản Bite to Business, hãy đăng nhập hoặc dùng chức năng Quên mật khẩu.";
    private static final int MAX_EMAIL_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 10;

    private final RegistrationDuplicateAttemptRepository duplicates;
    private final RegistrationMailService mail;
    private final TransactionTemplate transaction;
    private final DatabaseClock clock;
    private final AccountAccessService accounts;

    public Page<RegistrationDuplicateResponse> list(UUID adminId, int page, int size) {
        accounts.requireAdmin(adminId);
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return duplicates.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size))
                .map(RegistrationDuplicateResponse::from);
    }

    @Scheduled(fixedDelayString = "${app.registration-duplicate-email.poll-ms:60000}")
    public void sendDueRejections() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (!sendNext()) return;
        }
    }

    private boolean sendNext() {
        // Claim one due row (lock + push next_email_at forward) so a crash or another instance cannot resend it.
        RegistrationDuplicateAttempt attempt = transaction.execute(status -> duplicates.lockNextDue(clock.now())
                .map(found -> {
                    found.setEmailAttempts(found.getEmailAttempts() + 1);
                    found.setNextEmailAt(clock.now().plus(10, ChronoUnit.MINUTES));
                    return duplicates.saveAndFlush(found);
                })
                .orElse(null));
        if (attempt == null) return false;

        try {
            mail.sendRejection(attempt.getEmail(), REJECTION_REASON);
        } catch (RuntimeException ex) {
            log.warn("Duplicate registration rejection mail failed; attemptId={}, errorType={}",
                    attempt.getId(), ex.getClass().getSimpleName());
            transaction.executeWithoutResult(status -> duplicates.findById(attempt.getId()).ifPresent(row -> {
                if (row.getEmailAttempts() >= MAX_EMAIL_ATTEMPTS) {
                    row.setEmailStatus("FAILED");
                } else {
                    long delay = Math.min(3600, 60L << Math.min(row.getEmailAttempts(), 6));
                    row.setNextEmailAt(clock.now().plusSeconds(delay));
                }
                duplicates.saveAndFlush(row);
            }));
            return true;
        }
        Instant sentAt = clock.now();
        transaction.executeWithoutResult(status -> duplicates.findById(attempt.getId()).ifPresent(row -> {
            row.setEmailStatus("SENT");
            row.setEmailSentAt(sentAt);
            duplicates.saveAndFlush(row);
        }));
        return true;
    }
}
