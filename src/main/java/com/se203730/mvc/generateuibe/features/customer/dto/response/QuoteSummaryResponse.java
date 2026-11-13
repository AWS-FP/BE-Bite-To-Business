package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.customer.entity.Quote;

import java.time.Instant;
import java.util.UUID;

public record QuoteSummaryResponse(UUID id, String quoteNumber, String status, int revision,
                                   String reviewNote, Instant createdAt, Instant submittedAt, Instant updatedAt) {
    public static QuoteSummaryResponse from(Quote quote) {
        return new QuoteSummaryResponse(quote.getId(), quote.getQuoteNumber(), quote.getStatus(), quote.getRevision(),
                quote.getReviewNote(), quote.getCreatedAt(), quote.getSubmittedAt(), quote.getUpdatedAt());
    }
}
