package com.se203730.mvc.generateuibe.features.ai.service.tools;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.features.ai.service.context.UiProfileService.UiProfile;
import com.se203730.mvc.generateuibe.features.ai.service.tools.ProductSearchTool.SearchResult;
import com.se203730.mvc.generateuibe.features.customer.dto.response.FavoriteProductResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageCategoryResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageComboResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageProductResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageResponse;
import com.se203730.mvc.generateuibe.features.customer.service.FavoriteService;
import com.se203730.mvc.generateuibe.features.customer.service.HomePageService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPricingResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductPricingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool dữ liệu cho chế độ dựng trang: gom kết quả tìm kiếm, các khối trang chủ, danh mục và yêu thích
 * (chỉ của chính Customer đang đăng nhập) từ service có sẵn. Data model có đủ giá/tồn để renderer bind;
 * summary gửi cho model chỉ có số phần tử và tên.
 */
@Component
@Slf4j
public class PageDataTool {
    private static final int LIST_LIMIT = 8;
    private static final int SUMMARY_NAMES = 6;

    private final ProductSearchTool search;
    private final HomePageService home;
    private final FavoriteService favorites;
    private final ProductPricingService pricing;
    private final ReorderTool reorder;

    public PageDataTool(ProductSearchTool search, HomePageService home, FavoriteService favorites,
            ProductPricingService pricing, ReorderTool reorder) {
        this.search = search;
        this.home = home;
        this.favorites = favorites;
        this.pricing = pricing;
        this.reorder = reorder;
    }

    /**
     * @param productNames tên sản phẩm theo ID trong các danh sách cá nhân, để tóm tắt hồ sơ thói quen cho prompt
     */
    public record PageData(Map<String, Object> dataModel, Map<String, Object> summary, Map<String, Integer> lists,
            String query, String sort, Map<UUID, String> productNames) {
    }

    /**
     * @param customerId Customer đang đăng nhập (null với Guest) — chỉ dùng để đọc dữ liệu của chính họ
     * @param profile    hồ sơ thói quen (có thể rỗng); quyết định danh sách "xem gần đây" và "dành cho bạn"
     */
    public PageData build(List<String> keywords, String sort, String locale, UUID customerId, UiProfile profile) {
        boolean en = "en".equals(locale);
        Map<String, Object> dataModel = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Integer> lists = new LinkedHashMap<>();

        SearchResult found = keywords.isEmpty() && !"newest".equals(sort)
                ? search.empty()
                : search.search(keywords, sort, keywords.isEmpty() ? LIST_LIMIT : 12, locale);
        dataModel.putAll(found.dataModel());
        summary.put("query", found.dataModel().get("query"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) found.dataModel().get("products");
        putList("/products", products, summary, lists);

        Map<String, Object> homeModel = new LinkedHashMap<>();
        try {
            HomePageResponse page = home.home();
            homeModel.put("featured", products(page.featuredProducts(), en));
            homeModel.put("bestSelling", products(page.bestSellingProducts(), en));
            homeModel.put("deepDiscount", products(page.deepDiscountProducts(), en));
            homeModel.put("topRated", products(page.topRatedProducts(), en));
            homeModel.put("flashSale", products(page.flashSaleProducts(), en));
            homeModel.put("combos", combos(page.combos(), en));
            homeModel.put("promotions", page.activePromotions().stream().map(promotion -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", promotion.id().toString());
                row.put("name", en && promotion.nameEn() != null ? promotion.nameEn() : promotion.nameVi());
                row.put("content", en && promotion.contentEn() != null ? promotion.contentEn() : promotion.contentVi());
                row.put("startsAt", promotion.startsAt());
                row.put("endsAt", promotion.endsAt());
                return row;
            }).toList());
            homeModel.put("banners", page.banners().stream().map(banner -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", banner.id().toString());
                row.put("title", en && banner.titleEn() != null ? banner.titleEn() : banner.titleVi());
                row.put("description", en && banner.descriptionEn() != null ? banner.descriptionEn() : banner.descriptionVi());
                row.put("imageUrl", banner.imageUrl());
                row.put("targetUrl", banner.targetUrl());
                return row;
            }).toList());
        } catch (RuntimeException exception) {
            log.warn("Home data unavailable for AI page: {}", exception.getClass().getSimpleName());
        }
        dataModel.put("home", homeModel);
        for (String key : List.of("featured", "bestSelling", "deepDiscount", "topRated", "flashSale", "combos",
                "promotions", "banners")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) homeModel.getOrDefault(key, List.of());
            putList("/home/" + key, rows, summary, lists);
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        try {
            for (HomePageCategoryResponse category : home.categories()) {
                if (category.parentId() == null) {
                    categories.add(category(category, en));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Categories unavailable for AI page: {}", exception.getClass().getSimpleName());
        }
        dataModel.put("categories", categories);
        putList("/categories", categories, summary, lists);

        List<Map<String, Object>> favoriteRows = customerId == null ? List.of() : favorites(customerId, en);
        dataModel.put("favorites", favoriteRows);
        putList("/favorites", favoriteRows, summary, lists);

        // Danh sách cá nhân: xem gần đây (lịch sử thật), dành cho bạn (chỉ khi đủ bằng chứng), mua lại (Customer).
        Map<UUID, String> names = new LinkedHashMap<>();
        List<Map<String, Object>> recent = search.byIds(profile.recentlyViewed(), locale);
        List<UUID> forYouIds = profile.personalized()
                ? profile.interested().stream().filter(id -> !profile.avoided().contains(id)).toList()
                : List.of();
        List<Map<String, Object>> forYou = search.byIds(forYouIds, locale);
        List<Map<String, Object>> reorderRows = reorder.reorderItems(customerId, locale);
        dataModel.put("recentlyViewed", recent);
        dataModel.put("forYou", forYou);
        dataModel.put("reorder", reorderRows);
        putList("/recentlyViewed", recent, summary, lists);
        putList("/forYou", forYou, summary, lists);
        putList("/reorder", reorderRows, summary, lists);
        for (List<Map<String, Object>> rows : List.of(recent, forYou, reorderRows)) {
            rows.forEach(row -> names.put(UUID.fromString((String) row.get("productId")), (String) row.get("name")));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) found.dataModel().get("query");
        return new PageData(dataModel, summary, lists, found.query(), String.valueOf(query.get("sort")), names);
    }

    private static void putList(String path, List<Map<String, Object>> rows, Map<String, Object> summary,
            Map<String, Integer> lists) {
        lists.put(path, rows.size());
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("count", rows.size());
        info.put("items", rows.stream().limit(SUMMARY_NAMES).map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", row.getOrDefault("name", row.get("title")));
            if (row.containsKey("availableQuantity")) {
                Object available = row.get("availableQuantity");
                item.put("inStock", available instanceof Number number && number.doubleValue() > 0);
                item.put("onSale", Boolean.TRUE.equals(row.get("saleActive")));
            }
            return item;
        }).toList());
        summary.put(path, info);
    }

    private static List<Map<String, Object>> products(List<HomePageProductResponse> rows, boolean en) {
        return rows.stream().limit(LIST_LIMIT).map(product -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", product.productId().toString());
            item.put("sku", product.sku());
            item.put("name", en && product.nameEn() != null ? product.nameEn() : product.nameVi());
            item.put("spec", en && product.packageSpecEn() != null ? product.packageSpecEn() : product.packageSpecVi());
            item.put("origin", null);
            item.put("unitCode", product.unitCode());
            item.put("imageUrl", product.imageUrl());
            item.put("currency", "VND");
            item.put("priceAvailable", product.listPrice() != null);
            item.put("listPrice", product.listPrice());
            item.put("salePrice", product.salePrice());
            item.put("saleActive", product.salePrice() != null);
            item.put("saleEndsAt", product.promotionEndsAt());
            item.put("minOrderQuantity", product.minOrderQuantity());
            item.put("quantityStep", product.quantityStep());
            item.put("priceTiers", List.of());
            item.put("inventoryAvailable", product.availableQuantity() != null);
            item.put("availableQuantity", product.availableQuantity());
            item.put("averageRating", product.averageRating());
            item.put("reviewCount", product.reviewCount());
            return item;
        }).toList();
    }

    private static List<Map<String, Object>> combos(List<HomePageComboResponse> rows, boolean en) {
        return rows.stream().map(combo -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", combo.productId().toString());
            item.put("name", en && combo.nameEn() != null ? combo.nameEn() : combo.nameVi());
            item.put("imageUrl", combo.imageUrl());
            item.put("servingCount", combo.servingCount());
            item.put("listPrice", combo.listPrice());
            item.put("salePrice", combo.salePrice());
            item.put("saleActive", combo.salePrice() != null);
            item.put("availableQuantity", combo.availableQuantity());
            item.put("minOrderQuantity", combo.minOrderQuantity());
            item.put("quantityStep", combo.quantityStep());
            item.put("items", combo.items().stream().map(part -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", en && part.nameEn() != null ? part.nameEn() : part.nameVi());
                row.put("quantity", part.quantity());
                row.put("unitCode", part.unitCode());
                return row;
            }).toList());
            return item;
        }).toList();
    }

    private static Map<String, Object> category(HomePageCategoryResponse category, boolean en) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", category.id().toString());
        item.put("code", category.code());
        item.put("name", en && category.nameEn() != null ? category.nameEn() : category.nameVi());
        item.put("productCount", category.productCount());
        item.put("children", category.children() == null ? List.of() : category.children().stream().map(child -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", child.code());
            row.put("name", en && child.nameEn() != null ? child.nameEn() : child.nameVi());
            row.put("productCount", child.productCount());
            return row;
        }).toList());
        return item;
    }

    private List<Map<String, Object>> favorites(UUID customerId, boolean en) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (FavoriteProductResponse favorite : favorites.list(customerId, 0, LIST_LIMIT).content()) {
                ProductPricingResponse price;
                try {
                    price = pricing.getPricing(favorite.productId());
                } catch (BusinessException exception) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", favorite.productId().toString());
                item.put("sku", favorite.sku());
                item.put("name", en && favorite.nameEn() != null ? favorite.nameEn() : favorite.nameVi());
                item.put("spec", null);
                item.put("unitCode", price.unitCode());
                item.put("imageUrl", favorite.imageUrl());
                item.put("currency", price.currency());
                item.put("priceAvailable", price.priceAvailable());
                item.put("listPrice", price.listPrice());
                item.put("salePrice", price.salePrice());
                item.put("saleActive", price.saleActive());
                item.put("minOrderQuantity", price.minOrderQuantity());
                item.put("quantityStep", price.quantityStep());
                item.put("priceTiers", price.priceTiers().stream().map(tier -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("minQuantity", tier.minQuantity());
                    row.put("maxQuantity", tier.maxQuantity());
                    row.put("unitPrice", tier.unitPrice());
                    return row;
                }).toList());
                item.put("inventoryAvailable", price.inventoryAvailable());
                item.put("availableQuantity", price.availableQuantity());
                rows.add(item);
            }
        } catch (RuntimeException exception) {
            log.warn("Favorites unavailable for AI page: {}", exception.getClass().getSimpleName());
        }
        return rows;
    }
}
