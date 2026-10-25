package com.se203730.mvc.generateuibe.features.admin.repository;

import com.se203730.mvc.generateuibe.features.admin.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.admin.enums.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập bảng yêu cầu đăng ký bằng Spring Data JPA; các truy vấn khóa ghi được dùng trong transaction để tránh xử lý đồng thời.
 */
public interface RegistrationRequestRepository
        extends
            JpaRepository<RegistrationRequest, UUID>,
            JpaSpecificationExecutor<RegistrationRequest> {
    Optional<RegistrationRequest> findByCustomerId(UUID customerId);
    Page<RegistrationRequest> findByStatus(RegistrationStatus status, Pageable pageable);
    long countByStatus(RegistrationStatus status);

    // Khóa ghi dòng yêu cầu theo ID; cần gọi bên trong transaction để tránh hai lần duyệt đồng thời.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.id = :id")
    Optional<RegistrationRequest> findForUpdate(@Param("id")
    UUID id);

    // Khóa yêu cầu theo Customer khi xác minh OTP hoặc tạo mật khẩu để tránh hai request cùng cập nhật.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.customerId = :customerId")
    Optional<RegistrationRequest> findByCustomerIdForUpdate(@Param("customerId")
    UUID customerId);
}
