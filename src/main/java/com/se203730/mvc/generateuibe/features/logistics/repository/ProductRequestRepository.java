package com.se203730.mvc.generateuibe.features.logistics.repository;

import com.se203730.mvc.generateuibe.features.logistics.entity.ProductRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ProductRequestRepository extends JpaRepository<ProductRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductRequest r where r.id = :id")
    Optional<ProductRequest> findForUpdate(@Param("id") UUID id);
    Page<ProductRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<ProductRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
