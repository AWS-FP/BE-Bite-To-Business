package com.se203730.mvc.generateuibe.features.logistics.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.customer.service.NotificationEventPublisher;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.logistics.entity.ProductRequest;
import com.se203730.mvc.generateuibe.features.logistics.repository.ProductRequestRepository;
import com.se203730.mvc.generateuibe.features.logistics.entity.ProductRequestResponse;
import com.se203730.mvc.generateuibe.features.logistics.repository.ProductRequestResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductRequestService {
    private final ProductRequestRepository requests;
    private final AccountAccessService accounts;
    private final DatabaseClock clock;
    private final ProductRequestResponseRepository responses;
    private final ApplicationEventPublisher events;
    private final NotificationEventPublisher notificationEvents;

    public record RequestView(UUID id, UUID customerId, String productName, String referenceUrl,
                              String customerNote, String status, Instant createdAt, Instant updatedAt) {}

    /** Yêu cầu của khách kèm phản hồi mới nhất của Logistics (nếu có). */
    public record MyRequestView(UUID id, String productName, String referenceUrl, String customerNote,
                                String status, boolean notifyByEmail, Instant createdAt, Instant updatedAt,
                                String responseNote, Instant respondedAt, UUID addedProductId,
                                String addedProductName, BigDecimal sellingPrice, String unitCode) {}

    @Transactional(readOnly = true)
    public Page<MyRequestView> mine(UUID userId, int page, int size) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Page<ProductRequest> result = requests.findByCustomerIdOrderByCreatedAtDescIdAsc(userId, PageRequest.of(page, size));
        // Một truy vấn lấy phản hồi cho cả trang; giữ bản mới nhất của mỗi yêu cầu.
        Map<UUID, ProductRequestResponse> latest = new HashMap<>();
        List<UUID> ids = result.getContent().stream().map(ProductRequest::getId).toList();
        if (!ids.isEmpty()) {
            for (ProductRequestResponse item : responses.findByRequestIdInOrderByCreatedAtDesc(ids)) {
                latest.putIfAbsent(item.getRequestId(), item);
            }
        }
        return result.map(item -> {
            ProductRequestResponse reply = latest.get(item.getId());
            return new MyRequestView(item.getId(), item.getProductName(), item.getReferenceUrl(),
                    item.getCustomerNote(), item.getStatus(), item.isNotifyByEmail(), item.getCreatedAt(),
                    item.getUpdatedAt(), reply == null ? null : reply.getResponseNote(),
                    reply == null ? null : reply.getCreatedAt(), reply == null ? null : reply.getAddedProductId(),
                    reply == null ? null : reply.getProductName(), reply == null ? null : reply.getSellingPrice(),
                    reply == null ? null : reply.getUnitCode());
        });
    }

    @Transactional
    public RequestView create(UUID userId, String name, String referenceUrl, String note, boolean notifyByEmail) {
        var customer = accounts.requireActiveUser(userId);
        if (customer.getRole() != UserRole.CUSTOMER || name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.now();
        ProductRequest request = new ProductRequest();
        request.setId(UUID.randomUUID());
        request.setCustomerId(userId);
        request.setProductName(name.trim());
        request.setReferenceUrl(referenceUrl == null || referenceUrl.isBlank() ? null : referenceUrl.trim());
        request.setCustomerNote(note == null || note.isBlank() ? null : note.trim());
        request.setContactEmail(customer.getEmail());
        request.setNotifyByEmail(notifyByEmail);
        request.setStatus("PENDING");
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        requests.save(request);
        return view(request);
    }

    @Transactional(readOnly = true)
    public Page<RequestView> list(UUID userId, String status, int page, int size) {
        requireLogistics(userId);
        if (status != null && !List.of("PENDING", "REVIEWING", "ADDED", "REJECTED").contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<ProductRequest> result = status == null ? requests.findAllByOrderByCreatedAtDesc(pageable)
                : requests.findByStatusOrderByCreatedAtDesc(status, pageable);
        return result.map(this::view);
    }

    @Transactional
    public RequestView startReview(UUID userId, UUID id) {
        requireLogistics(userId);
        ProductRequest request = requests.findForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PENDING".equals(request.getStatus()) && !"REVIEWING".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        request.setStatus("REVIEWING");
        request.setUpdatedAt(clock.now());
        return view(request);
    }

    @Transactional
    public RequestView respond(UUID userId, UUID id, String decision, String note, UUID productId) {
        requireLogistics(userId);
        ProductRequest request = requests.findForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PENDING".equals(request.getStatus()) && !"REVIEWING".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (!("ADDED".equals(decision) || "REJECTED".equals(decision))
                || note == null || note.isBlank()
                || ("ADDED".equals(decision) && productId == null)
                || ("REJECTED".equals(decision) && productId != null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.now();
        ProductRequestResponse response = new ProductRequestResponse();
        response.setId(UUID.randomUUID());
        response.setRequestId(id);
        response.setRespondedBy(userId);
        response.setResponseNote(note.trim());
        response.setRecipientEmail(accounts.requireActiveUser(request.getCustomerId()).getEmail());
        response.setCreatedAt(now);
        response.setUpdatedAt(now);
        response.setEmailStatus(request.isNotifyByEmail() ? "PENDING" : "SKIPPED");
        response.setNextEmailAttemptAt(request.isNotifyByEmail() ? now : null);
        if (productId != null) {
            var product = responses.findProductSnapshot(productId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            response.setAddedProductId(productId);
            response.setProductName(product.getName());
            response.setProductAddedAt(product.getAddedAt());
            response.setSellingPrice(product.getPrice());
            response.setAvailableQuantity(product.getAvailable());
            response.setUnitCode(product.getUnit());
        }
        responses.save(response);
        if (request.isNotifyByEmail()) {
            events.publishEvent(new ProductRequestEmailQueued());
        }
        request.setStatus(decision);
        request.setUpdatedAt(now);
        notificationEvents.publish(request.getCustomerId(), "SOURCING_REPLY", id, "PRODUCT_REQUEST",
                "Yêu cầu sản phẩm đã được phản hồi", "Your product request has an update",
                "Yêu cầu '" + request.getProductName() + "' đã được " +
                    ("ADDED".equals(decision) ? "chấp thuận." : "từ chối."),
                "Your request for '" + request.getProductName() + "' was " +
                    ("ADDED".equals(decision) ? "accepted." : "rejected."),
                null);
        return view(request);
    }

    private RequestView view(ProductRequest item) {
        return new RequestView(item.getId(), item.getCustomerId(), item.getProductName(), item.getReferenceUrl(),
                item.getCustomerNote(), item.getStatus(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private void requireLogistics(UUID userId) {
        if (accounts.requireActiveUser(userId).getRole() != UserRole.LOGISTICS_STAFF) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
