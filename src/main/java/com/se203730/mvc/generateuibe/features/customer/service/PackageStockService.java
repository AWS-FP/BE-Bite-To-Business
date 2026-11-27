package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.repository.PackageStockRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cập nhật kho theo từng package, chạy trong transaction của nghiệp vụ gọi tới:
 * hủy package thì nhả hàng đang giữ, giao xong thì xuất kho, khách trả hàng thì nhập lại kho.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageStockService {
    private final PackageStockRepository stock;
    private final ProductInventoryRepository inventory;

    /** Nhả phần hàng còn giữ của package đã hủy. Đơn cũ không có dữ liệu giữ hàng thì bỏ qua. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseCancelled(UUID orderId, UUID packageId, UUID actorId, Instant now, String reason) {
        boolean strict = stock.usesComponentStock(orderId);
        for (var item : stock.items(packageId)) {
            BigDecimal quantity = item.quantity().min(stock.heldQuantity(orderId, item.productId()));
            if (strict) {
                BigDecimal done = stock.packageMovement(packageId, item.productId(), "RELEASE");
                if (done.compareTo(item.quantity()) == 0) continue;
                if (done.signum() != 0 || quantity.compareTo(item.quantity()) != 0)
                    throw new IllegalStateException("Invalid component release: " + packageId);
            }
            if (quantity.signum() <= 0 || inventory.release(item.productId(), quantity, now) != 1) {
                if (strict) throw new IllegalStateException("Component release failed: " + packageId);
                log.warn("Skip stock release; packageId={}, productId={}", packageId, item.productId());
                continue;
            }
            stock.addMovement(item.productId(), orderId, packageId, "RELEASE", quantity, reason, actorId, now);
        }
    }

    /** Giao xong: xuất phần hàng đang giữ của package khỏi kho. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shipDelivered(UUID orderId, UUID packageId, UUID actorId, Instant now) {
        boolean strict = stock.usesComponentStock(orderId);
        for (var item : stock.items(packageId)) {
            BigDecimal quantity = item.quantity().min(stock.heldQuantity(orderId, item.productId()));
            if (strict) {
                BigDecimal done = stock.packageMovement(packageId, item.productId(), "SHIP");
                if (done.compareTo(item.quantity()) == 0) continue;
                if (done.signum() != 0 || quantity.compareTo(item.quantity()) != 0)
                    throw new IllegalStateException("Invalid component shipment: " + packageId);
            }
            if (quantity.signum() <= 0 || inventory.ship(item.productId(), quantity, now) != 1) {
                if (strict) throw new IllegalStateException("Component shipment failed: " + packageId);
                log.warn("Skip stock ship; packageId={}, productId={}", packageId, item.productId());
                continue;
            }
            stock.addMovement(item.productId(), orderId, packageId, "SHIP", quantity,
                    "Xuất kho khi giao package", actorId, now);
        }
    }

    /** Khách trả hàng đã giao: nhập lại toàn bộ hàng của package vào kho. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restockReturned(UUID orderId, UUID packageId, UUID actorId, Instant now) {
        boolean strict = stock.usesComponentStock(orderId);
        if (strict) stock.lockOrder(orderId);
        for (var item : stock.items(packageId)) {
            if (strict) {
                BigDecimal done = stock.packageMovement(packageId, item.productId(), "RETURN");
                if (done.compareTo(item.quantity()) == 0) continue;
                if (done.signum() != 0 || stock.packageMovement(packageId, item.productId(), "SHIP").compareTo(item.quantity()) != 0)
                    throw new IllegalStateException("Invalid component return: " + packageId);
            }
            if (inventory.restock(item.productId(), item.quantity(), now) != 1) {
                if (strict) throw new IllegalStateException("Component restock failed: " + packageId);
                log.warn("Skip restock; packageId={}, productId={}", packageId, item.productId());
                continue;
            }
            stock.addMovement(item.productId(), orderId, packageId, "RETURN", item.quantity(),
                    "Nhập lại kho hàng khách trả", actorId, now);
        }
    }
}
