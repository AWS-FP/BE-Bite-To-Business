
package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPriceTierResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPricingResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.Product;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductInventory;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductPrice;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductPriceTier;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductInventoryRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductPriceRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductPriceTierRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductPricingService {

    private static final String PUBLISHED = "PUBLISHED";

    // Đủ cho một trang danh mục; chặn request quá lớn.
    private static final int MAX_BATCH_SIZE = 100;

    private final ProductRepository products;
    private final ProductPriceRepository prices;
    private final ProductPriceTierRepository priceTiers;
    private final ProductInventoryRepository inventories;

    public ProductPricingResponse getPricing(UUID productId) {

        Product product = products.findById(productId)
            .filter(p -> PUBLISHED.equals(p.getStatus()))
            .orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
            );

        ProductPrice price = prices.findById(productId).orElse(null);

        ProductInventory inventory =
            inventories.findById(productId).orElse(null);

        List<ProductPriceTierResponse> tiers = priceTiers
            .findByProductIdOrderByMinQuantityAscIdAsc(productId)
            .stream()
            .map(ProductPriceTierResponse::from)
            .toList();

        // Giá sale chỉ có hiệu lực khi promotion hợp lệ.
        boolean saleActive = price != null
            && price.getSalePrice() != null
            && prices.hasActiveSalePromotion(productId);

        BigDecimal comboAvailable = "COMBO".equals(product.getUnitCode())
            ? inventories.comboAvailability(List.of(productId)).stream().findFirst()
                .map(ProductInventoryRepository.CheckoutAvailability::getAvailableQuantity).orElse(BigDecimal.ZERO)
            : null;
        return toResponse(product, price, inventory, tiers, saleActive, comboAvailable);
    }

    /**
     * Giá và tồn kho của nhiều sản phẩm trong một lần gọi cho trang danh mục.
     * Bỏ qua sản phẩm không tồn tại hoặc chưa PUBLISHED; giữ thứ tự id đầu vào.
     */
    public List<ProductPricingResponse> getPricingBatch(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        if (productIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Set<UUID> ids = new LinkedHashSet<>(productIds);

        Map<UUID, Product> productById = products.findAllById(ids)
            .stream()
            .filter(p -> PUBLISHED.equals(p.getStatus()))
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (productById.isEmpty()) {
            return List.of();
        }
        Set<UUID> publishedIds = productById.keySet();

        Map<UUID, ProductPrice> priceById = prices.findAllById(publishedIds)
            .stream()
            .collect(Collectors.toMap(ProductPrice::getProductId, Function.identity()));
        Map<UUID, ProductInventory> inventoryById = inventories.findAllById(publishedIds)
            .stream()
            .collect(Collectors.toMap(ProductInventory::getProductId, Function.identity()));
        Map<UUID, List<ProductPriceTierResponse>> tiersById = priceTiers
            .findByProductIdInOrderByMinQuantityAscIdAsc(publishedIds)
            .stream()
            .collect(Collectors.groupingBy(
                ProductPriceTier::getProductId,
                Collectors.mapping(ProductPriceTierResponse::from, Collectors.toList())
            ));
        Set<UUID> activeSaleIds = new HashSet<>(
            prices.findIdsWithActiveSalePromotion(publishedIds)
        );

        Map<UUID, BigDecimal> comboAvailable = inventories.comboAvailability(publishedIds).stream()
            .collect(Collectors.toMap(ProductInventoryRepository.CheckoutAvailability::getProductId,
                ProductInventoryRepository.CheckoutAvailability::getAvailableQuantity));
        return ids.stream()
            .filter(productById::containsKey)
            .map(id -> {
                ProductPrice price = priceById.get(id);
                boolean saleActive = price != null
                    && price.getSalePrice() != null
                    && activeSaleIds.contains(id);
                return toResponse(
                    productById.get(id),
                    price,
                    inventoryById.get(id),
                    tiersById.getOrDefault(id, List.of()),
                    saleActive,
                    comboAvailable.get(id)
                );
            })
            .toList();
    }

    private ProductPricingResponse toResponse(
        Product product,
        ProductPrice price,
        ProductInventory inventory,
        List<ProductPriceTierResponse> tiers,
        boolean saleActive,
        BigDecimal comboAvailable
    ) {
        BigDecimal effectiveSalePrice = saleActive
            ? price.getSalePrice()
            : null;

        BigDecimal availableQuantity = comboAvailable != null ? comboAvailable : inventory == null
            ? null
            : inventory.getAvailableQuantity();

        return new ProductPricingResponse(
            product.getId(),
            product.getSku(),
            product.getUnitCode(),
            "VND",
            product.getMinOrderQuantity(),
            product.getQuantityStep(),
            price != null,
            price == null ? null : price.getListPrice(),
            effectiveSalePrice,
            saleActive,
            tiers,
            comboAvailable != null || inventory != null,
            availableQuantity
        );
    }
}

