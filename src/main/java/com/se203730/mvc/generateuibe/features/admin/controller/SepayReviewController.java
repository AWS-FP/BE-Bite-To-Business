package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.response.SepayReviewResponse;
import com.se203730.mvc.generateuibe.features.customer.service.BankPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payments/sepay")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Payment Reviews")
public class SepayReviewController {
    @Autowired
    private BankPaymentService paymentService;

    @GetMapping("/review")
    public ResponseEntity<ResponseObject<List<SepayReviewResponse>>> list() {
        List<SepayReviewResponse> data = paymentService.listReviewEvents();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }
}
