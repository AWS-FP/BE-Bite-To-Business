package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.dto.request.SepayWebhookRequest;
import com.se203730.mvc.generateuibe.features.customer.service.BankPaymentService;
import com.se203730.mvc.generateuibe.features.customer.service.SepayWebhookVerifier;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
public class SepayWebhookController {
    @Autowired
    private SepayWebhookVerifier verifier;

    @Autowired
    private BankPaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @SecurityRequirements
    @PostMapping(value = "/api/payments/sepay/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Boolean> receive(
            @RequestHeader(value = "X-SePay-Timestamp", required = false)
            String timestamp,
            @RequestHeader(value = "X-SePay-Signature", required = false)
            String signature,
            @RequestBody
            byte[] body) {
        verifier.verify(timestamp, signature, body);
        SepayWebhookRequest request;
        try {
            request = objectMapper.readValue(body, SepayWebhookRequest.class);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // SePay dùng id 0 cho nút "Gửi thử"; chỉ xác minh kết nối, không ghi thanh toán.
        if (request != null && Long.valueOf(0).equals(request.id())) {
            paymentService.checkWebhookConfiguration();
            return Map.of("success", true);
        }
        paymentService.receive(request);
        return Map.of("success", true);
    }
}
