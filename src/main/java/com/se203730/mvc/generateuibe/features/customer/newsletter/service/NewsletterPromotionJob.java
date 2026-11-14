package com.se203730.mvc.generateuibe.features.customer.newsletter.service;

import com.se203730.mvc.generateuibe.features.customer.newsletter.repository.NewsletterRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class NewsletterPromotionJob {
    private static final Logger log = LoggerFactory.getLogger(NewsletterPromotionJob.class);
    private final NewsletterRepository repository;
    private final NewsletterMailService mail;
    private final TransactionTemplate transactions;

    // Explicit opt-in by the project operator. Off by default to avoid accidental bulk mail in development.
    @Value("${app.newsletter.promotion-notifications-enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.newsletter.poll-ms:300000}")
    public void sendCampaignUpdates() {
        if (!enabled) return;
        try {
            transactions.executeWithoutResult(status -> repository.queueNewCampaigns());
            // At most 10 messages per poll: do not flood the project's Gmail SMTP quota.
            for (int i = 0; i < 10; i++) {
                var delivery = transactions.execute(status -> repository.claimOne());
                if (delivery == null) return;
                boolean sent = mail.sendPromotion(delivery.email(), delivery.title(),
                        delivery.content(), delivery.imageUrl());
                if (sent) transactions.executeWithoutResult(status -> repository.markSent(delivery.deliveryId()));
                else transactions.executeWithoutResult(status -> repository.retryLater(delivery.deliveryId()));
            }
        } catch (RuntimeException ex) {
            log.warn("Newsletter campaign delivery encountered an error; next poll will retry");
        }
    }
}
