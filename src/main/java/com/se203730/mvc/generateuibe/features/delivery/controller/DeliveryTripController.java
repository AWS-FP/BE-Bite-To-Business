package com.se203730.mvc.generateuibe.features.delivery.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.DeliveryTripResponse;
import com.se203730.mvc.generateuibe.features.delivery.dto.response.DeliveryTripSummaryResponse;
import com.se203730.mvc.generateuibe.features.delivery.enums.DeliveryTripStatus;
import com.se203730.mvc.generateuibe.features.delivery.service.DeliveryTripService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/trips")
@PreAuthorize("hasRole('DELIVERY_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Delivery - Trips")
public class DeliveryTripController {
    private final DeliveryTripService delivery;

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<DeliveryTripSummaryResponse>>> list(
            @CurrentUserUUID
            UUID staffId,
            @RequestParam(required = false)
            DeliveryTripStatus status,
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,
            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(100)
            int size) {
        return success(PageResponse.from(delivery.list(staffId, status, page, size)));
    }

    @GetMapping("/current")
    public ResponseEntity<ResponseObject<DeliveryTripResponse>> current(
            @CurrentUserUUID
            UUID staffId) {
        return success(delivery.current(staffId));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<ResponseObject<DeliveryTripResponse>> detail(
            @CurrentUserUUID
            UUID staffId,
            @PathVariable
            UUID tripId) {
        return success(delivery.detail(staffId, tripId));
    }

    @PostMapping("/{tripId}/start")
    public ResponseEntity<ResponseObject<DeliveryTripResponse>> start(
            @CurrentUserUUID
            UUID staffId,
            @PathVariable
            UUID tripId) {
        return success(delivery.start(staffId, tripId));
    }

    @PostMapping("/{tripId}/stops/{stopId}/confirm-delivery")
    public ResponseEntity<ResponseObject<DeliveryTripResponse>> confirmStop(
            @CurrentUserUUID
            UUID staffId,
            @PathVariable
            UUID tripId,
            @PathVariable
            UUID stopId) {
        return success(delivery.confirmStop(staffId, tripId, stopId));
    }

    @PostMapping("/{tripId}/complete")
    public ResponseEntity<ResponseObject<DeliveryTripResponse>> complete(
            @CurrentUserUUID
            UUID staffId,
            @PathVariable
            UUID tripId) {
        return success(delivery.complete(staffId, tripId));
    }

    private <T> ResponseEntity<ResponseObject<T>> success(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseObject<>(200, "Success", data));
    }
}
