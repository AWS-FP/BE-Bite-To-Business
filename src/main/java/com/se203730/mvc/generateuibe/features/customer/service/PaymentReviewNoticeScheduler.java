package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.repository.SepayWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Gửi email cho khách sau khi transaction ghi khoản REVIEW đã commit, nên webhook không phải chờ SMTP.
 * Gửi lỗi thì lượt sau thử lại; chỉ thử trong 7 ngày kể từ lúc nhận tiền.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReviewNoticeScheduler {
    private final SepayWebhookEventRepository events;
    private final PaymentReviewMailService mail;
    private final DatabaseClock clock;
    private final TransactionTemplate transactions;

    @Scheduled(
            initialDelayString = "${app.sepay.review-notice-initial-delay-ms:45000}",
            fixedDelayString = "${app.sepay.review-notice-poll-ms:60000}")
    public void sendPending() {
        Instant since = clock.now().minus(Duration.ofDays(7));
        for (SepayWebhookEventRepository.NoticeRow row : events.findPendingNotices(since)) {
            try {
                boolean sent;
                if (row.getResolution() == null) {
                    sent = mail.sendReceived(row);
                } else if ("IGNORED".equals(row.getResolution())) {
                    // Khoản không thuộc đơn nào: không có gì để báo thêm.
                    sent = true;
                } else {
                    sent = mail.sendResolved(row);
                }
                if (sent) {
                    Instant now = clock.now();
                    transactions.executeWithoutResult(status -> events.markNotified(row.getTransactionId(), now));
                }
            } catch (RuntimeException exception) {
                log.error("Could not notify the customer about SePay transaction {}", row.getTransactionId(), exception);
            }
        }
    }
}
