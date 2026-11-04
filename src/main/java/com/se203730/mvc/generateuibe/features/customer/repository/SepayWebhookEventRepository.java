package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.SepayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SepayWebhookEventRepository extends JpaRepository<SepayWebhookEvent, Long> {
    boolean existsByGatewayAndReferenceCode(String gateway, String referenceCode);

    List<SepayWebhookEvent> findTop100ByResultOrderByReceivedAtDesc(String result);
}
