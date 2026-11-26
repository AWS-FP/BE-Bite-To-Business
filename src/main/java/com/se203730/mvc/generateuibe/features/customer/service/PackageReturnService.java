package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.outbox.service.OutboxEventService;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.request.CreatePackageReturnRequest;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PackageReturnRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PackageReturnRepository.ReturnView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trả hàng: khách gửi yêu cầu trong 24 giờ sau khi package được giao; Admin chấp nhận thì hàng được
 * nhập lại kho và khách nhận email. Hoàn tiền được chuyển khoản thủ công nên hệ thống chỉ thông báo.
 */
@Service
@RequiredArgsConstructor
public class PackageReturnService {
    private static final Duration RETURN_WINDOW = Duration.ofHours(24);
    private static final Set<String> STATES = Set.of("REQUESTED", "ACCEPTED", "REJECTED");

    private final AccountAccessService accounts;
    private final OrderRepository orders;
    private final PackageReturnRepository returns;
    private final PackageStockService stock;
    private final NotificationEventPublisher notifications;
    private final OutboxEventService outbox;
    private final DatabaseClock clock;

    @Transactional
    public ReturnView request(UUID customerId, UUID orderId, UUID packageId, CreatePackageReturnRequest request) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        orders.findForUpdate(orderId)
                .filter(order -> order.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        var parcel = returns.lockPackage(packageId)
                .filter(item -> item.orderId().equals(orderId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.now();
        if (parcel.cancelledAt() != null || parcel.deliveredAt() == null || parcel.hasReturn()
                || !now.isBefore(parcel.deliveredAt().plus(RETURN_WINDOW))) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ALLOWED);
        }
        UUID id = UUID.randomUUID();
        var amounts = returns.calculateAmounts(packageId);
        if (amounts == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        returns.insert(id, packageId, customerId, request.reason().strip(), request.bank().strip(),
                request.accountNumber().strip(), request.accountName().strip(), now, amounts);
        return returns.find(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnView> list(UUID adminId, String status, int page, int size) {
        accounts.requireAdmin(adminId);
        if ((status != null && !STATES.contains(status)) || page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long total = returns.count(status);
        return new PageResponse<>(returns.page(status, page, size), page, size, total,
                (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public ReturnView detail(UUID adminId, UUID id) {
        accounts.requireAdmin(adminId);
        return returns.find(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Chấp nhận trả hàng: nhập lại kho và báo cho khách (hoàn tiền chuyển khoản thủ công). */
    @Transactional
    public ReturnView accept(UUID adminId, UUID id) {
        accounts.requireAdmin(adminId);
        ReturnView current = lockRequested(id);
        if (current.refundAmount() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.now();
        returns.review(id, "ACCEPTED", adminId, null, now);
        stock.restockReturned(current.orderId(), current.packageId(), adminId, now);
        notify(current, "package.return_accepted",
                "Đã chấp nhận trả hàng", "Return accepted",
                "Yêu cầu trả kiện \"" + current.packageName() + "\" đã được chấp nhận. "
                        + "Tiền hàng sẽ được hoàn vào tài khoản bạn đã cung cấp.",
                "The return of package \"" + current.packageName() + "\" was accepted. "
                        + "The refund will be sent to the account you provided.",
                null);
        return returns.find(id).orElseThrow();
    }

    @Transactional
    public ReturnView reject(UUID adminId, UUID id, String note) {
        accounts.requireAdmin(adminId);
        if (note == null || note.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        ReturnView current = lockRequested(id);
        String reason = note.strip();
        returns.review(id, "REJECTED", adminId, reason, clock.now());
        notify(current, "package.return_rejected",
                "Yêu cầu trả hàng bị từ chối", "Return declined",
                "Yêu cầu trả kiện \"" + current.packageName() + "\" không được chấp nhận: " + reason,
                "The return request for package \"" + current.packageName() + "\" was declined: " + reason,
                reason);
        return returns.find(id).orElseThrow();
    }

    @Transactional
    public ReturnView confirmRefund(UUID adminId, UUID id) {
        accounts.requireAdmin(adminId);
        String status = returns.lockStatus(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ReturnView current = returns.find(id).orElseThrow();
        if (!"ACCEPTED".equals(status) || current.refundedAt() != null || current.refundAmount() == null) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        Instant now = clock.now();
        returns.recordRefund(id, adminId, now);
        notifications.publish(current.customerId(), null, null, "ORDER", "Đã ghi nhận hoàn tiền", "Refund recorded",
                "Khoản hoàn cho kiện \"" + current.packageName() + "\" đã được ghi nhận.",
                "The refund for package \"" + current.packageName() + "\" was recorded.",
                "/orders/" + current.orderId());
        return returns.find(id).orElseThrow();
    }

    private ReturnView lockRequested(UUID id) {
        String status = returns.lockStatus(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"REQUESTED".equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return returns.find(id).orElseThrow();
    }

    private void notify(ReturnView item, String eventType, String titleVi, String titleEn,
                        String contentVi, String contentEn, String reason) {
        notifications.publish(item.customerId(), null, null, "ORDER", titleVi, titleEn, contentVi, contentEn,
                "/orders/" + item.orderId());
        // Lambda btb-customer-notifier gửi email cho khách từ sự kiện này.
        outbox.record(eventType, "PACKAGE_RETURN", item.id(), reason == null
                ? Map.of("orderId", item.orderId(), "customerId", item.customerId(), "packageName", item.packageName())
                : Map.of("orderId", item.orderId(), "customerId", item.customerId(), "packageName", item.packageName(),
                        "reason", reason));
    }
}
