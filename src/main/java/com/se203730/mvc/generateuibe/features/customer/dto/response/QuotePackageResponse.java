package com.se203730.mvc.generateuibe.features.customer.dto.response;

import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackageItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotePackageResponse(UUID id, String name, String recipientName, String recipientPhone,
                                   LocalDate deliveryDate, String deliveryTimeSlot, String address, String note,
                                   String city, BigDecimal latitude, BigDecimal longitude,
                                   Integer distanceMetres, BigDecimal shippingFee, String addressStatus,
                                   List<Allocation> items) {
    public record Allocation(UUID quoteItemId, BigDecimal quantity) {}

    public static QuotePackageResponse from(QuotePackage parcel, List<QuotePackageItem> items) {
        return new QuotePackageResponse(parcel.getId(), parcel.getPackageName(), parcel.getRecipientName(),
                parcel.getRecipientPhone(), parcel.getDeliveryDate(), parcel.getDeliveryTimeSlot(),
                parcel.getDeliveryAddress(), parcel.getDeliveryNote(), parcel.getCity(), parcel.getLatitude(),
                parcel.getLongitude(), parcel.getDistanceMetres(), parcel.getShippingFee(), parcel.getAddressStatus(),
                items.stream().map(item -> new Allocation(item.getQuoteItemId(), item.getQuantity())).toList());
    }
}
