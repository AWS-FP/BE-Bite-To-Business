package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.request.CreateRefundRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.RefundRequestResponse;
import com.se203730.mvc.generateuibe.features.customer.entity.Order;
import com.se203730.mvc.generateuibe.features.customer.entity.OrderPackage;
import com.se203730.mvc.generateuibe.features.customer.entity.PackageRefund;
import com.se203730.mvc.generateuibe.features.customer.entity.Payment;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderPackageRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.OrderRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PackageRefundRepository;
import com.se203730.mvc.generateuibe.features.customer.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CustomerRefundService {
    private final AccountAccessService accounts;
    private final OrderRepository orders;
    private final OrderPackageRepository packages;
    private final PaymentRepository payments;
    private final PackageRefundRepository refunds;
    private final DatabaseClock clock;

    @Transactional
    public List<RefundRequestResponse> create(UUID customerId, UUID orderId, CreateRefundRequest request) {
        requireCustomer(customerId);
        Order order = orders.findForUpdate(orderId)
                .filter(item -> item.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Payment payment = payments.findByOrderIdAndStatus(orderId, "SUCCESS")
                .filter(item -> item.getPaidAt() != null && "VND".equals(item.getCurrency())
                        && item.getAmount().compareTo(order.getTotalAmount()) == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_PAYMENT_REQUIRED));

        List<OrderPackage> all = packages.findByOrderIdOrderByIdAsc(orderId);
        List<UUID> selectedIds = request.packageIds() == null ? List.of() : request.packageIds();
        if ((request.entireOrder() && !selectedIds.isEmpty())
                || (!request.entireOrder() && selectedIds.isEmpty())
                || new HashSet<>(selectedIds).size() != selectedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<OrderPackage> selected = new ArrayList<>();
        for (OrderPackage item : all) {
            if (request.entireOrder() || selectedIds.contains(item.getId())) {
                selected.add(item);
            }
        }
        if (selected.isEmpty() || (!request.entireOrder() && selected.size() != selectedIds.size())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<UUID> packageIds = new ArrayList<>();
        for (OrderPackage item : selected) {
            packageIds.add(item.getId());
        }
        if (refunds.existsByOrderPackageIdInAndStatusIn(packageIds,
                List.of("REQUESTED", "APPROVED", "PROCESSING", "REFUNDED"))) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        Instant now = clock.now();
        UUID groupId = UUID.randomUUID();
        List<RefundRequestResponse> result = new ArrayList<>();
        for (OrderPackage parcel : selected) {
            PackageRefund refund = new PackageRefund();
            refund.setId(UUID.randomUUID());
            refund.setOrderId(orderId);
            refund.setOrderPackageId(parcel.getId());
            refund.setPaymentId(payment.getId());
            refund.setRequestedBy(customerId);
            refund.setRequestGroupId(groupId);
            refund.setStatus("REQUESTED");
            refund.setReason(request.reason().trim());
            refund.setRecipientBank(request.bank().trim());
            refund.setRecipientAccount(request.accountNumber());
            refund.setRecipientName(request.accountName().trim());
            refund.setCreatedAt(now);
            refund.setUpdatedAt(now);
            refunds.save(refund);
            result.add(RefundRequestResponse.from(refund, parcel.getPackageName()));
        }
        refunds.flush();
        return result;
    }

    @Transactional(readOnly = true)
    public List<RefundRequestResponse> list(UUID customerId, UUID orderId) {
        requireCustomer(customerId);
        orders.findById(orderId).filter(order -> order.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Map<UUID, String> names = new HashMap<>();
        for (OrderPackage item : packages.findByOrderIdOrderByIdAsc(orderId)) {
            names.put(item.getId(), item.getPackageName());
        }
        List<RefundRequestResponse> result = new ArrayList<>();
        for (PackageRefund refund : refunds.findByOrderIdAndRequestedByOrderByCreatedAtDesc(orderId, customerId)) {
            result.add(RefundRequestResponse.from(refund, names.get(refund.getOrderPackageId())));
        }
        return result;
    }

    private void requireCustomer(UUID customerId) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
