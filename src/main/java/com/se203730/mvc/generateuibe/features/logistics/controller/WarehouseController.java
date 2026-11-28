package com.se203730.mvc.generateuibe.features.logistics.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.logistics.service.WarehouseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/logistics/inventory")
@PreAuthorize("hasRole('LOGISTICS_STAFF')")
@RequiredArgsConstructor
@Tag(name = "Logistics - Inventory")
public class WarehouseController {
    private final WarehouseService warehouse;

    public record StockChange(@NotNull UUID movementId, @NotNull @Digits(integer = 9, fraction = 3) BigDecimal quantity,
                              @Size(max = 1000) String reason) {}


    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<WarehouseService.StockResponse>>> list(@CurrentUserUUID UUID userId,
            @RequestParam(required = false) @Size(max = 200) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.se203730.mvc.generateuibe.common.exception.BusinessException(
                    com.se203730.mvc.generateuibe.common.exception.ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", PageResponse.from(warehouse.list(userId, keyword, page, size))));
    }

    @PostMapping("/{productId}/receipts")
    public ResponseEntity<ResponseObject<WarehouseService.StockResponse>> receive(@CurrentUserUUID UUID userId,
            @PathVariable UUID productId, @Valid @RequestBody StockChange request) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", warehouse.change(userId, productId,
                request.movementId(), "RECEIVE", request.quantity(), request.reason())));
    }

    @PostMapping("/{productId}/adjustments")
    public ResponseEntity<ResponseObject<WarehouseService.StockResponse>> adjust(@CurrentUserUUID UUID userId,
            @PathVariable UUID productId, @Valid @RequestBody StockChange request) {
        return ResponseEntity.ok(new ResponseObject<>(200, "Success", warehouse.change(userId, productId,
                request.movementId(), "ADJUST", request.quantity(), request.reason())));
    }
}
