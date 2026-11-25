package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.client.SepayTransactionClient;
import com.se203730.mvc.generateuibe.features.customer.dto.request.SepayWebhookRequest;
import com.se203730.mvc.generateuibe.features.customer.repository.SepayWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Đối soát định kỳ với sao kê SePay: khoản tiền vào mà webhook chưa báo (server tắt, mạng lỗi…)
 * được ghi nhận bù qua đúng luồng webhook. Mỗi giao dịch chạy trong transaction riêng của
 * BankPaymentService.receive, nên một dòng lỗi không chặn các dòng còn lại.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SepayReconciliationService {
    private static final int MAX_ROWS = 1000;

    private final SepayTransactionClient sepay;
    private final BankPaymentService payments;
    private final SepayWebhookEventRepository events;
    private final DatabaseClock clock;

    @Value("${app.sepay.bank-account:}")
    private String bankAccount;

    @Value("${app.sepay.reconcile-lookback-hours:48}")
    private long lookbackHours;

    /** Trả về số giao dịch được ghi nhận bù trong lượt này. */
    public int reconcile() {
        if (!sepay.configured() || bankAccount == null || bankAccount.isBlank()) {
            return 0;
        }
        Instant since = clock.now().minus(Duration.ofHours(Math.max(1, lookbackHours)));
        List<SepayTransactionClient.Transaction> rows = sepay.incomingSince(bankAccount.trim(), since, MAX_ROWS);
        int recovered = 0;
        for (SepayTransactionClient.Transaction row : rows) {
            if (events.existsById(row.id())) {
                continue;
            }
            SepayWebhookRequest request = new SepayWebhookRequest(row.id(),
                    row.gateway() == null ? "UNKNOWN" : row.gateway(),
                    row.accountNumber() == null ? bankAccount.trim() : row.accountNumber(),
                    row.subAccount(), null, row.content(), "in", row.amountIn(), row.referenceCode());
            try {
                payments.receive(request, "RECONCILIATION", row.paidAt());
                recovered++;
            } catch (DataIntegrityViolationException exception) {
                // Webhook vừa ghi cùng giao dịch ở instance khác: bỏ qua.
                log.debug("SePay transaction {} was recorded concurrently", row.id());
            } catch (RuntimeException exception) {
                log.error("Could not reconcile SePay transaction {}", row.id(), exception);
            }
        }
        if (recovered > 0) {
            log.warn("SePay reconciliation recorded {} transaction(s) the webhook had missed", recovered);
        }
        return recovered;
    }
}
