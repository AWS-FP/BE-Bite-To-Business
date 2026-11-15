package com.se203730.mvc.generateuibe.features.customer.invoice.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.invoice.dto.response.CustomerInvoiceResponse;
import com.se203730.mvc.generateuibe.features.customer.invoice.dto.response.OrderVatPreviewResponse;
import com.se203730.mvc.generateuibe.features.customer.invoice.service.CustomerInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer - Invoices (read-only)")
public class CustomerInvoiceController {
    private final CustomerInvoiceService service;

    public CustomerInvoiceController(CustomerInvoiceService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}/invoice")
    @Operation(summary = "Get my order invoice status and official PDF link, if issued")
    public ResponseEntity<ResponseObject<CustomerInvoiceResponse>> getInvoice(
            @Parameter(hidden = true) @CurrentUserUUID UUID customerId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.getInvoice(customerId, orderId)));
    }

    @GetMapping("/{orderId}/vat-preview")
    @Operation(summary = "Inspect VAT amounts from my order snapshot (not an official invoice)")
    public ResponseEntity<ResponseObject<OrderVatPreviewResponse>> previewVat(
            @Parameter(hidden = true) @CurrentUserUUID UUID customerId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.previewVat(customerId, orderId)));
    }
}
