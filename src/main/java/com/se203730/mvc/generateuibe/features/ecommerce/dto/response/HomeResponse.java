package com.se203730.mvc.generateuibe.features.ecommerce.dto.response;

import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.FlashSaleProductResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.PromotionResponse;

import java.util.List;

public record HomeResponse(
        List<HomeProductResponse> featuredProducts,
        List<FlashSaleProductResponse> flashSaleProducts,
        List<HomeComboResponse> combos,
        List<PromotionResponse> activePromotions
) {}
