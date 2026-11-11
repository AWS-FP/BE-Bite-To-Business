package com.se203730.mvc.generateuibe.features.customer.quote.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Quote tổng hợp gồm dòng hàng, gói giao và phân bổ theo từng gói. */
public record QuoteResponse(
        UUID id,
        String quoteNumber,
        String status,
        int revision,
        String contactName,
        String contactPhone,
        String contactEmail,
        String companyName,
        String customerPoReference,
        String note,
        BigDecimal itemsSubtotal,
        BigDecimal volumeDiscountAmount,
        BigDecimal saleDiscountAmount,
        BigDecimal vatAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        Instant submittedAt,
        List<Item> items,
        List<Package> packages
) {
    public record Item(
            UUID id, UUID productId, String sku, String nameVi,
            String unitCode, BigDecimal quantity,
            BigDecimal listUnitPrice, BigDecimal volumeUnitPrice,
            BigDecimal saleUnitPrice, BigDecimal lineSubtotal,
            BigDecimal lineTotal, String customerNote
    ) {}
    public record Package(
            UUID id, String packageName, LocalDate deliveryDate,
            String deliveryAddress, List<Allocation> allocations
    ) {}
    public record Allocation(UUID quoteItemId, BigDecimal quantity) {}
}
