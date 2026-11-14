package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductVatRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductVatResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductVat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ProductVatService {
    @Autowired
    private ProductVatRepository products;
    @Autowired
    private AccountAccessService accounts;
    @Autowired
    private DatabaseClock clock;

    @Transactional(readOnly = true)
    public ProductVatResponse get(UUID staffId, UUID productId) {
        requireEcommerce(staffId);
        ProductVat product = products.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new ProductVatResponse(product.getId(), product.getSku(), product.getVatRate(), product.getVatUpdatedAt());
    }

    @Transactional
    public ProductVatResponse update(UUID staffId, UUID productId, BigDecimal rate) {
        requireEcommerce(staffId);
        ProductVat product = products.findForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (product.getVatRate() == null || product.getVatRate().compareTo(rate) != 0) {
            Instant changedAt = clock.now();
            product.setVatRate(rate);
            product.setVatUpdatedAt(changedAt);
            product.setUpdatedAt(changedAt);
            products.save(product);
        }
        return new ProductVatResponse(productId, product.getSku(), rate, product.getVatUpdatedAt());
    }

    private void requireEcommerce(UUID staffId) {
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.ECOMMERCE_EXECUTIVE) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
