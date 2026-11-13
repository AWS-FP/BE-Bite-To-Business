package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.QuotePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuotePackageRepository extends JpaRepository<QuotePackage, UUID> {
    List<QuotePackage> findAllByQuoteIdAndCancelledAtIsNullOrderByCreatedAtAsc(UUID quoteId);
    Optional<QuotePackage> findByIdAndQuoteIdAndCancelledAtIsNull(UUID id, UUID quoteId);
}
