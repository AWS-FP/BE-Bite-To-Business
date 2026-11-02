package com.se203730.mvc.generateuibe.features.customer.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

public interface PackageComboItemView {
    UUID getPackageId();
    UUID getOrderItemId();
    String getSku();
    String getNameVi();
    String getNameEn();
    String getUnitCode();
    BigDecimal getQuantity();
}
