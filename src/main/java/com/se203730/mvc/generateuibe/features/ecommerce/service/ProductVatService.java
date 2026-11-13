package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductVatRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductVatRateChangeRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductVatResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductVat;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductVatRateChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProductVatService {
    @Autowired
    private ProductVatRepository products;
    @Autowired
    private AccountAccessService accounts;
    @Autowired
    private ProductVatRateChangeRepository changes;
    @Autowired
    private DatabaseClock clock;

    @Transactional(readOnly = true)
    public ProductVatResponse get(UUID staffId, UUID productId) {
        requireEcommerce(staffId);
        ProductVat product = products.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new ProductVatResponse(product.getId(), product.getSku(), product.getVatRate());
    }

    @Transactional
    public ProductVatResponse update(UUID staffId, UUID productId, BigDecimal rate) {
        requireEcommerce(staffId);
        ProductVat product = products.findForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (product.getVatRate() == null || product.getVatRate().compareTo(rate) != 0) {
            ProductVatRateChange change = new ProductVatRateChange();
            change.setId(UUID.randomUUID());
            change.setProductId(productId);
            change.setPreviousRate(product.getVatRate());
            change.setNewRate(rate);
            change.setChangedBy(staffId);
            change.setChangedAt(clock.now());
            product.setVatRate(rate);
            product.setUpdatedAt(change.getChangedAt());
            products.save(product);
            changes.save(change);
        }
        return new ProductVatResponse(productId, product.getSku(), rate);
    }

    private void requireEcommerce(UUID staffId) {
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.ECOMMERCE_EXECUTIVE) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
