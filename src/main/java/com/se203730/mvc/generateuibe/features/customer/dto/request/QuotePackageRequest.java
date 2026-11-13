package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuotePackageRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 30) String recipientPhone,
        @NotNull @FutureOrPresent LocalDate deliveryDate,
        @NotBlank @Size(max = 100) String deliveryTimeSlot,
        @NotBlank @Size(max = 1000) String address,
        @Size(max = 1000) String note,
        @NotBlank @Pattern(regexp = "HCM|HANOI") String city,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @NotEmpty @Size(max = 200) List<@NotNull @Valid Allocation> items
) {
    public record Allocation(@NotNull UUID quoteItemId,
                             @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantity) {}
}
