package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateQuoteItemRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuotePackageRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuoteCouponsRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.request.QuoteRevisionRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteDraftResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuotePackageResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.QuoteSummaryResponse;
import com.se203730.mvc.generateuibe.features.customer.service.QuoteDraftService;
import com.se203730.mvc.generateuibe.features.customer.service.QuotePackageService;
import com.se203730.mvc.generateuibe.features.customer.service.QuotePackageRoutingService;
import com.se203730.mvc.generateuibe.features.customer.service.QuoteCouponService;
import com.se203730.mvc.generateuibe.features.customer.service.QuoteWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/customer/quotes")
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
public class CustomerQuoteController {
    @Autowired
    private QuoteDraftService service;

    @Autowired
    private QuotePackageService packages;

    @Autowired
    private QuotePackageRoutingService routing;

    @Autowired
    private QuoteCouponService coupons;

    @Autowired
    private QuoteWorkflowService workflow;

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<QuoteSummaryResponse>>> list(
            @CurrentUserUUID
            UUID customerId,
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,
            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(100)
            int size) {
        PageResponse<QuoteSummaryResponse> data = PageResponse.from(service.list(customerId, page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }

    @PostMapping("/draft")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> getOrCreate(@CurrentUserUUID UUID customerId) {
        return ok(service.getOrCreate(customerId));
    }

    @GetMapping("/{quoteId}")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> get(@CurrentUserUUID UUID customerId,
                                                                  @PathVariable UUID quoteId) {
        return ok(service.get(customerId, quoteId));
    }

    @PutMapping("/{quoteId}/items")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> putItem(@CurrentUserUUID UUID customerId,
                                                                      @PathVariable UUID quoteId,
                                                                      @Valid @RequestBody UpdateQuoteItemRequest request) {
        return ok(service.putItem(customerId, quoteId, request));
    }

    @PostMapping("/{quoteId}/refresh-prices")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> refreshPrices(@CurrentUserUUID UUID customerId,
                                                                            @PathVariable UUID quoteId) {
        return ok(service.refreshPrices(customerId, quoteId));
    }

    @PostMapping("/{quoteId}/submit")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> submit(@CurrentUserUUID UUID customerId,
                                                                    @PathVariable UUID quoteId,
                                                                    @Valid @RequestBody QuoteRevisionRequest request) {
        return ok(workflow.submit(customerId, quoteId, request.revision()));
    }

    @DeleteMapping("/{quoteId}/items/{itemId}")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> removeItem(@CurrentUserUUID UUID customerId,
                                                                         @PathVariable UUID quoteId,
                                                                         @PathVariable UUID itemId) {
        return ok(service.removeItem(customerId, quoteId, itemId));
    }

    @PutMapping("/{quoteId}/coupons")
    public ResponseEntity<ResponseObject<QuoteDraftResponse>> selectCoupons(@CurrentUserUUID UUID customerId,
                                                                           @PathVariable UUID quoteId,
                                                                           @Valid @RequestBody QuoteCouponsRequest request) {
        return ok(coupons.select(customerId, quoteId, request));
    }

    @GetMapping("/{quoteId}/packages")
    public ResponseEntity<ResponseObject<List<QuotePackageResponse>>> listPackages(@CurrentUserUUID UUID customerId,
                                                                                   @PathVariable UUID quoteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", packages.list(customerId, quoteId)));
    }

    @PostMapping("/{quoteId}/packages")
    public ResponseEntity<ResponseObject<QuotePackageResponse>> createPackage(@CurrentUserUUID UUID customerId,
                                                                              @PathVariable UUID quoteId,
                                                                              @Valid @RequestBody QuotePackageRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(201, "Package created.", routing.save(customerId, quoteId, null, request)));
    }

    @PutMapping("/{quoteId}/packages/{packageId}")
    public ResponseEntity<ResponseObject<QuotePackageResponse>> updatePackage(@CurrentUserUUID UUID customerId,
                                                                              @PathVariable UUID quoteId,
                                                                              @PathVariable UUID packageId,
                                                                              @Valid @RequestBody QuotePackageRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Package updated.", routing.save(customerId, quoteId, packageId, request)));
    }

    @DeleteMapping("/{quoteId}/packages/{packageId}")
    public ResponseEntity<Void> removePackage(@CurrentUserUUID UUID customerId, @PathVariable UUID quoteId,
                                               @PathVariable UUID packageId) {
        packages.remove(customerId, quoteId, packageId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<ResponseObject<QuoteDraftResponse>> ok(QuoteDraftResponse data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }
}
