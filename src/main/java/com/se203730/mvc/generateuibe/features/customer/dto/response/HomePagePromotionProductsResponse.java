package com.se203730.mvc.generateuibe.features.customer.dto.response;

import java.util.List;

public record HomePagePromotionProductsResponse(HomePagePromotionResponse promotion,
    List<HomePageProductResponse> items, int page, int size, boolean hasNext) {}
