package com.se203730.mvc.generateuibe.features.customer.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc giao dịch gần đây qua SePay User API v1 (GET /userapi/transactions/list) để đối soát
 * các khoản webhook bị sót. Giới hạn của SePay là 2 request/giây nên job chỉ gọi một lần mỗi lượt.
 */
@Component
@Slf4j
public class SepayTransactionClient {
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter SEPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient client;

    @Value("${app.sepay.api-token:}")
    private String apiToken;

    @Value("${app.sepay.api-base-url:https://my.sepay.vn/userapi}")
    private String baseUrl;

    public SepayTransactionClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return apiToken != null && !apiToken.isBlank();
    }

    /** Giao dịch của tài khoản từ thời điểm since trở đi, tối đa limit dòng. */
    public List<Transaction> incomingSince(String accountNumber, Instant since, int limit) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/+$", "") + "/transactions/list")
                .queryParam("account_number", accountNumber)
                .queryParam("transaction_date_min", SEPAY_TIME.format(since.atZone(BANK_ZONE)))
                .queryParam("limit", limit)
                .build().encode().toUriString();
        JsonNode body;
        try {
            body = client.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken.trim())
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new SepayUnavailableException("SePay transaction list request failed", exception);
        }
        if (body == null || body.path("status").asInt(0) != 200) {
            throw new SepayUnavailableException("SePay transaction list returned an error", null);
        }
        List<Transaction> result = new ArrayList<>();
        for (JsonNode row : body.path("transactions")) {
            Transaction transaction = parse(row);
            if (transaction != null) {
                result.add(transaction);
            }
        }
        return result;
    }

    private Transaction parse(JsonNode row) {
        try {
            long id = Long.parseLong(row.path("id").asText("").trim());
            BigDecimal amountIn = decimal(row.path("amount_in").asText("0"));
            if (id <= 0 || amountIn.signum() <= 0) {
                return null;
            }
            // Mỗi khoản VND là số nguyên; SePay trả dạng "18067000.00".
            BigDecimal whole = amountIn.stripTrailingZeros();
            if (whole.scale() > 0) {
                return null;
            }
            Instant paidAt = null;
            String date = row.path("transaction_date").asText("");
            if (!date.isBlank()) {
                paidAt = LocalDateTime.parse(date.trim(), SEPAY_TIME).atZone(BANK_ZONE).toInstant();
            }
            return new Transaction(id, text(row, "bank_brand_name"), text(row, "account_number"),
                    text(row, "sub_account"), text(row, "transaction_content"),
                    whole.setScale(0), text(row, "reference_number"), paidAt);
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException exception) {
            log.warn("Skipped a SePay transaction row that could not be parsed");
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value.trim());
    }

    private static String text(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    public record Transaction(
            long id,
            String gateway,
            String accountNumber,
            String subAccount,
            String content,
            BigDecimal amountIn,
            String referenceCode,
            Instant paidAt
    ) {
    }

    public static class SepayUnavailableException extends RuntimeException {
        public SepayUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
