package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.features.ecommerce.entity.ProductVat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductVatRepository extends JpaRepository<ProductVat, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductVat p where p.id = :id")
    Optional<ProductVat> findForUpdate(@Param("id") UUID id);
}
