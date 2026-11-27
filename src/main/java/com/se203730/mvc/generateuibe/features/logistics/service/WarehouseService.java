package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import com.se203730.mvc.generateuibe.features.customer.repository.InventoryMovementRepository;
import com.se203730.mvc.generateuibe.features.logistics.entity.WarehouseStock;
import com.se203730.mvc.generateuibe.features.logistics.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseStockRepository stocks;
    private final InventoryMovementRepository movements;
    private final AccountAccessService accounts;
    private final DatabaseClock clock;

    public record StockResponse(UUID productId, String sku, String name, String unit, String imageUrl,
                                BigDecimal onHand, BigDecimal reserved, BigDecimal available) {}

    @Transactional(readOnly = true)
    public Page<StockResponse> list(UUID userId, String keyword, int page, int size) {
        requireLogistics(userId);
        String search = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return stocks.search(search, PageRequest.of(page, size)).map(item -> new StockResponse(item.getProductId(),
                item.getSku(), item.getName(), item.getUnit(), item.getImageUrl(), item.getOnHand(), item.getReserved(), item.getAvailable()));
    }

    @Transactional
    public StockResponse change(UUID userId, UUID productId, UUID movementId, String type, BigDecimal quantity, String reason) {
        requireLogistics(userId);
        if (stocks.isCombo(productId)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        stocks.createIfMissing(productId, userId, clock.now());
        WarehouseStock stock = stocks.findForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (("RECEIVE".equals(type) && (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 0))
                || ("ADJUST".equals(type) && (quantity.signum() == 0 || cleanReason == null))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        InventoryMovement previous = movements.findById(movementId).orElse(null);
        if (previous != null) {
            if (!previous.getProductId().equals(productId) || !previous.getCreatedBy().equals(userId)
                    || !previous.getMovementType().equals(type) || previous.getQuantity().compareTo(quantity) != 0
                    || !java.util.Objects.equals(previous.getReason(), cleanReason)) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return response(stock);
        }
        BigDecimal updated = stock.getOnHandQuantity().add(quantity);
        if (updated.compareTo(stock.getReservedQuantity()) < 0) {
            throw new BusinessException(ErrorCode.QUOTE_STOCK_UNAVAILABLE);
        }
        Instant now = clock.now();
        stock.setOnHandQuantity(updated);
        stock.setUpdatedBy(userId);
        stock.setUpdatedAt(now);
        InventoryMovement movement = new InventoryMovement();
        movement.setId(movementId);
        movement.setProductId(productId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReason(cleanReason);
        movement.setCreatedBy(userId);
        movement.setCreatedAt(now);
        movements.saveAndFlush(movement);
        return response(stock);
    }

    @Transactional
    public StockResponse clearAvailable(UUID userId, UUID productId, UUID movementId) {
        requireLogistics(userId);
        if (stocks.isCombo(productId)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        stocks.createIfMissing(productId, userId, clock.now());
        WarehouseStock stock = stocks.findForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        InventoryMovement previous = movements.findById(movementId).orElse(null);
        if (previous != null) {
            if (!previous.getProductId().equals(productId) || !previous.getCreatedBy().equals(userId)
                    || !"ADJUST".equals(previous.getMovementType())
                    || !"Xóa tồn khả dụng".equals(previous.getReason())) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return response(stock);
        }
        BigDecimal available = stock.getOnHandQuantity().subtract(stock.getReservedQuantity());
        if (available.signum() == 0) {
            return response(stock);
        }
        return change(userId, productId, movementId, "ADJUST", available.negate(), "Xóa tồn khả dụng");
    }

    private StockResponse response(WarehouseStock stock) {
        return new StockResponse(stock.getProductId(), null, null, null, null, stock.getOnHandQuantity(), stock.getReservedQuantity(),
                stock.getOnHandQuantity().subtract(stock.getReservedQuantity()));
    }

    private void requireLogistics(UUID userId) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
