package com.se203730.mvc.generateuibe.features.customer.invoice.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only invoice status of an order owned by the authenticated customer.
 * NOT_ISSUED means no official electronic invoice record exists yet.
 * pdfUrl is exposed only for a legitimately ISSUED invoice.
 */
public record CustomerInvoiceResponse(
        UUID orderId,
        String orderNumber,
        String invoiceStatus,
        String invoiceNumber,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String pdfUrl,
        Instant issuedAt,
        boolean downloadable
) {}
