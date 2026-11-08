
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.ProductPageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Category;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Product;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.BrandRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.CategoryRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final ProductRepository products;
    private final BrandRepository brands;
    private final CategoryRepository categories;

    public ProductPageResponse getAll(
        String q,
        UUID categoryId,
        UUID brandId,
        String sort,
        int page,
        int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String keyword = q == null ? "" : q.trim();

        if (keyword.length() > MAX_SEARCH_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (brandId != null && !brands.existsById(brandId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Luôn chỉ lấy sản phẩm đã PUBLISHED.
        Specification<Product> filter = (root, query, cb) ->
            cb.equal(root.get("status"), PUBLISHED);

        // Tìm kiếm theo tên tiếng Việt, tiếng Anh hoặc SKU.
        if (!keyword.isEmpty()) {
            String pattern = "%" +
                escapeLike(keyword.toLowerCase(Locale.ROOT)) +
                "%";

            filter = filter.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nameVi")), pattern, '\\'),
                cb.like(cb.lower(root.get("nameEn")), pattern, '\\'),
                cb.like(cb.lower(root.get("sku")), pattern, '\\')
            ));
        }

        // Lọc theo thương hiệu.
        if (brandId != null) {
            filter = filter.and((root, query, cb) ->
                cb.equal(root.get("brandId"), brandId));
        }

        // Lọc theo danh mục cha/con đang active.
        if (categoryId != null) {
            Set<UUID> acceptedCategoryIds =
                findVisibleSubtree(categoryId);

            filter = filter.and((root, query, cb) ->
                root.get("categoryId").in(acceptedCategoryIds));
        }

        // Truy vấn có phân trang và sắp xếp.
        Page<Product> result = products.findAll(
            filter,
            PageRequest.of(page, size, sortBy(sort))
        );

        return ProductPageResponse.from(result);
    }

    private Sort sortBy(String sort) {
        String key = sort == null || sort.isBlank()
            ? "name_asc"
            : sort.trim().toLowerCase(Locale.ROOT);

        return switch (key) {
            case "name_asc" -> Sort.by(
                Sort.Order.asc("nameVi").ignoreCase(),
                Sort.Order.asc("id")
            );

            case "name_desc" -> Sort.by(
                Sort.Order.desc("nameVi").ignoreCase(),
                Sort.Order.asc("id")
            );

            case "newest" -> Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("id")
            );

            case "oldest" -> Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")
            );

            default -> throw new BusinessException(
                ErrorCode.INVALID_REQUEST
            );
        };
    }

    // Tìm tất cả category con active của category được chọn.
    private Set<UUID> findVisibleSubtree(UUID selectedId) {
        List<Category> active = categories.findAllActiveSorted();

        Map<UUID, Category> byId = new HashMap<>();
        Map<UUID, List<UUID>> children = new HashMap<>();

        for (Category category : active) {
            byId.put(category.getId(), category);

            if (category.getParentId() != null) {
                children.computeIfAbsent(
                    category.getParentId(),
                    ignored -> new ArrayList<>()
                ).add(category.getId());
            }
        }

        // Category và toàn bộ cha của nó phải active.
        Set<UUID> ancestors = new HashSet<>();
        UUID currentId = selectedId;

        while (currentId != null) {
            if (!ancestors.add(currentId)) {
                throw new BusinessException(
                    ErrorCode.INVALID_REQUEST
                );
            }

            Category current = byId.get(currentId);

            if (current == null) {
                throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND
                );
            }

            currentId = current.getParentId();
        }

        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(selectedId);

        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();

            if (!result.add(id)) {
                continue;
            }

            for (UUID childId : children.getOrDefault(
                id, List.of()
            )) {
                queue.addLast(childId);
            }
        }

        return result;
    }

    // Tránh ký tự % hoặc _ bị hiểu là wildcard khi search.
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
