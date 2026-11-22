package com.se203730.mvc.generateuibe.features.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.QuoteAddressEmailRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.QuoteAddressEmailRepository.Email;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class QuoteAddressEmailService {
    private static final Logger log = LoggerFactory.getLogger(QuoteAddressEmailService.class);

    @Autowired
    private QuoteAddressEmailRepository emails;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JavaMailSender sender;
    @Autowired
    private TemplateEngine templates;
    @Value("${app.mail.from:}")
    private String from;
    @Value("${app.mail.support-email:}")
    private String supportEmail;
    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Scheduled(fixedDelayString = "${app.quote-address-email.poll-ms:30000}")
    public void sendPendingEmails() {
        for (int i = 0; i < 10; i++) {
            UUID claim = UUID.randomUUID();
            Email email = transactions.execute(status -> emails.claim(claim));
            if (email == null) return;

            try {
                send(email);
                emails.markSent(email.id(), claim);
            } catch (RuntimeException | MessagingException | UnsupportedEncodingException ex) {
                emails.retryLater(email.id(), claim);
                log.warn("Could not send quote address email; it will be retried. Outbox id: {}", email.id());
            }
        }
    }

    private void send(Email email) throws MessagingException, UnsupportedEncodingException {
        if (from.isBlank()) throw new IllegalStateException("SMTP sender is not configured");
        String baseUrl = frontendBaseUrl.strip().replaceAll("/+$", "");
        String quotesUrl = baseUrl + "/quotes";

        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("websiteUrl", baseUrl);
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("quoteNumber", email.quoteNumber());
        context.setVariable("reason", email.reason());
        context.setVariable("quotesUrl", quotesUrl);

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from, "Bite to Business");
        helper.setTo(email.recipient());
        helper.setSubject("Bite to Business - Cập nhật địa chỉ giao hàng");
        // Bản text cho ứng dụng đọc mail không hiển thị HTML.
        String plainText = "Địa chỉ giao hàng trong báo giá " + email.quoteNumber()
                + " cần được cập nhật.\n\nLý do: " + email.reason()
                + "\n\nVui lòng đăng nhập và cập nhật địa chỉ: " + quotesUrl;
        helper.setText(plainText, templates.process("email/quote-address-update", context));
        sender.send(message);
    }
}
