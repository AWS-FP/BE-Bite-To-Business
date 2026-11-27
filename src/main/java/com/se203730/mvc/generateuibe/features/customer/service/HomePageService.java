package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.dto.response.*;
import com.se203730.mvc.generateuibe.features.customer.repository.HomePageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomePageService {
    private static final int FEATURED_LIMIT = 8;
    private static final int FLASH_LIMIT = 8;
    private static final int COMBO_LIMIT = 4;
    private static final int PROMOTION_LIMIT = 6;
    private static final int BANNER_LIMIT = 8;
    private final HomePageRepository repository;

    public List<HomePageProductResponse> rankedProducts(List<UUID> categories, String keyword, String sort, int limit) {
        return repository.rankedProducts(categories, keyword, sort, limit);
    }

    public HomePageResponse home() {
        List<HomePageProductResponse> bestSelling = repository.bestSellingProducts(FEATURED_LIMIT);
        List<HomePageProductResponse> deepDiscount = repository.deepDiscountProducts(FEATURED_LIMIT);
        List<HomePageProductResponse> topRated = repository.topRatedProducts(FEATURED_LIMIT);
        List<HomePageProductResponse> featured = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (List<HomePageProductResponse> group : List.of(bestSelling, deepDiscount, topRated)) {
            for (HomePageProductResponse product : group) {
                if (seen.add(product.productId()) && featured.size() < FEATURED_LIMIT) featured.add(product);
            }
        }
        // A new store may have no completed orders or reviews; use recently published products as fallback.
        if (featured.size() < FEATURED_LIMIT) {
            for (HomePageProductResponse product : repository.standardProducts(FEATURED_LIMIT)) {
                if (seen.add(product.productId()) && featured.size() < FEATURED_LIMIT) featured.add(product);
            }
        }
        return new HomePageResponse(List.copyOf(featured), bestSelling, deepDiscount, topRated,
            repository.flashSaleProducts(FLASH_LIMIT), repository.combos(COMBO_LIMIT),
            repository.activePromotions(PROMOTION_LIMIT), repository.banners(BANNER_LIMIT));
    }

    public List<HomePageCategoryResponse> categories() { return repository.categories(); }
    public List<HomePageBannerResponse> banners() { return repository.banners(BANNER_LIMIT); }
}
