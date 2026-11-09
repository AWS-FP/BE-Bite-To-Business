
package com.se203730.mvc.generateuibe.features.ecommerce.pricing.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.dto.response.ProductPriceTierResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.dto.response.ProductPricingResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Product;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity.ProductInventory;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.entity.ProductPrice;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductInventoryRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductPriceRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.pricing.repository.ProductPriceTierRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductPricingService {

    private static final String PUBLISHED = "PUBLISHED";

    private final ProductRepository products;
    private final ProductPriceRepository prices;
    private final ProductPriceTierRepository priceTiers;
    private final ProductInventoryRepository inventories;

    public ProductPricingResponse getPricing(UUID productId) {

        Product product = products.findById(productId)
            .filter(p -> PUBLISHED.equals(p.getStatus()))
            .orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
            );

        ProductPrice price = prices.findById(productId).orElse(null);

        ProductInventory inventory =
            inventories.findById(productId).orElse(null);

        List<ProductPriceTierResponse> tiers = priceTiers
            .findByProductIdOrderByMinQuantityAscIdAsc(productId)
            .stream()
            .map(ProductPriceTierResponse::from)
            .toList();

        // Giá sale chỉ có hiệu lực khi promotion hợp lệ.
        boolean saleActive = price != null
            && price.getSalePrice() != null
            && prices.hasActiveSalePromotion(productId);

        BigDecimal effectiveSalePrice = saleActive
            ? price.getSalePrice()
            : null;

        BigDecimal availableQuantity = inventory == null
            ? null
            : inventory.getAvailableQuantity();

        return new ProductPricingResponse(
            product.getId(),
            product.getSku(),
            product.getUnitCode(),
            "VND",
            product.getMinOrderQuantity(),
            product.getQuantityStep(),
            price != null,
            price == null ? null : price.getListPrice(),
            effectiveSalePrice,
            saleActive,
            tiers,
            inventory != null,
            availableQuantity
        );
    }
}

