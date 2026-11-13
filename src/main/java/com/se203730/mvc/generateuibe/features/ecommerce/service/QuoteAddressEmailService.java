package com.se203730.mvc.generateuibe.features.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.QuoteAddressEmailRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.QuoteAddressEmailRepository.Email;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Value("${spring.mail.username:}")
    private String from;
    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Scheduled(fixedDelayString = "${app.quote-address-email.poll-ms:30000}")
    public void sendPendingEmails() {
        for (int i = 0; i < 10; i++) {
            UUID claim = UUID.randomUUID();
            Email email = transactions.execute(status -> emails.claim(claim));
            if (email == null) return;

            try {
                if (from.isBlank()) throw new IllegalStateException("SMTP sender is not configured");
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(email.recipient());
                message.setSubject("Bite to Business - Cập nhật địa chỉ giao hàng");
                message.setText("Địa chỉ giao hàng trong báo giá " + email.quoteNumber()
                        + " cần được cập nhật.\n\nLý do: " + email.reason()
                        + "\n\nVui lòng đăng nhập và cập nhật địa chỉ: "
                        + frontendBaseUrl.strip().replaceAll("/+$", "") + "/quotes");
                sender.send(message);
                emails.markSent(email.id(), claim);
            } catch (RuntimeException ex) {
                emails.retryLater(email.id(), claim);
                log.warn("Could not send quote address email; it will be retried. Outbox id: {}", email.id());
            }
        }
    }

}
