package com.se203730.mvc.generateuibe.features.auth.dto.response;

/**
 * Kết quả xác minh OTP và access token để tạo mật khẩu; không trả refresh token của phiên OTP.
 */
public record OtpVerificationResponse(String accessToken, String tokenType, long expiresIn,
                                      boolean passwordSetupRequired) {
    @Override public String toString() { return "OtpVerificationResponse[redacted]"; }
}
