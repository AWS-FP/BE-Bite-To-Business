package com.se203730.mvc.generateuibe.features.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Explicit consent is required; no newsletter messages are sent by this API. */
public record NewsletterSubscribeRequest(
        @NotBlank @Email @Size(max=254) String email,
        boolean consent
) {}
