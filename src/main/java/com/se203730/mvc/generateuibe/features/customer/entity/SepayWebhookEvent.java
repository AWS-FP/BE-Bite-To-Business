package com.se203730.mvc.generateuibe.features.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sepay_webhook_events", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class SepayWebhookEvent {
    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(nullable = false)
    private String gateway;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "sub_account")
    private String subAccount;

    @Column(name = "payment_code")
    private String paymentCode;

    @Column(name = "transfer_type", nullable = false)
    private String transferType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false)
    private String result;

    @Column(name = "review_reason")
    private String reviewReason;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
