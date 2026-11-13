package com.se203730.mvc.generateuibe.features.ecommerce.promotion.service;

import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.ActiveSaleInfo;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.FlashSaleProductResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.PromotionResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private final PromotionRepository promotions;

    public List<PromotionResponse> getActivePromotions(int limit) {
        return promotions.findActiveStandardPromotions(limit);
    }

    public List<FlashSaleProductResponse> getActiveFlashSales(int limit) {
        return promotions.findActiveFlashSaleProducts(limit);
    }

    public Map<UUID, ActiveSaleInfo> getActiveSalesForProducts(
            Collection<UUID> productIds
    ) {
        return promotions.findActiveSalesByProductIds(productIds);
    }
}
