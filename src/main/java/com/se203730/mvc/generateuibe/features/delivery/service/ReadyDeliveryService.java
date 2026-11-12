package com.se203730.mvc.generateuibe.features.delivery.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.ReadyDeliveryOrderResponse;
import com.se203730.mvc.generateuibe.features.delivery.repository.ReadyDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadyDeliveryService {
    private final ReadyDeliveryRepository repository;
    private final AccountAccessService accounts;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ReadyDeliveryOrderResponse> list(UUID staffId, int page, int size) {
        if (accounts.requireActiveUser(staffId).getRole() != UserRole.DELIVERY_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var orders = repository.findReadyOrders(PageRequest.of(page, size));
        if (orders.isEmpty()) {
            return orders.map(order -> new ReadyDeliveryOrderResponse(order.getId(), order.getOrderNumber(), List.of()));
        }
        var orderIds = orders.getContent().stream().map(order -> order.getId()).toList();
        Map<UUID, List<OrderPackage>> packages = repository.findReadyPackages(orderIds).stream()
                .collect(Collectors.groupingBy(OrderPackage::getOrderId));
        return orders.map(order -> new ReadyDeliveryOrderResponse(order.getId(), order.getOrderNumber(),
                packages.getOrDefault(order.getId(), List.of()).stream()
                        .map(p -> new ReadyDeliveryOrderResponse.Package(p.getId(), p.getPackageNumber(),
                                p.getPackageName(), p.getDeliveryDate(), p.getDeliveryAddress()))
                        .toList()));
    }
}
