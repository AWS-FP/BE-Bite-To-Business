package com.se203730.mvc.generateuibe.features.customer.quote.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record QuoteContactRequest(
        @Size(max = 200) String contactName,
        @Size(max = 15) String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 200) String companyName,
        @Size(max = 100) String customerPoReference,
        @Size(max = 2000) String note
) {}
