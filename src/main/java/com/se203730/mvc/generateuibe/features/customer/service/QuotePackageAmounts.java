package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.entity.Quote;
import com.se203730.mvc.generateuibe.features.customer.entity.QuoteItem;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackageItem;
import com.se203730.mvc.generateuibe.features.customer.repository.QuotePackageItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotePackageAmounts {
    private final QuotePackageItemRepository allocations;

    public record Amount(BigDecimal merchandise, BigDecimal productCoupon, BigDecimal vat,
                         BigDecimal shippingCoupon, BigDecimal shippingFee, BigDecimal total) {}

    public Map<UUID, Amount> calculate(Quote quote, List<QuoteItem> lines, List<QuotePackage> packages) {
        List<QuotePackage> parcels = new ArrayList<>(packages);
        parcels.sort(Comparator.comparing(QuotePackage::getId));
        Map<UUID, BigDecimal[]> sums = new HashMap<>();
        Map<UUID, List<QuotePackageItem>> assigned = new HashMap<>();
        for (QuotePackage parcel : parcels) {
            sums.put(parcel.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            for (QuotePackageItem part : allocations.findAllByQuotePackageId(parcel.getId())) {
                assigned.computeIfAbsent(part.getQuoteItemId(), ignored -> new ArrayList<>()).add(part);
            }
        }
        for (QuoteItem line : lines) {
            BigDecimal quantityLeft = line.getQuantity();
            BigDecimal merchandiseLeft = line.getQuantity().multiply(line.getSaleUnitPrice()).setScale(0, RoundingMode.HALF_UP);
            BigDecimal couponLeft = line.getCouponDiscountAmount();
            BigDecimal vatLeft = merchandiseLeft.subtract(couponLeft).multiply(line.getVatRate())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            List<QuotePackageItem> parts = new ArrayList<>(assigned.getOrDefault(line.getId(), List.of()));
            parts.sort(Comparator.comparing(QuotePackageItem::getQuotePackageId));
            for (QuotePackageItem part : parts) {
                BigDecimal merchandise = portion(merchandiseLeft, part.getQuantity(), quantityLeft);
                BigDecimal coupon = portion(couponLeft, merchandise, merchandiseLeft);
                BigDecimal vat = portion(vatLeft, merchandise.subtract(coupon), merchandiseLeft.subtract(couponLeft));
                BigDecimal[] amount = sums.get(part.getQuotePackageId());
                amount[0] = amount[0].add(merchandise);
                amount[1] = amount[1].add(coupon);
                amount[2] = amount[2].add(vat);
                quantityLeft = quantityLeft.subtract(part.getQuantity());
                merchandiseLeft = merchandiseLeft.subtract(merchandise);
                couponLeft = couponLeft.subtract(coupon);
                vatLeft = vatLeft.subtract(vat);
            }
        }
        BigDecimal shippingLeft = quote.getShippingFee();
        BigDecimal discountLeft = quote.getShippingCouponDiscountAmount();
        Map<UUID, Amount> result = new HashMap<>();
        for (QuotePackage parcel : parcels) {
            BigDecimal discount = portion(discountLeft, parcel.getShippingFee(), shippingLeft);
            BigDecimal[] sum = sums.get(parcel.getId());
            BigDecimal total = sum[0].subtract(sum[1]).add(sum[2]).add(parcel.getShippingFee()).subtract(discount);
            result.put(parcel.getId(), new Amount(sum[0], sum[1], sum[2], discount, parcel.getShippingFee(), total));
            shippingLeft = shippingLeft.subtract(parcel.getShippingFee());
            discountLeft = discountLeft.subtract(discount);
        }
        return result;
    }

    private BigDecimal portion(BigDecimal amount, BigDecimal part, BigDecimal total) {
        if (amount.signum() == 0 || total.signum() == 0) return BigDecimal.ZERO;
        return amount.multiply(part).divide(total, 0, RoundingMode.HALF_UP).min(amount);
    }
}
