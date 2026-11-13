
package com.se203730.mvc.generateuibe.features.ecommerce.catalog.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.BrandProductResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.dto.response.BrandResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.entity.Brand;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.BrandRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private static final String PUBLISHED = "PUBLISHED";

    private final BrandRepository brands;
    private final ProductRepository products;

    public List<BrandResponse> getAll() {
        return brands.findAllSorted()
            .stream()
            .map(BrandResponse::from)
            .toList();
    }

    public BrandResponse getById(UUID brandId) {
        return BrandResponse.from(requireBrand(brandId));
    }

    public List<BrandProductResponse> getProducts(UUID brandId) {
        requireBrand(brandId);

        return products
            .findByBrandIdAndStatusOrderByNameViAscIdAsc(
                brandId,
                PUBLISHED
            )
            .stream()
            .map(BrandProductResponse::from)
            .toList();
    }

    private Brand requireBrand(UUID brandId) {
        return brands.findById(brandId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
            );
    }
}
