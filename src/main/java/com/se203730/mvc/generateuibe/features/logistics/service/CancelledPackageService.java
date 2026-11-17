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

@Service
@RequiredArgsConstructor
public class CancelledPackageService {
    private final CancelledPackageRepository packages;
    private final AccountAccessService accounts;

    public record PackageView(UUID packageId, UUID orderId, String packageName,
                              String packageNumber, Instant cancelledAt) {}

    @Transactional(readOnly = true)
    public Page<PackageView> list(UUID userId, int page, int size) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return packages.findCancelled(PageRequest.of(page, size)).map(item -> new PackageView(
                item.getPackageId(), item.getOrderId(), item.getPackageName(), item.getPackageNumber(), item.getCancelledAt()));
    }
}
