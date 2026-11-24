package com.se203730.mvc.generateuibe.features.ecommerce.dto.response;

import java.util.UUID;

public interface CatalogSuggestion {
    String getKind();
    UUID getId();
    String getLabelVi();
    String getLabelEn();
    String getImageUrl();
}
