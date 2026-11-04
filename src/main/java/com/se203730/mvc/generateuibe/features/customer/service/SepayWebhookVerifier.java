package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class SepayWebhookVerifier {
    @Value("${app.sepay.webhook-secret:}")
    private String secret;

    public void verify(String timestamp, String signature, byte[] body) {
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        if (body == null || body.length == 0 || body.length > 65536
                || timestamp == null || signature == null || !signature.matches("sha256=[0-9a-f]{64}")) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 300 || seconds > now + 300) {
                throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            byte[] actual = HexFormat.of().parseHex(signature.substring(7));
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_UNAUTHORIZED);
        }
    }
}
