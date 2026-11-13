package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.se203730.mvc.generateuibe.features.customer.dto.response.BankPaymentResponse;
import com.se203730.mvc.generateuibe.features.customer.service.BankPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer - Bank Payments")
public class BankPaymentController {
    @Autowired
    private BankPaymentService paymentService;

    @PostMapping("/orders/{orderId}/bank-payment")
    public ResponseEntity<ResponseObject<BankPaymentResponse>> create(
            @CurrentUserUUID
            UUID customerId,
            @PathVariable
            UUID orderId) {
        BankPaymentResponse data = paymentService.create(customerId, orderId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }

    @GetMapping("/bank-payments/{paymentId}")
    public ResponseEntity<ResponseObject<BankPaymentResponse>> get(
            @CurrentUserUUID
            UUID customerId,
            @PathVariable
            UUID paymentId) {
        BankPaymentResponse data = paymentService.get(customerId, paymentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }
}
