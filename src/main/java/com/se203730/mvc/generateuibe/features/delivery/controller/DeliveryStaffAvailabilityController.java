package com.se203730.mvc.generateuibe.features.delivery.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.delivery.service.DeliveryStaffAvailabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DeliveryStaffAvailabilityController {
    @Autowired
    private DeliveryStaffAvailabilityService service;

    public record UpdateRequest(@NotBlank @Pattern(regexp = "AVAILABLE|OFF_DUTY") String status) {}

    @GetMapping("/api/logistics/delivery-staff")
    @PreAuthorize("hasRole('LOGISTICS_STAFF')")
    public ResponseEntity<ResponseObject<List<DeliveryStaffAvailabilityService.Staff>>> list(@CurrentUserUUID UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.list(userId)));
    }

    @PutMapping("/api/delivery/availability")
    @PreAuthorize("hasRole('DELIVERY_STAFF')")
    public ResponseEntity<ResponseObject<String>> update(@CurrentUserUUID UUID userId,
                                                          @Valid @RequestBody UpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", service.update(userId, request.status())));
    }
}
