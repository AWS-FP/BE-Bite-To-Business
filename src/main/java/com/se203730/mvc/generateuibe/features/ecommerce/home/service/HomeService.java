package com.se203730.mvc.generateuibe.features.ecommerce.home.service;

import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeComboResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeProductResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.home.dto.response.HomeResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.home.repository.HomeRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.dto.response.ActiveSaleInfo;
import com.se203730.mvc.generateuibe.features.ecommerce.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final int FEATURED_LIMIT = 8;
    private static final int FLASH_SALE_LIMIT = 8;
    private static final int COMBO_LIMIT = 4;
    private static final int PROMOTION_LIMIT = 6;

    private final HomeRepository homeRepository;
    private final PromotionService promotionService;

    public HomeResponse getHome() {
        List<HomeProductResponse> featured = homeRepository.findFeaturedProducts(FEATURED_LIMIT);
        List<HomeComboResponse> combos = homeRepository.findCombos(COMBO_LIMIT);

        // One query for sale metadata shared by featured products and combos.
        List<UUID> ids = new ArrayList<>();
        featured.forEach(p -> ids.add(p.productId()));
        combos.forEach(c -> ids.add(c.productId()));
        Map<UUID, ActiveSaleInfo> sales = promotionService.getActiveSalesForProducts(ids);

        List<HomeProductResponse> enrichedFeatured = featured.stream().map(product -> {
            ActiveSaleInfo sale = sales.get(product.productId());
            return new HomeProductResponse(
                    product.productId(), product.sku(), product.nameVi(),
                    product.nameEn(), product.imageUrl(), product.unitCode(),
                    product.listPrice(), sale == null ? null : sale.salePrice(),
                    product.availableQuantity(),
                    sale == null ? null : sale.promotionId(),
                    sale == null ? null : sale.promotionNameVi(),
                    sale == null ? null : sale.promotionEndsAt()
            );
        }).toList();

        List<HomeComboResponse> enrichedCombos = combos.stream().map(combo -> {
            ActiveSaleInfo sale = sales.get(combo.productId());
            return new HomeComboResponse(
                    combo.productId(), combo.sku(), combo.nameVi(), combo.nameEn(),
                    combo.imageUrl(), combo.servingCount(), combo.listPrice(),
                    sale == null ? null : sale.salePrice(),
                    combo.availableQuantity(), combo.items()
            );
        }).toList();

        return new HomeResponse(
                enrichedFeatured,
                promotionService.getActiveFlashSales(FLASH_SALE_LIMIT),
                enrichedCombos,
                promotionService.getActivePromotions(PROMOTION_LIMIT)
        );
    }
}
