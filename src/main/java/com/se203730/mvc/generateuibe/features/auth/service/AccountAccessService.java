package com.se203730.mvc.generateuibe.features.auth.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.common.security.AccountAccessDeniedException;
import com.se203730.mvc.generateuibe.features.auth.entity.RegistrationRequest;
import com.se203730.mvc.generateuibe.features.user.entity.User;
import com.se203730.mvc.generateuibe.features.auth.enums.RegistrationStatus;
import com.se203730.mvc.generateuibe.features.user.enums.UserRole;
import com.se203730.mvc.generateuibe.features.auth.repository.RegistrationRequestRepository;
import com.se203730.mvc.generateuibe.features.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kiểm tra quyền và trạng thái tài khoản trong database; một JWT hợp lệ chưa có nghĩa tài khoản được phép mua hàng.
 */
@Service
@Transactional(readOnly = true)
public class AccountAccessService {
    @Autowired
    private UserRepository users;

    @Autowired
    private RegistrationRequestRepository registrations;

    /** Tài khoản phải tồn tại, không bị cấm và đã xác minh email; Customer còn phải được duyệt và kích hoạt. */
    public User requireActiveUser(UUID id) {
        User user = users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.isBanned()) throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        if (!user.isEmailVerified()) throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        if (user.getRole() == UserRole.CUSTOMER) {
            var registration = registrations.findByCustomerId(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED));
            if (registration.getStatus() != RegistrationStatus.APPROVED || registration.getActivatedAt() == null)
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }
        return user;
    }

    /** Kiểm tra tài khoản đang hoạt động và có đúng role ADMIN. */
    public void requireAdmin(UUID id) {
        if (requireActiveUser(id).getRole() != UserRole.ADMIN) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    /** Điều kiện dùng chung cho gửi OTP, xác minh OTP và tạo mật khẩu sau khi Admin duyệt. */
    public User requireApprovedCustomer(RegistrationRequest registration) {
        User user = users.findById(registration.getCustomerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getRole() != UserRole.CUSTOMER || registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVATED);
        }
        if (registration.getActivatedAt() != null) {
            throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_ACTIVATED);
        }

        return user;
    }

    /** Chuyển role trong database thành ROLE_... để Spring Security sử dụng; không lấy role do client tự khai. */
    public List<GrantedAuthority> authorities(Jwt jwt) {
        return users.findById(subject(jwt))
                .map(user -> List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .orElseGet(List::of);
    }

    /** Được SecurityConfig gọi để kiểm tra mỗi request nghiệp vụ hoặc request chỉ dành cho Admin. */
    public AuthorizationDecision authorize(Authentication authentication, boolean adminOnly) {
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated())
            return new AuthorizationDecision(false);
        try {
            User user = requireActiveUser(subject(token.getToken()));
            requirePasswordLogin(token.getToken());
            if (adminOnly && user.getRole() != UserRole.ADMIN) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            return new AuthorizationDecision(true);
        } catch (BusinessException ex) {
            if (ex.getErrorCode().getStatus().value() == 401)
                throw new InsufficientAuthenticationException(ex.getMessage());
            throw new AccountAccessDeniedException(ex.getErrorCode());
        }
    }

    /** Lấy UUID từ claim sub của JWT; từ chối giá trị không phải UUID chuẩn. */
    public static UUID subject(Jwt jwt) {
        try {
            UUID id = UUID.fromString(jwt.getSubject());
            if (!id.toString().equalsIgnoreCase(jwt.getSubject())) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadCredentialsException("Invalid token subject.");
        }
    }

    // Token OTP chỉ dùng tạo mật khẩu, không dùng làm phiên mua hàng kể cả sau kích hoạt.
    public static void requirePasswordLogin(Jwt jwt) {
        // amr ghi cách xác thực phiên. Phiên mua hàng cần phương thức password, không chỉ xác minh OTP.
        Object methods = jwt.getClaim("amr");
        if (methods instanceof List<?> list && list.stream()
                .anyMatch(value -> value instanceof Map<?, ?> map && "password".equals(map.get("method")))) return;
        throw new BusinessException(ErrorCode.PASSWORD_LOGIN_REQUIRED);
    }
}
