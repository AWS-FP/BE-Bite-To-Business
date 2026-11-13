package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.repository.QuoteCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class QuotePriceService {
    @Autowired
    private QuoteCatalogRepository catalog;

    public record Price(QuoteCatalogRepository.CatalogPrice product, BigDecimal volume, BigDecimal sale) {}

    public Price calculate(UUID productId, BigDecimal quantity) {
        var product = catalog.findPrice(productId, quantity)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (quantity.compareTo(product.getMinQuantity()) < 0
                || quantity.remainder(product.getQuantityStep()).signum() != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        BigDecimal volume = product.getTierPrice() == null ? product.getListPrice() : product.getTierPrice();
        if (volume.compareTo(product.getListPrice()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        BigDecimal sale = volume;
        if (Boolean.TRUE.equals(product.getPromotionActive())) {
            if (product.getSaleDiscountPercent() != null) {
                sale = volume.multiply(BigDecimal.valueOf(100).subtract(product.getSaleDiscountPercent()))
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            } else if (product.getSalePrice() != null) {
                sale = volume.multiply(product.getSalePrice())
                        .divide(product.getListPrice(), 0, RoundingMode.HALF_UP);
            }
        }
        return new Price(product, volume, sale);
    }
}
