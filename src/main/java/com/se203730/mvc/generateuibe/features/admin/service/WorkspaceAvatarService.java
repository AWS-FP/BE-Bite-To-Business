package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.util.DatabaseClock;
import com.se203730.mvc.generateuibe.features.admin.dto.response.UserResponse;
import com.se203730.mvc.generateuibe.features.admin.entity.User;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.repository.UserRepository;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerAvatarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class WorkspaceAvatarService {
    @Autowired
    private AccountAccessService accounts;

    @Autowired
    private CustomerAvatarService avatars;

    @Autowired
    private UserRepository users;

    @Autowired
    private DatabaseClock clock;

    @Transactional
    public UserResponse updateAvatar(UUID userId, MultipartFile file) {
        User user = accounts.requireActiveUser(userId);
        if (user.getRole() == UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        String avatarUrl = avatars.upload(file);
        user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        accounts.requireActiveUser(userId);
        if (user.getRole() == UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(clock.now());
        return UserResponse.from(users.saveAndFlush(user));
    }
}
