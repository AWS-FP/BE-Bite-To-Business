package com.se203730.mvc.generateuibe.features.ai.service.tools;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductSummaryResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPricingResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductPricingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Tool đọc dữ liệu cho Generate UI: chỉ gọi service catalog/pricing công khai (cùng dữ liệu Guest xem được).
 * Data model có giá/tồn để renderer bind; summary gửi cho model không có giá hay số tồn.
 */
@Component
public class ProductSearchTool {
    public static final Set<String> SORTS = Set.of("name_asc", "name_desc", "newest", "oldest");
    private static final int MAX_PRODUCTS = 20;

    private final ProductService products;
    private final ProductPricingService pricing;

    public ProductSearchTool(ProductService products, ProductPricingService pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    public record SearchResult(Map<String, Object> dataModel, Map<String, Object> summary, int resultCount,
            String query) {
    }

    /** Tìm theo một hoặc nhiều từ khóa (so sánh nhiều sản phẩm), gộp kết quả không trùng. */
    public SearchResult search(List<String> keywords, String sort, int size, String locale) {
        String safeSort = sort != null && SORTS.contains(sort) ? sort : "name_asc";
        int limit = Math.max(1, Math.min(size, MAX_PRODUCTS));
        List<String> terms = keywords.stream()
                .filter(term -> term != null && !term.isBlank())
                .map(term -> term.trim().length() > 100 ? term.trim().substring(0, 100) : term.trim())
                .distinct()
                .toList();
        Map<UUID, ProductSummaryResponse> found = new LinkedHashMap<>();
        if (terms.isEmpty()) {
            products.getAll(null, null, null, safeSort, 0, limit).items()
                    .forEach(item -> found.putIfAbsent(item.id(), item));
        } else {
            int perTerm = terms.size() == 1 ? limit : Math.max(3, limit / terms.size());
            for (String term : terms) {
                products.getAll(term, null, null, safeSort, 0, perTerm).items()
                        .forEach(item -> found.putIfAbsent(item.id(), item));
            }
        }
        boolean english = "en".equals(locale);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (ProductSummaryResponse product : found.values()) {
            if (items.size() >= limit) {
                break;
            }
            ProductPricingResponse price;
            try {
                price = pricing.getPricing(product.id());
            } catch (BusinessException exception) {
                continue;
            }
            String name = english && product.nameEn() != null ? product.nameEn() : product.nameVi();
            String spec = english && product.packageSpecEn() != null ? product.packageSpecEn() : product.packageSpecVi();
            BigDecimal available = price.availableQuantity();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", product.id().toString());
            item.put("sku", product.sku());
            item.put("name", name);
            item.put("spec", spec);
            item.put("origin", product.origin());
            item.put("unitCode", product.unitCode());
            item.put("imageUrl", product.imageUrl());
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
            item.put("availableQuantity", available);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("index", items.size());
            summary.put("name", name);
            summary.put("priceAvailable", price.priceAvailable());
            summary.put("inStock", available != null && available.signum() > 0);
            summary.put("hasPriceTiers", price.priceTiers().size() > 1);
            items.add(item);
            summaries.add(summary);
        }
        String queryText = terms.isEmpty() ? null : String.join(", ", terms);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("text", queryText);
        query.put("sort", safeSort);

        Map<String, Object> dataModel = new LinkedHashMap<>();
        dataModel.put("query", query);
        dataModel.put("resultCount", items.size());
        dataModel.put("products", items);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resultCount", items.size());
        summary.put("query", query);
        summary.put("products", summaries);
        return new SearchResult(dataModel, summary, items.size(), queryText);
    }

    /** Data model rỗng khi chưa đủ thông tin để tìm (cần hỏi lại). */
    public SearchResult empty() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("text", null);
        query.put("sort", "name_asc");
        Map<String, Object> dataModel = new LinkedHashMap<>();
        dataModel.put("query", query);
        dataModel.put("resultCount", 0);
        dataModel.put("products", List.of());
        Map<String, Object> summary = new LinkedHashMap<>(dataModel);
        return new SearchResult(dataModel, summary, 0, null);
    }
}
