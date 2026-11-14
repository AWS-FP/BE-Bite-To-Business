package com.se203730.mvc.generateuibe.features.delivery.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import com.se203730.mvc.generateuibe.features.customer.enums.OrderStatus;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderPackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.ClaimedPackageResponse;
import com.se203730.mvc.generateuibe.features.delivery.entity.DeliveryPackageClaim;
import com.se203730.mvc.generateuibe.features.delivery.repository.DeliveryPackageClaimRepository;
import com.se203730.mvc.generateuibe.features.delivery.repository.DeliveryStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryPackageClaimService {
    private final AccountAccessService accounts;
    private final OrderRepository orders;
    private final OrderPackageRepository packages;
    private final DeliveryPackageClaimRepository claims;
    private final DeliveryStopRepository stops;
    private final DatabaseClock clock;

    @Transactional
    public ClaimedPackageResponse claim(UUID staffId, UUID packageId) {
        requireDeliveryStaff(staffId);
        UUID orderId = packages.findOrderIdByPackageId(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Order order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        OrderPackage parcel = packages.findForUpdate(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (parcel.getCancelledAt() != null || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.DELIVERY_PACKAGE_CANCELLED);
        }
        if (!orders.hasSuccessfulPayment(order.getId(), order.getTotalAmount())) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_REQUIRED);
        }
        if (stops.existsByOrderPackageId(packageId)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        DeliveryPackageClaim existing = claims.findById(packageId).orElse(null);
        if (existing != null) {
            if (!existing.getDeliveryStaffId().equals(staffId)) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return response(existing, parcel, order);
        }
        DeliveryPackageClaim claim = new DeliveryPackageClaim();
        claim.setPackageId(packageId);
        claim.setDeliveryStaffId(staffId);
        claim.setClaimedAt(clock.now());
        claims.saveAndFlush(claim);
        return response(claim, parcel, order);
    }

    @Transactional(readOnly = true)
    public ClaimedPackageResponse detail(UUID staffId, UUID packageId) {
        requireDeliveryStaff(staffId);
        DeliveryPackageClaim claim = claims.findById(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!claim.getDeliveryStaffId().equals(staffId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        OrderPackage parcel = packages.findById(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Order order = orders.findById(parcel.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return response(claim, parcel, order);
    }

    @Transactional(readOnly = true)
    public Page<ClaimedPackageResponse> mine(UUID staffId, int page, int size) {
        requireDeliveryStaff(staffId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Page<DeliveryPackageClaim> results = claims.findByDeliveryStaffIdAndDeliveredAtIsNullOrderByClaimedAtDescPackageIdAsc(staffId, PageRequest.of(page, size));
        return packageResponses(results);
    }

    @Transactional(readOnly = true)
    public Page<ClaimedPackageResponse> history(UUID staffId, int page, int size) {
        requireDeliveryStaff(staffId);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Page<DeliveryPackageClaim> results = claims.findByDeliveryStaffIdAndDeliveredAtIsNotNullOrderByDeliveredAtDescPackageIdAsc(staffId, PageRequest.of(page, size));
        return packageResponses(results);
    }

    @Transactional
    public ClaimedPackageResponse complete(UUID staffId, UUID packageId) {
        requireDeliveryStaff(staffId);
        UUID orderId = packages.findOrderIdByPackageId(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Order order = orders.findForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        OrderPackage parcel = packages.findForUpdate(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        DeliveryPackageClaim claim = claims.findById(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!claim.getDeliveryStaffId().equals(staffId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (claim.getDeliveredAt() != null) {
            return response(claim, parcel, order);
        }
        if (parcel.getCancelledAt() != null || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.DELIVERY_PACKAGE_CANCELLED);
        }
        if (!orders.hasSuccessfulPayment(order.getId(), order.getTotalAmount())) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_REQUIRED);
        }
        claim.setDeliveredAt(clock.now());
        claims.saveAndFlush(claim);
        return response(claim, parcel, order);
    }

    private Page<ClaimedPackageResponse> packageResponses(Page<DeliveryPackageClaim> results) {
        return results.map(claim -> {
            OrderPackage parcel = packages.findById(claim.getPackageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            Order order = orders.findById(parcel.getOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            return response(claim, parcel, order);
        });
    }

    private void requireDeliveryStaff(UUID staffId) {
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.DELIVERY_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private ClaimedPackageResponse response(DeliveryPackageClaim claim, OrderPackage parcel, Order order) {
        return new ClaimedPackageResponse(parcel.getId(), parcel.getPackageNumber(), parcel.getPackageName(),
                order.getId(), order.getOrderNumber(), parcel.getDeliveryDate(), parcel.getDeliveryTimeSlot(),
                parcel.getDeliveryAddress(), parcel.getRecipientName(), parcel.getRecipientPhone(),
                parcel.getDeliveryNote(), parcel.getCity(), parcel.getLatitude(), parcel.getLongitude(),
                claim.getClaimedAt(), parcel.getCancelledAt() != null || order.getStatus() == OrderStatus.CANCELLED,
                claim.getDeliveredAt());
    }
}
