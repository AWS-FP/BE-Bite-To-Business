package com.se203730.mvc.generateuibe.features.customer.favorite.service;

import com.se203730.mvc.generateuibe.common.api.PageResponse;
import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import com.se203730.mvc.generateuibe.features.admin.service.AccountAccessService;
import com.se203730.mvc.generateuibe.features.customer.favorite.dto.response.FavoriteProductResponse;
import com.se203730.mvc.generateuibe.features.customer.favorite.dto.response.FavoriteStatusResponse;
import com.se203730.mvc.generateuibe.features.customer.favorite.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favorites;
    private final AccountAccessService accounts;

    @Transactional(readOnly = true)
    public PageResponse<FavoriteProductResponse> list(UUID customerId, int page, int size) {
        requireCustomer(customerId);
        if (page < 0 || size < 1 || size > 50) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return favorites.page(customerId, page, size);
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse status(UUID customerId, UUID productId) {
        requireCustomer(customerId);
        return new FavoriteStatusResponse(productId, favorites.isFavorite(customerId, productId));
    }

    @Transactional
    public FavoriteStatusResponse add(UUID customerId, UUID productId) {
        requireCustomer(customerId);
        if (!favorites.isPublished(productId)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        favorites.add(customerId, productId);
        return new FavoriteStatusResponse(productId, true);
    }

    @Transactional
    public FavoriteStatusResponse remove(UUID customerId, UUID productId) {
        requireCustomer(customerId);
        favorites.remove(customerId, productId);
        return new FavoriteStatusResponse(productId, false);
    }

    private void requireCustomer(UUID id) {
        if (accounts.requireActiveUser(id).getRole() != UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
