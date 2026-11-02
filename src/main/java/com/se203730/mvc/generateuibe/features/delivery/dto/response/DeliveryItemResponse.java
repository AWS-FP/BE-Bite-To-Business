package com.se203730.mvc.generateuibe.features.delivery.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DeliveryItemResponse(
        UUID orderItemId,
        String sku,
        String nameVi,
        String nameEn,
        String unitCode,
        BigDecimal quantity,
        String customerNote,
        List<ComboComponent> comboComponents
) {
    public record ComboComponent(
            String sku,
            String nameVi,
            String nameEn,
            String unitCode,
            BigDecimal quantity
    ) {
    }
}
