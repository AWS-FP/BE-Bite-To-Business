package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.BrandRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.CategoryRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.DistributorRepository;
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
        if (categories.findById(request.categoryId()).filter(c -> c.isActive() && c.getParentId() != null).isEmpty()
                || !brands.existsById(request.brandId())
                || !distributors.existsById(request.distributorId())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (request.minOrder().remainder(request.quantityStep()).signum() != 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var tiers = new ArrayList<>(request.tiers());
        tiers.sort(Comparator.comparing(CreateWarehouseProductRequest.Tier::min));
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.get(i);
            if (tier.price().compareTo(request.price()) > 0 || tier.max() != null && tier.max().compareTo(tier.min()) < 0
                    || i > 0 && (tiers.get(i - 1).max() == null || tiers.get(i - 1).max().compareTo(tier.min()) >= 0)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        validateFreeTextLength(request);
        validateNutrition(request);
        WarehouseProductImageStorage.Image image = file == null ? null : images.upload(file);
        if (image != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) images.cleanup(image);
                }
            });
        }
        UUID id = UUID.randomUUID();
        products.insert(id, userId, request, image == null ? null : image.url(), image == null ? null : image.publicId());
        return new CreatedProduct(id, request.sku().trim(), "DRAFT");
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
