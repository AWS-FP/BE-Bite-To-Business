package com.se203730.mvc.generateuibe.features.user.repository;

import com.se203730.mvc.generateuibe.features.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Đọc/ghi tài khoản trong bảng users; tìm email không phân biệt chữ hoa, chữ thường để phục vụ đăng ký và đăng nhập.
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    // So sánh email bằng lower(...) để TEST@gmail.com và test@gmail.com được hiểu là cùng tài khoản.
    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);
}
