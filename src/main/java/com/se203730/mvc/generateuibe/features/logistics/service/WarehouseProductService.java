package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.BrandRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.CategoryRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.DistributorRepository;
import com.se203730.mvc.generateuibe.features.logistics.dto.CreateWarehouseProductRequest;
import com.se203730.mvc.generateuibe.features.logistics.repository.WarehouseProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import jakarta.validation.Validator;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseProductService {
    private final AccountAccessService accounts;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final WarehouseProductRepository products;
    private final WarehouseProductImageStorage images;
    private final DistributorRepository distributors;
    private final Validator validator;

    @Transactional
    public void disable(UUID userId, UUID productId) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        if (!products.archive(productId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
    public record CreatedProduct(UUID id, String sku, String status) {}

    public record DistributorOption(UUID id, String name) {}

    @Transactional(readOnly = true)
    public List<DistributorOption> distributors(UUID userId) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        return distributors.findAllByOrderByNameAsc().stream()
                .map(distributor -> new DistributorOption(distributor.getId(), distributor.getName()))
                .toList();
    }

    @Transactional
    public CreatedProduct create(UUID userId, CreateWarehouseProductRequest request, MultipartFile file) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        validate(request);
        WarehouseProductImageStorage.Image image = upload(file);
        UUID id = UUID.randomUUID();
        products.insert(id, userId, request, image == null ? null : image.url(), image == null ? null : image.publicId());
        return new CreatedProduct(id, request.sku().trim(), "DRAFT");
    }

    private void validate(CreateWarehouseProductRequest request) {
        if (categories.findById(request.categoryId()).filter(c -> c.isActive() && c.getParentId() != null).isEmpty()
                || !brands.existsById(request.brandId())
                || !distributors.existsById(request.distributorId())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        validateTiers(request);
        validateFreeTextLength(request);
        validateNutrition(request);
    }

    private void validateTiers(CreateWarehouseProductRequest request) {
        var tiers = new ArrayList<>(request.tiers());
        tiers.sort(Comparator.comparing(CreateWarehouseProductRequest.Tier::min));
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            if (request.price() == null || tier.price().compareTo(request.price()) > 0 || tier.max() != null && tier.max().compareTo(tier.min()) < 0
                    || i > 0 && (tiers.get(i - 1).max() == null || tiers.get(i - 1).max().compareTo(tier.min()) >= 0)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    private WarehouseProductImageStorage.Image upload(MultipartFile file) {
        WarehouseProductImageStorage.Image image = file == null ? null : images.upload(file);
        if (image != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) images.cleanup(image);
                }
            });
        }
        return image;
    }

    private void requireLogistics(UUID userId) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF)
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    @Transactional(readOnly = true)
    public WarehouseProductRepository.EditPage editable(UUID userId, String keyword, int page, int size) {
        requireLogistics(userId);
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() > 160 || page < 0 || size < 1 || size > 50)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return products.editable(q, page, size);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> editDetail(UUID userId, UUID id) {
        requireLogistics(userId);
        return products.editDetail(id);
    }

    @Transactional
    public CreatedProduct update(UUID userId, UUID id, CreateWarehouseProductRequest request, MultipartFile file) {
        requireLogistics(userId);
        String status = products.lockEditable(id);
        var existing = products.editDetail(id);
        if (!Objects.equals(request.sku(), existing.get("sku")) || !Objects.equals(request.unit(), existing.get("unit")))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        boolean draft = "DRAFT".equals(status);
        // Published commercial settings are managed through the existing executive workflow.
        if (!draft && (!sameNumber(request.price(), existing.get("price"))
                || !sameNumber(request.vat(), existing.get("vat"))
                || !sameNumber(request.minOrder(), existing.get("minOrder"))
                || !sameTiers(request, existing.get("tiers"))))
            throw new BusinessException(ErrorCode.CONFLICT);
        validateEdit(request, existing, draft);
        var image = upload(file);
        products.update(id, userId, request, image == null ? null : image.url(), image == null ? null : image.publicId(),
                draft, !sameNutrition(request, existing), !sameTiers(request, existing.get("tiers")));
        return new CreatedProduct(id, request.sku(), status);
    }

    private void validateEdit(CreateWarehouseProductRequest request, Map<String, Object> existing, boolean draft) {
        // Reuse the creation DTO's Bean Validation constraints. Only existing legacy omissions
        // may remain unchanged; an edit cannot clear a populated required field.
        for (var violation : validator.validate(request)) {
            String field = violation.getPropertyPath().toString();
            Object old = existing.get(field);
            Object value = violation.getInvalidValue();
            boolean legacyEmpty = existing.containsKey(field) &&
                    (old == null || old instanceof String oldText && oldText.isBlank()) &&
                    (value == null || value instanceof String newText && newText.isBlank());
            boolean emptyNutrition = field.equals("nutritionFacts") && value instanceof List<?> rows && rows.isEmpty()
                    && existing.get("nutritionFacts") instanceof List<?> oldRows && oldRows.isEmpty();
            if (!legacyEmpty && !emptyNutrition) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!Objects.equals(request.categoryId(), existing.get("categoryId")) &&
                (request.categoryId() == null || categories.findById(request.categoryId())
                        .filter(c -> c.isActive() && c.getParentId() != null).isEmpty()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!Objects.equals(request.brandId(), existing.get("brandId")) &&
                (request.brandId() == null || !brands.existsById(request.brandId())))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!Objects.equals(request.distributorId(), existing.get("distributorId")) &&
                (request.distributorId() == null || !distributors.existsById(request.distributorId())))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (draft && !sameTiers(request, existing.get("tiers"))) validateTiers(request);
        if (draft && request.price() != null && request.tiers().stream().anyMatch(t -> t.price().compareTo(request.price()) > 0))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        for (var entry : Map.of("descriptionVi", java.util.Optional.ofNullable(request.descriptionVi()),
                "descriptionEn", java.util.Optional.ofNullable(request.descriptionEn()),
                "note", java.util.Optional.ofNullable(request.note()),
                "storageVi", java.util.Optional.ofNullable(request.storageVi()),
                "storageEn", java.util.Optional.ofNullable(request.storageEn())).entrySet()) {
            String value = entry.getValue().orElse(null);
            if (!Objects.equals(value, existing.get(entry.getKey())) && value != null && !value.isBlank()
                    && value.strip().split("\\s+").length > MAX_TEXT_WORDS)
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!sameNutrition(request, existing)) validateNutrition(request);
    }

    private boolean sameNutrition(CreateWarehouseProductRequest request, Map<String, Object> existing) {
        if (!Objects.equals(request.trimmedNutritionBasis(), existing.get("nutritionBasis")) ||
                !(existing.get("nutritionFacts") instanceof List<?> rows) || rows.size() != request.nutritionFactsOrEmpty().size()) return false;
        for (int i = 0; i < rows.size(); i++) {
            var fact = request.nutritionFactsOrEmpty().get(i);
            if (!(rows.get(i) instanceof Map<?, ?> old) || fact == null ||
                    !Objects.equals(fact.nutrient(), old.get("nutrient")) ||
                    !Objects.equals(fact.nutrientEn(), old.get("nutrientEn")) ||
                    !Objects.equals(fact.amount(), old.get("amount"))) return false;
        }
        return true;
    }

    private boolean sameNumber(BigDecimal requested, Object actual) {
        return requested == null ? actual == null : actual instanceof BigDecimal value && requested.compareTo(value) == 0;
    }

    private boolean sameTiers(CreateWarehouseProductRequest request, Object existing) {
        if (request.tiers() == null || !(existing instanceof List<?> rows) || rows.size() != request.tiers().size()) return false;
        if (request.tiers().stream().anyMatch(t -> t == null || t.min() == null) ||
                request.tiers().stream().map(t -> t.min().stripTrailingZeros()).distinct().count() != rows.size()) return false;
        return request.tiers().stream().allMatch(t -> rows.stream().anyMatch(row -> row instanceof Map<?, ?> m
                && sameNumber(t.min(), m.get("min")) && sameNumber(t.max(), m.get("max"))
                && sameNumber(t.price(), m.get("price"))));
    }

    // Ô văn bản tự do tối đa 150 chữ để đoạn hiển thị trên trang sản phẩm không phá bố cục.
    private static final int MAX_TEXT_WORDS = 150;

    private void validateFreeTextLength(CreateWarehouseProductRequest request) {
        for (String text : new String[]{request.descriptionVi(), request.descriptionEn(), request.note(),
                request.storageVi(), request.storageEn()}) {
            if (text != null && !text.isBlank() && text.strip().split("\\s+").length > MAX_TEXT_WORDS) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }

    // Bảng dinh dưỡng bắt buộc: có lượng tham chiếu, ít nhất một dòng, tên VI/EN không trùng nhau.
    private void validateNutrition(CreateWarehouseProductRequest request) {
        var facts = request.nutritionFactsOrEmpty();
        if (facts.isEmpty() || request.trimmedNutritionBasis() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<String> seenVi = new HashSet<>();
        Set<String> seenEn = new HashSet<>();
        for (var fact : facts) {
            if (!seenVi.add(fact.nutrient().strip().toLowerCase(Locale.ROOT))
                    || !seenEn.add(fact.nutrientEn().strip().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }
}
