package com.se203730.mvc.generateuibe.features.admin.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.admin.dto.request.CreateStaffRequest;
import com.se203730.mvc.generateuibe.features.admin.dto.response.StaffResponse;
import com.se203730.mvc.generateuibe.features.admin.enums.StaffRole;
import com.se203730.mvc.generateuibe.features.admin.service.AdminStaffService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/staff")
@Tag(name = "Admin - Staff Accounts")
public class CreateStaffController {
    @Autowired
    private AdminStaffService staffService;

    @GetMapping
    public ResponseEntity<ResponseObject<PageResponse<StaffResponse>>> list(
            @CurrentUserUUID
            UUID adminId,
            @RequestParam(required = false)
            StaffRole role,
            @RequestParam(required = false)
            @Size(max = 254)
            String keyword,
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,
            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(100)
            int size) {
        Page<StaffResponse> staffPage = staffService.list(adminId, role, keyword, page, size);
        PageResponse<StaffResponse> data = PageResponse.from(staffPage);

        ResponseObject<PageResponse<StaffResponse>> response = new ResponseObject<>();
        response.setStatus(200);
        response.setMessage("Success");
        response.setData(data);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ResponseObject<StaffResponse>> create(
            @CurrentUserUUID
            UUID adminId,
            @Valid
            @RequestBody
            CreateStaffRequest request) {
        StaffResponse staff = staffService.create(adminId, request);

        ResponseObject<StaffResponse> response = new ResponseObject<>();
        response.setStatus(201);
        response.setMessage("Staff account created.");
        response.setData(staff);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
    }
}
