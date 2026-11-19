package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.features.logistics.entity.ProductRequestResponse;
import com.se203730.mvc.generateuibe.features.logistics.repository.ProductRequestResponseRepository;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class ProductRequestEmailService {
    private final ProductRequestResponseRepository responses;
    private final TransactionTemplate transactions;
    private final JavaMailSender mail;
    private final TemplateEngine templates;
    private final ThreadPoolTaskExecutor executor;
    private final DatabaseClock clock;
    private final Semaphore slots = new Semaphore(3);

    @Value("${spring.mail.username:}")
    private String from;
    @Value("${app.frontend-base-url:${app.frontend.base-url:http://localhost:5173}}")
    private String websiteUrl;

    public ProductRequestEmailService(ProductRequestResponseRepository responses, TransactionTemplate transactions,
            JavaMailSender mail, TemplateEngine templates, DatabaseClock clock,
            @Qualifier("productRequestEmailExecutor") ThreadPoolTaskExecutor executor) {
        this.responses = responses;
        this.transactions = transactions;
        this.mail = mail;
        this.templates = templates;
        this.clock = clock;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.product-request-email.poll-ms:3000}")
    public void processQueue() {
        for (int i = 0; i < 3; i++) {
            if (!slots.tryAcquire()) return;
            try {
                executor.execute(() -> {
                    try {
                        sendNext();
                    } catch (Exception ex) {
                        log.warn("Product request email worker failed: {}", ex.getClass().getSimpleName());
                    } finally {
                        slots.release();
                    }
                });
            } catch (RuntimeException ex) {
                slots.release();
                log.warn("Product request email worker unavailable: {}", ex.getClass().getSimpleName());
                return;
            }
        }
    }

    @TransactionalEventListener
    public void onEmailQueued(ProductRequestEmailQueued event) {
        processQueue();
    }

    private void sendNext() {
        UUID token = UUID.randomUUID();
        ProductRequestResponse response = transactions.execute(status -> {
            ProductRequestResponse item = responses.findNextEmail().orElse(null);
            if (item == null) return null;
            item.setEmailClaimToken(token);
            item.setEmailClaimedUntil(clock.now().plus(10, ChronoUnit.MINUTES));
            return responses.saveAndFlush(item);
        });
        if (response == null) return;

        try {
            sendEmail(response);
        } catch (Exception ex) {
            log.warn("Product request email failed; responseId={}, errorType={}", response.getId(), ex.getClass().getSimpleName());
            long delay = Math.min(3600, 30L << Math.min(response.getEmailAttempts(), 7));
            transactions.executeWithoutResult(status -> responses.retryEmail(response.getId(), token, delay));
            return;
        }
        int updated = transactions.execute(status -> responses.markEmailSent(response.getId(), token));
        if (updated != 1) {
            log.warn("Email sent but claim is no longer valid; responseId={}", response.getId());
        }
    }

    private void sendEmail(ProductRequestResponse response) throws Exception {
        if (from.isBlank()) throw new IllegalStateException("SMTP sender is not configured");
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("websiteUrl", websiteUrl);
        context.setVariable("supportEmail", from);
        context.setVariable("showBrandPhotos", false);
        context.setVariable("responseNote", response.getResponseNote());
        context.setVariable("productName", response.getProductName());
        context.setVariable("productAddedDate", response.getProductAddedAt());
        context.setVariable("sellingPrice", response.getSellingPrice());
        context.setVariable("availableQuantity", response.getAvailableQuantity());
        boolean added = response.getAddedProductId() != null;
        if (added) {
            context.setVariable("productUrl", websiteUrl.replaceAll("/+$", "") + "/products/" + response.getAddedProductId());
        }
        var message = mail.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from, "Bite to Business");
        helper.setTo(response.getRecipientEmail());
        helper.setSubject(added ? "Bite to Business - Sản phẩm đã được bổ sung" : "Bite to Business - Phản hồi yêu cầu sản phẩm");
        helper.setText(templates.process(added ? "email/product-request-added" : "email/product-request-response", context), true);
        helper.addInline("btb-logo", new ClassPathResource("email/images/brand-logo.png"), "image/png");
        mail.send(message);
    }
}
