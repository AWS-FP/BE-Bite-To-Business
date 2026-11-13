package com.se203730.mvc.generateuibe.features.ecommerce.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote_address_reviews", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class QuoteAddressReview {
    @Id
    private UUID id;
    private UUID quoteId;
    private UUID quotePackageId;
    private int quoteRevision;
    private UUID reviewedBy;
    private String result;
    private String reason;
    private Instant reviewedAt;
}
