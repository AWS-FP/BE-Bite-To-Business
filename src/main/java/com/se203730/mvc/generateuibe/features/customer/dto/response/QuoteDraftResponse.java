package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteDraftResponse(
        UUID id, String quoteNumber, String status, int revision, Instant submittedAt, String reviewNote,
        String contactName, String contactPhone, String contactEmail, String companyName, String note,
        UUID productCouponId, UUID shippingCouponId,
        BigDecimal itemsSubtotal, BigDecimal volumeDiscountAmount, BigDecimal saleDiscountAmount,
        BigDecimal couponDiscountAmount, BigDecimal shippingFee, BigDecimal shippingCouponDiscountAmount,
        BigDecimal vatAmount, BigDecimal productVatAmount, String vatStatus,
        BigDecimal totalAmount, BigDecimal estimatedTotalAmount, List<Item> items
) {
    public record Item(UUID id, UUID productId, String sku, String nameVi, String nameEn,
                       String unitCode, BigDecimal quantity, BigDecimal listUnitPrice,
                       BigDecimal volumeUnitPrice, BigDecimal saleUnitPrice,
                       BigDecimal vatRate, BigDecimal productVatAmount) {
        public static Item from(QuoteItem item) {
            return new Item(item.getId(), item.getProductId(), item.getSku(), item.getNameVi(),
                    item.getNameEn(), item.getUnitCode(), item.getQuantity(), item.getListUnitPrice(),
                    item.getVolumeUnitPrice(), item.getSaleUnitPrice(),
                    item.isVatRateConfigured() ? item.getVatRate() : null,
                    item.isVatRateConfigured() ? item.getQuantity().multiply(item.getSaleUnitPrice())
                            .setScale(0, java.math.RoundingMode.HALF_UP).subtract(item.getCouponDiscountAmount())
                            .multiply(item.getVatRate()).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP) : null);
        }
    }

    public static QuoteDraftResponse from(Quote quote, List<QuoteItem> items) {
        boolean vatComplete = !items.isEmpty() && items.stream().allMatch(QuoteItem::isVatRateConfigured);
        BigDecimal total = quote.getItemsSubtotal().subtract(quote.getVolumeDiscountAmount())
                .subtract(quote.getSaleDiscountAmount()).subtract(quote.getCouponDiscountAmount())
                .add(quote.getVatAmount()).add(quote.getShippingFee())
                .subtract(quote.getShippingCouponDiscountAmount());
        return new QuoteDraftResponse(quote.getId(), quote.getQuoteNumber(), quote.getStatus(), quote.getRevision(),
                quote.getSubmittedAt(), quote.getReviewNote(),
                quote.getContactName(), quote.getContactPhone(), quote.getContactEmail(), quote.getCompanyName(), quote.getNote(),
                quote.getCouponId(), quote.getShippingCouponId(),
                quote.getItemsSubtotal(), quote.getVolumeDiscountAmount(), quote.getSaleDiscountAmount(),
                quote.getCouponDiscountAmount(), quote.getShippingFee(), quote.getShippingCouponDiscountAmount(),
                vatComplete ? quote.getVatAmount() : null, quote.getVatAmount(),
                vatComplete ? "COMPLETE" : "PRODUCT_RATES_MISSING",
                null, total, items.stream().map(Item::from).toList());
    }
}
