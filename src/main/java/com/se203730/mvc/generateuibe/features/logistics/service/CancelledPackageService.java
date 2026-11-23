package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.logistics.repository.CancelledPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.entity.InventoryMovement;
import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import com.se203730.mvc.generateuibe.features.customer.repository.InventoryMovementRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductInventoryRepository;

@Service
@RequiredArgsConstructor
public class CancelledPackageService {
    private final CancelledPackageRepository packages;
    private final AccountAccessService accounts;
    private final OrderRepository orders;
    private final InventoryMovementRepository movements;
    private final ProductInventoryRepository inventory;
    private final DatabaseClock clock;

    public record PackageView(UUID packageId, UUID orderId, String packageName,
                              String packageNumber, Instant cancelledAt, Instant processedAt) {}
    public record Item(UUID productId, String sku, String name, String unit, BigDecimal quantity) {}
    public record PackageDetails(UUID packageId, UUID orderId, String packageName, String packageNumber,
                                 Instant cancelledAt, boolean processed, List<Item> items) {}

    @Transactional(readOnly = true)
    public Page<PackageView> list(UUID userId, int page, int size) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return packages.findCancelled(PageRequest.of(page, size)).map(item -> new PackageView(
                item.getPackageId(), item.getOrderId(), item.getPackageName(), item.getPackageNumber(), item.getCancelledAt(), item.getProcessedAt()));
    }

    @Transactional(readOnly = true)
    public PackageDetails detail(UUID userId, UUID packageId) {
        requireLogistics(userId);
        OrderPackage parcel = cancelledPackage(packageId);
        List<Item> items = packages.findItems(packageId).stream()
                .map(item -> new Item(item.getProductId(), item.getSku(), item.getName(), item.getUnit(), item.getQuantity()))
                .toList();
        return new PackageDetails(parcel.getId(), parcel.getOrderId(), parcel.getPackageName(), parcel.getPackageNumber(),
                parcel.getCancelledAt(), packages.isProcessed(packageId), items);
    }

    @Transactional
    public PackageDetails releaseReserved(UUID userId, UUID packageId, String note) {
        requireLogistics(userId);
        OrderPackage parcel = cancelledPackage(packageId);
        var order = orders.findForUpdate(parcel.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        parcel = cancelledPackage(packageId);
        if (packages.isProcessed(packageId)) return detail(userId, packageId);
        if (order.getReservationReleasedAt() != null || packages.requiresManualReview(order.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        var items = packages.findItems(packageId);
        var balances = movements.findBalances(order.getId());
        if (items.isEmpty() || balances.isEmpty()) throw new BusinessException(ErrorCode.CONFLICT);
        Instant now = clock.now();
        for (var item : items) {
            var balance = balances.stream().filter(value -> value.getProductId().equals(item.getProductId()))
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
            if (balance.getReserved().subtract(balance.getReleased()).subtract(balance.getShipped())
                    .compareTo(item.getQuantity()) < 0 || inventory.release(item.getProductId(), item.getQuantity(), now) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            InventoryMovement movement = new InventoryMovement();
            movement.setId(UUID.randomUUID());
            movement.setProductId(item.getProductId());
            movement.setOrderId(order.getId());
            movement.setOrderPackageId(packageId);
            movement.setMovementType("RELEASE");
            movement.setQuantity(item.getQuantity());
            movement.setReason("Giải phóng hàng giữ của package đã hủy");
            movement.setCreatedBy(userId);
            movement.setCreatedAt(now);
            movements.save(movement);
        }
        packages.markProcessed(packageId, userId, now, note == null || note.isBlank() ? null : note.trim());
        return detail(userId, packageId);
    }

    private OrderPackage cancelledPackage(UUID packageId) {
        OrderPackage parcel = packages.findById(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (parcel.getCancelledAt() == null) throw new BusinessException(ErrorCode.CONFLICT);
        return parcel;
    }

    private void requireLogistics(UUID userId) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
