package com.se203730.mvc.generateuibe.features.ecommerce.dto.request;
import jakarta.validation.constraints.NotBlank;
public record ExecutiveProductStatusRequest(@NotBlank String status) {}
