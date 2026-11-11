package com.se203730.mvc.generateuibe.features.customer.quote.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.AddQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.QuoteAllocationRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.QuoteContactRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.request.UpdateQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.quote.dto.response.QuoteResponse;
import com.se203730.mvc.generateuibe.features.customer.quote.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;

/** Khách được sửa DRAFT; gửi duyệt không tạo Order, không giữ tồn kho. */
@Service
@RequiredArgsConstructor
public class QuoteService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern PHONE = Pattern.compile("^\\+84[35789][0-9]{8}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final QuoteRepository quotes;
    private final AccountAccessService accounts;

    // Tỉ lệ VAT demo: cấu hình ở application-local.properties nếu cần.
    // Không mặc định một mức VAT thực tế cho mọi sản phẩm.
    @Value("${app.quote.vat-rate:0}")
    private BigDecimal vatRate;

    @Transactional
    public QuoteResponse create(UUID customerId) {
        requireCustomer(customerId);
        return quotes.detail(quotes.create(customerId));
    }

    @Transactional(readOnly = true)
    public QuoteResponse get(UUID customerId, UUID quoteId) {
        requireCustomer(customerId);
        requireOwned(customerId, quoteId, false);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse updateContact(UUID customerId, UUID quoteId, QuoteContactRequest req) {
        requireDraft(customerId, quoteId);
        quotes.saveContact(quoteId,
                trim(req.contactName()), trim(req.contactPhone()),
                trim(req.contactEmail()), trim(req.companyName()),
                trim(req.customerPoReference()), trim(req.note()));
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse addItem(UUID customerId, UUID quoteId, AddQuoteItemRequest req) {
        requireDraft(customerId, quoteId);
        if (quotes.containsProduct(quoteId, req.productId())) {
            throw new BusinessException(ErrorCode.CONFLICT); // Một sản phẩm, một dòng / Quote.
        }
        PriceSnapshot snapshot = price(req.productId(), req.quantity());
        quotes.addItem(quoteId, snapshot.product(), req.quantity(), snapshot.tierId(),
                snapshot.volume(), snapshot.sale(), checkedVatRate(), trim(req.customerNote()));
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse updateItem(UUID customerId, UUID quoteId,
                                    UUID itemId, UpdateQuoteItemRequest req) {
        requireDraft(customerId, quoteId);
        var item = requireItem(quoteId, itemId);
        validateQuantityScale(req.quantity());
        // Không giảm quantity thấp hơn tổng đã phân bổ vào các gói còn hiệu lực.
        if (quotes.allocated(quoteId, itemId, null).compareTo(req.quantity()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        PriceSnapshot snapshot = price(item.productId(), req.quantity());
        quotes.updateItem(itemId, snapshot.product(), req.quantity(),
                snapshot.tierId(), snapshot.volume(), snapshot.sale(), checkedVatRate(), trim(req.customerNote()));
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse deleteItem(UUID customerId, UUID quoteId, UUID itemId) {
        requireDraft(customerId, quoteId);
        requireItem(quoteId, itemId);
        quotes.deleteItem(itemId); // Các phân bổ của item được FK ON DELETE CASCADE dọn.
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse addPackage(UUID customerId, UUID quoteId, QuotePackageRequest req) {
        requireDraft(customerId, quoteId);
        validatePackage(req);
        quotes.createPackage(quoteId, customerId, req.packageName().trim(),
                req.deliveryDate(), req.deliveryAddress().trim());
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse updatePackage(UUID customerId, UUID quoteId,
                                       UUID packageId, QuotePackageRequest req) {
        requireDraft(customerId, quoteId);
        requirePackage(quoteId, packageId);
        validatePackage(req);
        quotes.updatePackage(packageId, req.packageName().trim(),
                req.deliveryDate(), req.deliveryAddress().trim());
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse deletePackage(UUID customerId, UUID quoteId, UUID packageId) {
        requireDraft(customerId, quoteId);
        requirePackage(quoteId, packageId);
        quotes.cancelPackage(packageId); // Hủy mềm. Lượng hàng cần phân bổ lại.
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse allocate(UUID customerId, UUID quoteId,
                                  UUID packageId, UUID itemId, QuoteAllocationRequest req) {
        requireDraft(customerId, quoteId);
        requirePackage(quoteId, packageId);
        var item = requireItem(quoteId, itemId);
        validateQuantityScale(req.quantity());
        // Cho phép chia lượng giữa nhiều package, nhưng mỗi phần theo bước lượng của sản phẩm.
        var product = quotes.publishedProduct(item.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
        if (req.quantity().remainder(product.step()).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        BigDecimal other = quotes.allocated(quoteId, itemId, packageId);
        if (other.add(req.quantity()).compareTo(item.quantity()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        quotes.upsertAllocation(quoteId, packageId, itemId, req.quantity());
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse removeAllocation(UUID customerId, UUID quoteId, UUID packageId, UUID itemId) {
        requireDraft(customerId, quoteId);
        requirePackage(quoteId, packageId);
        requireItem(quoteId, itemId);
        quotes.deleteAllocation(quoteId, packageId, itemId);
        quotes.recalculate(quoteId, true);
        return quotes.detail(quoteId);
    }

    @Transactional
    public QuoteResponse submit(UUID customerId, UUID quoteId) {
        requireDraft(customerId, quoteId);
        QuoteResponse draft = quotes.detail(quoteId);
        if (draft.items().isEmpty() || !quotes.hasActivePackage(quoteId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (isBlank(draft.contactName()) || isBlank(draft.contactPhone()) ||
                isBlank(draft.contactEmail()) || !PHONE.matcher(draft.contactPhone()).matches() ||
                !EMAIL.matcher(draft.contactEmail()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (quotes.hasPastDeliveryDate(quoteId, LocalDate.now(BUSINESS_ZONE)) ||
                !quotes.allItemsFullyAllocated(quoteId)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        // Gửi duyệt phải lấy lại giá theo thông tin hiện hành, nhưng không giữ hàng.
        for (QuoteResponse.Item item : draft.items()) {
            PriceSnapshot snapshot = price(item.productId(), item.quantity());
            quotes.updateItem(item.id(), snapshot.product(), item.quantity(),
                    snapshot.tierId(), snapshot.volume(), snapshot.sale(), checkedVatRate(), item.customerNote());
        }
        quotes.recalculate(quoteId, false);
        quotes.submit(quoteId);
        return quotes.detail(quoteId);
    }

    private QuoteRepository.Header requireOwned(UUID customerId, UUID quoteId, boolean lock) {
        QuoteRepository.Header header = quotes.findHeader(quoteId, lock)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!header.customerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return header;
    }

    private void requireDraft(UUID customerId, UUID quoteId) {
        requireCustomer(customerId);
        QuoteRepository.Header header = requireOwned(customerId, quoteId, true);
        if (!"DRAFT".equals(header.status())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private void requireCustomer(UUID customerId) {
        if (accounts.requireActiveUser(customerId).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private QuoteRepository.ItemData requireItem(UUID quoteId, UUID itemId) {
        return quotes.itemData(quoteId, itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requirePackage(UUID quoteId, UUID packageId) {
        if (!quotes.activePackage(quoteId, packageId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validatePackage(QuotePackageRequest req) {
        if (isBlank(req.packageName()) || isBlank(req.deliveryAddress()) ||
                req.deliveryDate() == null || req.deliveryDate().isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private record PriceSnapshot(QuoteRepository.ProductPrice product,
                                 UUID tierId, BigDecimal volume, BigDecimal sale) {}

    private PriceSnapshot price(UUID productId, BigDecimal quantity) {
        validateQuantityScale(quantity);
        var p = quotes.publishedProduct(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (p.listPrice() == null || p.listPrice().signum() < 0 ||
                p.moq() == null || p.step() == null || p.step().signum() <= 0 ||
                quantity.compareTo(p.moq()) < 0 ||
                quantity.subtract(p.moq()).remainder(p.step()).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var tier = quotes.applicableTier(productId, quantity).orElse(null);
        BigDecimal volume = tier == null ? p.listPrice() : p.listPrice().min(tier.unitPrice());
        BigDecimal sale = p.activeSalePrice() == null
                ? volume : volume.min(p.activeSalePrice());
        return new PriceSnapshot(p, tier == null ? null : tier.id(), volume, sale);
    }

    private BigDecimal checkedVatRate() {
        if (vatRate == null || vatRate.signum() < 0 ||
                vatRate.compareTo(new BigDecimal("100")) > 0 ||
                vatRate.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return vatRate;
    }

    private void validateQuantityScale(BigDecimal value) {
        if (value == null || value.signum() <= 0 ||
                value.stripTrailingZeros().scale() > 3 ||
                value.compareTo(new BigDecimal("999999999.999")) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
