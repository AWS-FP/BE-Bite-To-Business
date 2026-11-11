package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.request.UpdateCustomerProfileRequest;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerProfileResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerProfileService {
    @Autowired
    private UserRepository users;

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private AccountAccessService accounts;

    @Autowired
    private DatabaseClock clock;

    @Autowired
    private CustomerAvatarService avatars;

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(UUID customerId) {
        User customer = requireCustomer(customerId);
        UserProfile profile = requireProfile(customerId);
        return CustomerProfileResponse.from(customer, profile);
    }

    @Transactional
    public CustomerProfileResponse updateProfile(UUID customerId, UpdateCustomerProfileRequest request) {
        // Khóa trước khi đọc trạng thái để không ghi đè cờ banned/role từ dữ liệu cũ.
        users.findByIdForUpdate(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User customer = requireCustomer(customerId);
        UserProfile profile = requireProfile(customerId);

        if (users.existsByPhoneAndIdNot(request.phone(), customerId)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        Instant now = clock.now();
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setPhone(request.phone());
        customer.setUpdatedAt(now);

        profile.setBusinessName(request.businessName());
        profile.setProvince(request.province());
        profile.setDistrict(null);
        profile.setWard(request.ward());
        profile.setDeliveryAddress(request.deliveryAddress());
        profile.setDeliveryNote(request.deliveryNote());
        profile.setInvoiceSameAsDelivery(request.invoiceSameAsDelivery());
        profile.setInvoicePurchaser(request.invoicePurchaser());
        profile.setInvoiceTaxCode(request.invoiceTaxCode());
        profile.setInvoiceEmail(request.invoiceEmail());
        profile.setInvoiceAddress(Boolean.TRUE.equals(request.invoiceSameAsDelivery())
                ? profile.getFullDeliveryAddress()
                : request.invoiceAddress());
        profile.setUpdatedAt(now);

        users.saveAndFlush(customer);
        profiles.saveAndFlush(profile);
        return CustomerProfileResponse.from(customer, profile);
    }

    public CustomerProfileResponse updateAvatar(UUID customerId, MultipartFile file) {
        requireCustomer(customerId);
        requireProfile(customerId);
        String avatarUrl = avatars.upload(file);
        return avatars.save(customerId, avatarUrl);
    }

    private User requireCustomer(UUID customerId) {
        User customer = accounts.requireActiveUser(customerId);
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return customer;
    }

    private UserProfile requireProfile(UUID customerId) {
        return profiles.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_PROFILE_NOT_FOUND));
    }
}
