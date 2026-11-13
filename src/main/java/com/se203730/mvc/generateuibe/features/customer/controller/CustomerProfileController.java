package com.se203730.mvc.generateuibe.features.customer.controller;

import com.se203730.mvc.generateuibe.common.annotation.CurrentUserUUID;
import com.se203730.mvc.generateuibe.common.api.ResponseObject;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateCustomerProfileRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerProfileResponse;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer/profile")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer - Profile")
public class CustomerProfileController {
    @Autowired
    private CustomerProfileService profileService;

    @GetMapping
    public ResponseEntity<ResponseObject<CustomerProfileResponse>> getProfile(
            @CurrentUserUUID
            UUID customerId) {
        CustomerProfileResponse data = profileService.getProfile(customerId);
        ResponseObject<CustomerProfileResponse> response = new ResponseObject<>(200, "Success", data);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    @PutMapping
    public ResponseEntity<ResponseObject<CustomerProfileResponse>> updateProfile(
            @CurrentUserUUID
            UUID customerId,
            @Valid
            @RequestBody
            UpdateCustomerProfileRequest request) {
        CustomerProfileResponse data = profileService.updateProfile(customerId, request);
        ResponseObject<CustomerProfileResponse> response = new ResponseObject<>(200, "Profile updated.", data);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseObject<CustomerProfileResponse>> updateAvatar(
            @CurrentUserUUID UUID customerId,
            @RequestPart("file") MultipartFile file) {
        CustomerProfileResponse data = profileService.updateAvatar(customerId, file);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new ResponseObject<>(200, "Avatar updated.", data));
    }
}
