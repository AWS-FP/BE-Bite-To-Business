package com.se203730.mvc.generateuibe.features.customer.quote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record QuotePackageRequest(
        @NotBlank @Size(max = 200) String packageName,
        @NotNull LocalDate deliveryDate,
        @NotBlank @Size(max = 2000) String deliveryAddress
) {}
