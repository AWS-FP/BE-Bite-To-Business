package com.se203730.mvc.generateuibe.features.ecommerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.PromotionExpiryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Close expired published campaigns without depending on a staff member opening
 * the E-commerce console. The query is atomic and safe when multiple ECS tasks
 * execute the scheduler simultaneously.
 *
 * Product-wide sale_price must NOT be zeroed here: the same product may belong
 * to another promotion and pricing APIs already check campaign validity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionExpiryJob {
    private final PromotionExpiryRepository repository;

    @Scheduled(
        fixedDelayString = "${app.promotion.expiry-poll-ms:60000}",
        initialDelayString = "${app.promotion.expiry-initial-ms:1000}"
    )
    @Transactional
    public void expirePublishedCampaigns() {
        int expired = repository.disableExpiredPublishedCampaigns();
        if (expired > 0) {
            log.info("Automatically disabled {} expired promotions", expired);
        }
    }
}
