package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.repository.PackageStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class PackageExpiryScheduler {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PackageStockRepository packages;
    private final PackageExpiryService expiry;

    // Chạy định kỳ; sau 0h (giờ Việt Nam) các package của ngày hôm trước chưa ai nhận sẽ bị hủy.
    @Scheduled(fixedDelayString = "${app.package-expiry.poll-ms:600000}")
    public void cancelUnclaimedPackages() {
        for (var parcel : packages.findUnclaimedPastDeliveryDate(LocalDate.now(VIETNAM), 100)) {
            try {
                expiry.cancel(parcel);
            } catch (RuntimeException exception) {
                log.error("Could not cancel expired package {}", parcel.packageId(), exception);
            }
        }
    }
}
