package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.client.SepayTransactionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mặc định mỗi 10 phút hỏi SePay các khoản tiền vào gần đây; tắt khi chưa có app.sepay.api-token. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SepayReconciliationScheduler {
    private final SepayReconciliationService reconciliation;

    @Scheduled(
            initialDelayString = "${app.sepay.reconcile-initial-delay-ms:60000}",
            fixedDelayString = "${app.sepay.reconcile-poll-ms:600000}")
    public void reconcile() {
        try {
            reconciliation.reconcile();
        } catch (SepayTransactionClient.SepayUnavailableException exception) {
            log.warn("SePay reconciliation skipped: {}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("SePay reconciliation failed", exception);
        }
    }
}
