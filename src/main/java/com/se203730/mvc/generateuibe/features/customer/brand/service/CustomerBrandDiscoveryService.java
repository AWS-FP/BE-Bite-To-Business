
package com.se203730.mvc.generateuibe.features.customer.brand.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.brand.dto.response.CustomerBrandDirectoryItemResponse;
import com.se203730.mvc.generateuibe.features.customer.brand.repository.CustomerBrandDiscoveryRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.BrandResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerBrandDiscoveryService {

    private final BrandRepository brands;
    private final CustomerBrandDiscoveryRepository discovery;

    public List<CustomerBrandDirectoryItemResponse> directory() {
        Map<UUID, LinkedHashSet<UUID>> categoriesByBrand = new HashMap<>();
        Map<UUID, Long> countsByBrand = new HashMap<>();

        for (Object[] row : discovery.findPublishedCountsByBrandAndCategory()) {
            UUID brandId = (UUID) row[0];
            UUID categoryId = (UUID) row[1];
            long count = ((Number) row[2]).longValue();

            categoriesByBrand
                .computeIfAbsent(brandId, key -> new LinkedHashSet<>())
                .add(categoryId);

            countsByBrand.merge(brandId, count, Long::sum);
        }

        return brands.findAllSorted()
            .stream()
            .map(brand -> CustomerBrandDirectoryItemResponse.from(
                brand,
                countsByBrand.getOrDefault(brand.getId(), 0L),
                new ArrayList<>(
                    categoriesByBrand.getOrDefault(
                        brand.getId(),
                        new LinkedHashSet<>()
                    )
                )
            ))
            .toList();
    }

    public List<BrandResponse> related(UUID brandId, int limit) {
        if (!brands.existsById(brandId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        int safeLimit = Math.max(1, Math.min(limit, 24));

        return discovery
            .findRelatedPublishedBrands(
                brandId,
                PageRequest.of(0, safeLimit)
            )
            .stream()
            .map(BrandResponse::from)
            .toList();
    }
}
