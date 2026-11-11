package com.se203730.mvc.generateuibe.features.ecommerce.catalog.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.CategoryResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Category;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;

    /** Danh sách danh mục cha, kèm toàn bộ danh mục con active theo cấp. */
    public List<CategoryResponse> getTree() {
        List<Category> active = categories.findAllActiveSorted();
        Map<UUID, List<Category>> childrenByParent = groupChildren(active);
        List<CategoryResponse> result = new ArrayList<>();

        for (Category category : active) {
            if (category.getParentId() == null) {
                result.add(buildTree(category, childrenByParent, new HashSet<>()));
            }
        }
        return result;
    }

    /** Chỉ trả danh mục cấp cha; children luôn là []. */
    public List<CategoryResponse> getParents() {
        return categories.findAllActiveSorted().stream()
                .filter(category -> category.getParentId() == null)
                .map(category -> CategoryResponse.from(category, List.of()))
                .toList();
    }

    /** Danh mục con của parentId; parent và mọi cấp tổ tiên phải active. */
    public List<CategoryResponse> getChildren(UUID parentId) {
        List<Category> active = categories.findAllActiveSorted();
        Map<UUID, Category> byId = new HashMap<>();
        for (Category category : active) {
            byId.put(category.getId(), category);
        }
        if (!isVisible(parentId, byId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Map<UUID, List<Category>> childrenByParent = groupChildren(active);
        List<CategoryResponse> result = new ArrayList<>();
        for (Category child : childrenByParent.getOrDefault(parentId, List.of())) {
            result.add(buildTree(child, childrenByParent, new HashSet<>()));
        }
        return result;
    }

    private boolean isVisible(UUID id, Map<UUID, Category> byId) {
        Set<UUID> visited = new HashSet<>();
        UUID currentId = id;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                return false; // Cấu trúc cha/con không hợp lệ (vòng lặp).
            }
            Category category = byId.get(currentId);
            if (category == null) {
                return false; // Category bị tắt hoặc không tồn tại trong dữ liệu active.
            }
            currentId = category.getParentId();
        }
        return true;
    }

    private Map<UUID, List<Category>> groupChildren(List<Category> active) {
        Map<UUID, List<Category>> result = new HashMap<>();
        for (Category category : active) {
            if (category.getParentId() != null) {
                // Giữ nguyên thứ tự sort_order/name_vi từ Repository.
                result.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category);
            }
        }
        return result;
    }

    private CategoryResponse buildTree(
            Category category,
            Map<UUID, List<Category>> childrenByParent,
            Set<UUID> ancestors) {
        if (!ancestors.add(category.getId())) {
            return CategoryResponse.from(category, List.of()); // Phòng trường hợp dữ liệu tạo vòng lặp.
        }
        List<CategoryResponse> children = new ArrayList<>();
        for (Category child : childrenByParent.getOrDefault(category.getId(), List.of())) {
            if (!ancestors.contains(child.getId())) {
                children.add(buildTree(child, childrenByParent, ancestors));
            }
        }
        ancestors.remove(category.getId());
        return CategoryResponse.from(category, children);
    }
}
