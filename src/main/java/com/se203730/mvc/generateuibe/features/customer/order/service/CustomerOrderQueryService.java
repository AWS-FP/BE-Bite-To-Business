package com.se203730.mvc.generateuibe.features.customer.order.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.order.dto.response.*;
import com.se203730.mvc.generateuibe.features.customer.order.repository.CustomerOrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerOrderQueryService {
    private final CustomerOrderQueryRepository repository;

    public CustomerOrdersPageResponse list(UUID customerId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        long total = repository.countForCustomer(customerId);
        return new CustomerOrdersPageResponse(repository.listForCustomer(customerId, size, (long) page * size),
                page, size, total, (int) ((total + size - 1) / size));
    }

    public CustomerOrderDetailResponse get(UUID customerId, UUID orderId) {
        CustomerOrderSummaryResponse order = repository.findOwned(customerId, orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new CustomerOrderDetailResponse(order,
                repository.items(customerId, orderId), repository.packages(customerId, orderId));
    }
}
