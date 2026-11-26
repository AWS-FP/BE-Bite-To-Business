package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse;
import com.se203730.mvc.generateuibe.features.admin.dto.response.FinanceReportResponse.*;
import com.se203730.mvc.generateuibe.features.admin.service.FinanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Admin - Financial Reports")
public class FinanceReportController {
    private final FinanceReportService reports;
    public FinanceReportController(FinanceReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/report")
    @Operation(summary = "Matched receipts, completed orders, settled refunds and unallocated SePay receipts",
            description = "Inclusive Vietnamese calendar dates, at most 366 days. Net collected is matched successful payments minus settled package refunds in the period. It includes VAT and shipping recorded in payments and is not profit. Test and live records share the existing database and cannot be separated reliably.")
    public ResponseEntity<ResponseObject<FinanceReportResponse>> report(@CurrentUserUUID UUID adminId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") @Pattern(regexp = "day|week|month") String group,
            @RequestParam(required = false) UUID customerId) {
        return ok(reports.report(adminId, from, to, group, customerId));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Paginated payment, package refund and SePay review records",
            description = "SUCCESS payments use paid_at, settled refunds use refunded_at, other attempts use created_at. SePay reviews use received_at. Review records are audit records and must not be added to successful payments. Use the report endpoint for totals.")
    public ResponseEntity<ResponseObject<PageResponse<Transaction>>> transactions(@CurrentUserUUID UUID adminId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|PAYMENT|REFUND|SEPAY_REVIEW") String kind,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|PENDING|SUCCESS|FAILED|CANCELLED|EXPIRED|REQUESTED|APPROVED|REJECTED|PROCESSING|REFUNDED|REVIEW|RESTORED|IGNORED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(reports.transactions(adminId, from, to, customerId, kind, status, page, size));
    }

    @GetMapping("/customers")
    @Operation(summary = "Order counts and matched net receipts per customer in the selected period")
    public ResponseEntity<ResponseObject<PageResponse<Customer>>> customers(@CurrentUserUUID UUID adminId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(reports.customers(adminId, from, to, customerId, page, size));
    }

    private <T> ResponseEntity<ResponseObject<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResponseObject<>(200, "Success", data));
    }
}

