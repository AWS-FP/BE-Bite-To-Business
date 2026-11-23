package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.util.List;
public record HomePageResponse(
    List<HomePageProductResponse> featuredProducts,
    List<HomePageProductResponse> bestSellingProducts,
    List<HomePageProductResponse> deepDiscountProducts,
    List<HomePageProductResponse> topRatedProducts,
    List<HomePageProductResponse> flashSaleProducts,
    List<HomePageComboResponse> combos,
    List<HomePagePromotionResponse> activePromotions,
    List<HomePageBannerResponse> banners
) {}
