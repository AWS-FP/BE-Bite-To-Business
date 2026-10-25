package com.se203730.mvc.generateuibe.features.auth.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.auth.client.SupabaseAuthClient;
import com.se203730.mvc.generateuibe.features.auth.dto.request.LoginRequest;
import com.se203730.mvc.generateuibe.features.auth.dto.response.AuthResponse;
import com.se203730.mvc.generateuibe.features.user.dto.response.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Xử lý đăng nhập, làm mới phiên và đăng xuất; Supabase quản lý mật khẩu/token, backend kiểm tra thêm trạng thái nghiệp vụ.
 */
@Service
@Slf4j
public class AuthService {
    @Autowired
    private SupabaseAuthClient supabase;

    @Autowired
    private AccountAccessService accounts;

    @Autowired
    private JwtDecoder jwtDecoder;

    /** Gửi Gmail/password tới Supabase, sau đó kiểm tra tài khoản có đủ điều kiện dùng website. */
    public AuthResponse login(LoginRequest request) {
        return authenticatedSession(supabase.login(request.email(), request.password()));
    }

    /** Xin token mới và kiểm tra lại trạng thái tài khoản; refresh không bỏ qua bước kiểm tra bị cấm/kích hoạt. */
    public AuthResponse refresh(String refreshToken) {
        return authenticatedSession(supabase.refresh(refreshToken));
    }

    /** Yêu cầu Supabase thu hồi phiên tương ứng với access token. */
    public void logout(String accessToken) {
        supabase.logout(accessToken);
    }

    /** Xác minh token, đối chiếu UUID và kiểm tra trạng thái trong database trước khi trả phiên cho frontend. */
    private AuthResponse authenticatedSession(SupabaseAuthClient.Session session) {
        try {
            // Thiếu trường snake_case từ provider phải báo lỗi, không trả phiên có expiresIn = 0 cho FE.
            if (session.accessToken() == null || session.accessToken().isBlank()
                    || session.refreshToken() == null || session.refreshToken().isBlank()
                    || !"bearer".equalsIgnoreCase(session.tokenType()) || session.expiresIn() <= 0
                    || session.user() == null || session.user().id() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
            }
            // Không tin trực tiếp JSON trả về: kiểm tra chữ ký, issuer, audience và thời hạn JWT qua decoder.
            var jwt = jwtDecoder.decode(session.accessToken());
            if (!AccountAccessService.subject(jwt).equals(session.user().id()))
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            AccountAccessService.requirePasswordLogin(jwt);
            var user = accounts.requireActiveUser(session.user().id());
            return new AuthResponse(session.accessToken(), session.refreshToken(), "Bearer", session.expiresIn(),
                    UserResponse.from(user));
        // Nếu phiên mới không đạt kiểm tra nghiệp vụ, cố gắng thu hồi phiên đó tại Supabase.
        } catch (BusinessException | JwtException | AuthenticationException ex) {
            try { if (session.accessToken() != null) supabase.logout(session.accessToken()); }
            catch (BusinessException ignored) { log.warn("Could not revoke a rejected authentication session"); }
            if (ex instanceof BusinessException business) throw business;
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
