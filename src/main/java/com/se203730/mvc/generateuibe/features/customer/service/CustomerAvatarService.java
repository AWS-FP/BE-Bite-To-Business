package com.se203730.mvc.generateuibe.features.customer.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerProfileResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerAvatarService {
    @Value("${app.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:}")
    private String apiSecret;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private AccountAccessService accounts;

    @Autowired
    private DatabaseClock clock;

    public String upload(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }
        try {
            byte[] bytes = file.getBytes();
            boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255
                    && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            boolean png = bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80
                    && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13
                    && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            if (!jpeg && !png) throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", "avatars", "resource_type", "image"));
            Object url = result.get("secure_url");
            if (!(url instanceof String) || !((String) url).startsWith("https://")) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            return (String) url;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception);
        }
    }

    @Transactional
    public CustomerProfileResponse save(UUID customerId, String avatarUrl) {
        users.findByIdForUpdate(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User customer = accounts.requireActiveUser(customerId);
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        UserProfile profile = profiles.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_PROFILE_NOT_FOUND));
        customer.setAvatarUrl(avatarUrl);
        customer.setUpdatedAt(clock.now());
        users.saveAndFlush(customer);
        return CustomerProfileResponse.from(customer, profile);
    }
}
