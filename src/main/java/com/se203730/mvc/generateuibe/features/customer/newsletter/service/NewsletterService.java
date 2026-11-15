
package com.se203730.mvc.generateuibe.features.customer.newsletter.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.newsletter.dto.request.NewsletterSubscribeRequest;
import com.se203730.mvc.generateuibe.features.customer.newsletter.dto.response.NewsletterSubscriptionResponse;
import com.se203730.mvc.generateuibe.features.customer.newsletter.repository.NewsletterRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NewsletterService {

    private static final Logger log =
        LoggerFactory.getLogger(NewsletterService.class);

    private final NewsletterRepository repository;
    private final NewsletterMailService mail;

    @Transactional
    public NewsletterSubscriptionResponse subscribe(
        NewsletterSubscribeRequest request) {

        // 1. Validate request and explicit consent
        if (request == null ||
            request.email() == null ||
            !request.consent()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 2. Normalize email
        String email = request.email()
            .trim()
            .toLowerCase(Locale.ROOT);

        // 3. Validate email format
        if (email.isBlank() ||
            !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 4. Check database availability
        if (!repository.isReady()) {
            throw new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE
            );
        }

        // 5. Save or reactivate subscription
        boolean newlyActive = repository.subscribe(email);

        // 6. Send welcome email only after DB commit
        if (newlyActive &&
            TransactionSynchronizationManager
                .isSynchronizationActive()) {

            TransactionSynchronizationManager
                .registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                mail.sendWelcome(email);
                            } catch (RuntimeException ex) {
                                log.warn(
                                    "Newsletter welcome email delivery failed",
                                    ex
                                );
                            }
                        }
                    }
                );
        }

        // 7. Return success response
        return new NewsletterSubscriptionResponse(
            "Subscription recorded.",
            email
        );
    }
}
