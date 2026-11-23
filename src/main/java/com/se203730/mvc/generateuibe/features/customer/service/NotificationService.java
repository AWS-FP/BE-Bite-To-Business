package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.dto.response.*;
import com.se203730.mvc.generateuibe.features.customer.dto.request.NotificationPreferencesRequest;
import com.se203730.mvc.generateuibe.features.customer.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    @Transactional(readOnly=true)
    public PageResponse<NotificationResponse> list(UUID id,int page,int size,boolean unreadOnly) {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return repository.list(id,page,size,unreadOnly);
    }
    @Transactional(readOnly=true)
    public NotificationUnreadResponse count(UUID id) {
        return new NotificationUnreadResponse(repository.unreadCount(id));
    }
    @Transactional(readOnly=true)
    public NotificationPreferencesResponse preferences(UUID id) {
        return repository.preferences(id);
    }
    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID id,NotificationPreferencesRequest request) {
        if (request.quoteApproval()==null || request.quoteExpiry()==null ||
            request.deliveryEta()==null || request.sourcingReply()==null)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return repository.updatePreferences(id,request);
    }
    @Transactional
    public NotificationMarkResponse markRead(UUID id,UUID notificationId) {
        if (!repository.exists(id,notificationId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return new NotificationMarkResponse(repository.markRead(id,notificationId));
    }
    @Transactional
    public NotificationMarkResponse markAll(UUID id) {
        return new NotificationMarkResponse(repository.markAllRead(id));
    }
}
