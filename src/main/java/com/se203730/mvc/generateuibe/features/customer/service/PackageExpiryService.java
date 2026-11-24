package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.outbox.service.OutboxEventService;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PackageStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** Hủy package đã qua ngày giao mà không có nhân viên giao hàng nhận, rồi báo cho khách. */
@Service
@RequiredArgsConstructor
public class PackageExpiryService {
    private final OrderRepository orders;
    private final PackageStockRepository packages;
    private final PackageStockService stock;
    private final NotificationEventPublisher notifications;
    private final OutboxEventService outbox;
    private final DatabaseClock clock;

    @Transactional
    public void cancel(PackageStockRepository.ExpiredPackage parcel) {
        // Khóa đơn theo cùng thứ tự với lúc nhân viên nhận package để không chen ngang.
        orders.findForUpdate(parcel.orderId()).orElseThrow();
        Instant now = clock.now();
        if (packages.cancelIfUnclaimed(parcel.packageId(), now) != 1) {
            return;
        }
        stock.releaseCancelled(parcel.orderId(), parcel.packageId(), parcel.customerId(), now,
                "Tự động hủy package quá ngày giao");
        String name = parcel.packageName();
        notifications.publish(parcel.customerId(), null, null, "DELIVERY",
                "Kiện hàng đã bị hủy", "Package cancelled",
                "Kiện \"" + name + "\" đã quá ngày giao mà chưa được nhận giao nên đã bị hủy.",
                "Package \"" + name + "\" passed its delivery date without being picked up, so it was cancelled.",
                "/orders/" + parcel.orderId());
        // Lambda btb-customer-notifier gửi email cho khách từ sự kiện này.
        outbox.record("package.cancelled_unclaimed", "ORDER_PACKAGE", parcel.packageId(), Map.of(
                "orderId", parcel.orderId(),
                "customerId", parcel.customerId(),
                "packageName", name,
                "cancelledAt", now));
    }
}
