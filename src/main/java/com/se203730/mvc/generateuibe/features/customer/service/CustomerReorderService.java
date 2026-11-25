package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuoteContactRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.repository.CustomerReorderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates a NEW quote from a previous order, with freshly validated prices and shipping. */
@Service
public class CustomerReorderService {
    private static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CustomerReorderRepository previous;
    private final QuoteDraftService drafts;
    private final QuotePackageRoutingService routing;
    private final QuoteWorkflowService workflow;

    public CustomerReorderService(CustomerReorderRepository previous,
                                  QuoteDraftService drafts,
                                  QuotePackageRoutingService routing,
                                  QuoteWorkflowService workflow) {
        this.previous = previous;
        this.drafts = drafts;
        this.routing = routing;
        this.workflow = workflow;
    }

    @Transactional
    public QuoteDraftResponse prepare(UUID customerId, UUID orderId) {
        // Ownership check is part of the read, not only the controller's role check.
        var contact = previous.contact(customerId, orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        var oldItems = previous.lines(customerId, orderId);
        var oldParcels = previous.parcels(customerId, orderId);
        if (oldItems.isEmpty() || oldParcels.isEmpty()) {
            throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
        }
        if (contact.name() == null || contact.phone() == null || contact.email() == null) {
            throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
        }

        // A separate quote prevents wiping out the customer's unfinished cart/quote.
        QuoteDraftResponse quote = drafts.createFresh(customerId);
        Set<UUID> seen = new HashSet<>();
        for (var old : oldItems) {
            if (old.productId() == null || old.quantity() == null || !seen.add(old.productId())) {
                throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            }
            // Pricing tiers, VAT, status and quantity steps are recalculated on today's catalog.
            quote = drafts.putItem(customerId, quote.id(),
                    new UpdateQuoteItemRequest(old.productId(), old.quantity()));
        }
        quote = drafts.updateContact(customerId, quote.id(), new QuoteContactRequest(
                contact.name(), contact.phone(), contact.email(), contact.company(), contact.note()));
        Map<UUID, UUID> quoteItemIds = new HashMap<>();
        for (var item : quote.items()) quoteItemIds.put(item.productId(), item.id());

        LocalDate earliestDelivery = LocalDate.now(DELIVERY_ZONE).plusDays(2);
        LocalDate firstOldDate = oldParcels.stream().map(CustomerReorderRepository.Parcel::deliveryDate)
                .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        for (var old : oldParcels) {
            if (old.latitude() == null || old.longitude() == null || old.city() == null ||
                    !"HCM".equals(old.city()) || old.address() == null || old.address().isBlank() ||
                    old.recipientName() == null || old.recipientPhone() == null) {
                throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            }
            var lines = previous.allocations(customerId, orderId, old.id());
            if (lines.isEmpty()) throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
            List<QuotePackageRequest.Allocation> assigned = lines.stream().map(line -> {
                UUID quoteItemId = quoteItemIds.get(line.productId());
                if (quoteItemId == null) throw new BusinessException(ErrorCode.QUOTE_INCOMPLETE);
                return new QuotePackageRequest.Allocation(quoteItemId, line.quantity());
            }).toList();
            // Preserve the original spacing between deliveries, shifting past dates to a
            // fresh delivery window. The customer reviews the proposed dates before ordering.
            LocalDate deliveryDate = earliestDelivery;
            if (firstOldDate != null && old.deliveryDate() != null) {
                long offsetDays = Math.max(0, ChronoUnit.DAYS.between(firstOldDate, old.deliveryDate()));
                LocalDate shifted = earliestDelivery.plusDays(offsetDays);
                deliveryDate = old.deliveryDate().isAfter(shifted) ? old.deliveryDate() : shifted;
            }
            routing.save(customerId, quote.id(), null, new QuotePackageRequest(
                    old.name() == null || old.name().isBlank() ? "Delivery" : old.name(),
                    old.recipientName(), old.recipientPhone(), deliveryDate, old.address(),
                    old.note(), old.city(), old.latitude(), old.longitude(), assigned));
        }
        quote = drafts.get(customerId, quote.id());
        // Verifies stock, VAT and current price before showing the payment-confirmation step.
        // If any check fails, @Transactional rolls back this new quote atomically.
        return workflow.validatePricing(customerId, quote.id(), quote.revision());
    }
}
