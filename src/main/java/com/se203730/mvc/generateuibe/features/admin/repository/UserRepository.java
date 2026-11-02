package com.se203730.mvc.generateuibe.features.admin.repository;

import com.se203730.mvc.generateuibe.features.admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import com.se203730.mvc.generateuibe.features.admin.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Đọc/ghi tài khoản trong bảng users; tìm email không phân biệt chữ hoa, chữ thường để phục vụ đăng ký và đăng nhập.
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("""
            select u from User u where u.role in :roles and (
                lower(concat(u.lastName, ' ', u.firstName)) like :keyword escape '!'
                or lower(concat(u.firstName, ' ', u.lastName)) like :keyword escape '!'
                or lower(u.email) like :keyword escape '!'
                or u.phone like :keyword escape '!'
                or u.phone like :phone escape '!'
            )
            """)
    Page<User> findStaff(
            @Param("roles")
            List<UserRole> roles,
            @Param("keyword")
            String keyword,
            @Param("phone")
            String phone,
            Pageable pageable);

    // So sánh email bằng lower(...) để TEST@gmail.com và test@gmail.com được hiểu là cùng tài khoản.
    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email")
    String email);

    // Số đã được chuẩn hóa về +84 trước khi truy vấn.
    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id")
    UUID id);

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email")
    String email);
}
