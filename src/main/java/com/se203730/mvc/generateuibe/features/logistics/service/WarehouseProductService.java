package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.BrandRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.CategoryRepository;
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

    public record CreatedProduct(UUID id, String sku, String status) {}

    @Transactional
    public CreatedProduct create(UUID userId, CreateWarehouseProductRequest request, MultipartFile file) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        if (categories.findById(request.categoryId()).filter(c -> c.isActive() && c.getParentId() != null).isEmpty()
                || request.brandId() != null && !brands.existsById(request.brandId())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
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

    // Bảng dinh dưỡng: có dòng thì phải có lượng tham chiếu, và mỗi thành phần chỉ xuất hiện một lần.
    private void validateNutrition(CreateWarehouseProductRequest request) {
        var facts = request.nutritionFactsOrEmpty();
        if (!facts.isEmpty() && request.trimmedNutritionBasis() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<String> seen = new HashSet<>();
        for (var fact : facts) {
            if (!seen.add(fact.nutrient().strip().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
    }
}
