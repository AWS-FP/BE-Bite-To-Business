package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.dto.response.*;
import com.se203730.mvc.generateuibe.features.customer.repository.CustomerOrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerOrderQueryService {
    private final CustomerOrderQueryRepository repository;

    /**
     * @param group ALL, ACTIVE (CONFIRMED/PROCESSING: awaiting payment or delivery)
     *              or FINISHED (COMPLETED/CANCELLED).
     */
    public CustomerOrdersPageResponse list(UUID customerId, String group, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        String normalized = group == null ? "ALL" : group.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ALL", "ACTIVE", "FINISHED").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long total = repository.countForCustomer(customerId, normalized);
        return new CustomerOrdersPageResponse(
                repository.listForCustomer(customerId, normalized, size, (long) page * size),
                page, size, total, (int) ((total + size - 1) / size));
    }

    public CustomerOrderDetailResponse get(UUID customerId, UUID orderId) {
        CustomerOrderSummaryResponse order = repository.findOwned(customerId, orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new CustomerOrderDetailResponse(order,
                repository.items(customerId, orderId), repository.packages(customerId, orderId));
    }
}
