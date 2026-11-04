package com.se203730.mvc.generateuibe.features.customer.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SepayWebhookRequest(
        Long id,
        String gateway,
        String accountNumber,
        String subAccount,
        String code,
        String content,
        String transferType,
        BigDecimal transferAmount,
        String referenceCode
) {
}
