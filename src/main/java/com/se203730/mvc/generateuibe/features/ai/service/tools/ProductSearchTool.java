package com.se203730.mvc.generateuibe.features.ai.service.tools;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductDetailResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductSummaryResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPricingResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductPricingService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductDetailsResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductAttributeSearchRepository.Match;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductKnowledgeService;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductKnowledgeService.AttributeQuery;
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
    /** relevance (mặc định khi có từ khóa) dùng xếp hạng Smart Search V50/V51 của catalog. */
    public static final Set<String> SORTS = Set.of("relevance", "name_asc", "name_desc", "newest", "oldest");
    private static final int MAX_PRODUCTS = 20;

    private final ProductService products;
    private final ProductPricingService pricing;
    private final ProductKnowledgeService knowledge;

    public ProductSearchTool(ProductService products, ProductPricingService pricing,
            ProductKnowledgeService knowledge) {
        this.products = products;
        this.pricing = pricing;
        this.knowledge = knowledge;
    }

    public record SearchResult(Map<String, Object> dataModel, Map<String, Object> summary, int resultCount,
            String query) {
    }

    /** Tìm theo một hoặc nhiều từ khóa (so sánh nhiều sản phẩm), gộp kết quả không trùng. */
    public SearchResult search(List<String> keywords, String sort, int size, String locale) {
        boolean hasTerms = keywords.stream().anyMatch(term -> term != null && !term.isBlank());
        String safeSort = sort != null && SORTS.contains(sort) ? sort : hasTerms ? "relevance" : "name_asc";
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

    /**
     * Dựng thẻ sản phẩm (cùng hình dạng với /products) cho danh sách ID cho trước, giữ nguyên thứ tự.
     * Dùng cho "xem gần đây" và "mua lại": ID đến từ lịch sử của chính khách, còn giá/tồn luôn lấy mới
     * từ ProductService/ProductPricingService. Sản phẩm đã ngừng bán hoặc lỗi thì bỏ qua.
     */
    public List<Map<String, Object>> byIds(List<UUID> ids, String locale) {
        boolean english = "en".equals(locale);
        List<Map<String, Object>> items = new ArrayList<>();
        for (UUID id : ids.stream().distinct().limit(MAX_PRODUCTS).toList()) {
            ProductDetailResponse product;
            ProductPricingResponse price;
            try {
                product = products.getById(id);
                price = pricing.getPricing(id);
            } catch (BusinessException exception) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", product.id().toString());
            item.put("sku", product.sku());
            item.put("name", english && product.nameEn() != null ? product.nameEn() : product.nameVi());
            item.put("spec", english && product.packageSpecEn() != null ? product.packageSpecEn()
                    : product.packageSpecVi());
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
            item.put("availableQuantity", price.availableQuantity());
            item.put("facts", facts(product, english));
            items.add(item);
        }
        return items;
    }

    /**
     * Tìm theo thông tin mô tả trong DB (xuất xứ, bảo quản, thành phần, dinh dưỡng, VSATTP) qua
     * ProductKnowledgeService, rồi dựng thẻ sản phẩm kèm {@code facts} và {@code matchedOn} (nhóm điều kiện khớp).
     * Data model có cùng hình dạng với /products nên mọi widget sản phẩm đều bind được.
     */
    public SearchResult searchByAttributes(AttributeQuery query, String locale, int size) {
        List<Match> matches = knowledge.search(query, Math.max(1, Math.min(size, MAX_PRODUCTS)));
        Map<UUID, List<String>> matchedOn = new LinkedHashMap<>();
        for (Match match : matches) {
            List<String> on = new ArrayList<>();
            if (match.origin()) on.add("origin");
            if (match.storage()) on.add("storage");
            if (match.contains()) on.add("contains");
            if (match.nutrient()) on.add("nutrient");
            if (match.compliance()) on.add("compliance");
            matchedOn.put(match.productId(), on);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : byIds(new ArrayList<>(matchedOn.keySet()), locale)) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("matchedOn", matchedOn.get(UUID.fromString((String) row.get("productId"))));
            items.add(item);
        }
        return result(items, describe(query), "relevance");
    }

    /**
     * Bổ sung facts cho kết quả tìm theo tên khi khách hỏi thông tin của một sản phẩm cụ thể
     * ("ba chỉ bò bảo quản thế nào?"). Chỉ làm giàu tối đa {@code limit} sản phẩm đầu để giới hạn số truy vấn.
     */
    public SearchResult withFacts(SearchResult result, String locale, int limit) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> current = (List<Map<String, Object>>) result.dataModel().get("products");
        List<UUID> ids = current.stream().limit(limit).map(row -> UUID.fromString((String) row.get("productId")))
                .toList();
        if (ids.isEmpty()) {
            return result;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) result.dataModel().get("query");
        return result(byIds(ids, locale), result.query(), String.valueOf(query.get("sort")));
    }

    /** Chọn một sản phẩm khớp tên cho mỗi đối tượng được so sánh, giữ đúng thứ tự yêu cầu. */
    public SearchResult compareRepresentatives(List<String> terms, String sort, String locale) {
        List<UUID> ids = new ArrayList<>();
        for (String term : terms.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(8).toList()) {
            SearchResult found = search(List.of(term), sort, 12, locale);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) found.dataModel().get("products");
            String[] words = normalizedWords(term).split(" ");
            String distinctive = words[words.length - 1];
            rows.stream().filter(row -> java.util.Arrays.asList(normalizedWords(String.valueOf(row.get("name"))).split(" "))
                            .contains(distinctive))
                    .map(row -> UUID.fromString((String) row.get("productId")))
                    .filter(id -> !ids.contains(id)).findFirst().ifPresent(ids::add);
        }
        return result(byIds(ids, locale), String.join(", ", terms), sort == null ? "relevance" : sort);
    }

    private static String normalizedWords(String text) {
        return java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replaceAll("[^a-z0-9]+", " ").trim();
    }
    /** Dựng data model + summary từ danh sách thẻ; summary có thêm tóm tắt facts (không có văn bản dài, không giá). */
    private SearchResult result(List<Map<String, Object>> items, String queryText, String sort) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Object available = item.get("availableQuantity");
            @SuppressWarnings("unchecked")
            Map<String, Object> facts = (Map<String, Object>) item.getOrDefault("facts", Map.of());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("index", summaries.size());
            summary.put("name", item.get("name"));
            summary.put("priceAvailable", item.get("priceAvailable"));
            summary.put("inStock", available instanceof BigDecimal number && number.signum() > 0);
            summary.put("hasPriceTiers", item.get("priceTiers") instanceof List<?> tiers && tiers.size() > 1);
            summary.put("origin", facts.get("origin"));
            summary.put("hasStorageGuide", facts.get("storage") != null);
            summary.put("hasNutrition", facts.get("nutrition") instanceof List<?> rows && !rows.isEmpty());
            summary.put("hasOriginStory", facts.get("originDetail") != null);
            if (item.containsKey("matchedOn")) {
                summary.put("matchedOn", item.get("matchedOn"));
            }
            summaries.add(summary);
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("text", queryText);
        query.put("sort", sort);
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

    /** Thông tin mô tả của sản phẩm cho widget ProductFacts; văn bản lấy nguyên từ DB theo ngôn ngữ. */
    private static Map<String, Object> facts(ProductDetailResponse product, boolean english) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("origin", product.origin());
        facts.put("brand", product.brandName());
        facts.put("category", english && product.categoryNameEn() != null ? product.categoryNameEn()
                : product.categoryNameVi());
        facts.put("distributor", product.distributorName());
        facts.put("description", english && product.descriptionEn() != null ? product.descriptionEn()
                : product.descriptionVi());
        ProductDetailsResponse details = product.details();
        if (details != null) {
            facts.put("netWeightKg", details.netWeightKg());
            facts.put("storage", pick(english, details.kitchenStorageContentVi(), details.kitchenStorageContentEn()));
            facts.put("technical", pick(english, details.technicalContentVi(), details.technicalContentEn()));
            facts.put("originDetail", pick(english, details.originContentVi(), details.originContentEn()));
            facts.put("compliance", pick(english, details.complianceVatContentVi(), details.complianceVatContentEn()));
            facts.put("nutritionBasis", details.nutritionBasis());
            facts.put("nutrition", details.nutritionFacts() == null ? List.of()
                    : details.nutritionFacts().stream().map(fact -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("nutrient", english && fact.nutrientEn() != null ? fact.nutrientEn() : fact.nutrient());
                        row.put("amount", fact.amount());
                        return row;
                    }).toList());
        }
        return facts;
    }

    private static String pick(boolean english, String vi, String en) {
        String value = english && en != null && !en.isBlank() ? en : vi;
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Mô tả ngắn điều kiện tìm, dùng làm /query/text và câu trả lời ("xuất xứ: úc · bảo quản: đông lạnh"). */
    private static String describe(AttributeQuery query) {
        List<String> parts = new ArrayList<>();
        add(parts, "xuất xứ", query.origin());
        add(parts, "bảo quản", query.storage());
        add(parts, "thành phần", query.contains());
        add(parts, "dinh dưỡng", query.nutrient());
        add(parts, "chứng nhận", query.compliance());
        add(parts, "từ khóa", query.text());
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static void add(List<String> parts, String label, List<String> words) {
        if (words != null && words.stream().anyMatch(word -> word != null && !word.isBlank())) {
            parts.add(label + ": " + String.join(", ", words.stream().filter(word -> word != null && !word.isBlank())
                    .toList()));
        }
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
