package com.se203730.mvc.generateuibe.features.notification.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Gửi kết quả đăng ký và OTP qua Gmail SMTP; không tự tạo hoặc xác minh OTP.
 */
@Service
public class RegistrationMailService {
    @Autowired
    private JavaMailSender sender;

    @Autowired
    private TemplateEngine templateEngine;

    // Giá trị chuỗi lấy từ cấu hình nên dùng @Value.
    @Value("${spring.mail.username:}")
    private String from;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public void sendOtp(String email, String otp, long minutes) {
        if (from.isBlank())
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);

        // Thay các biến trong template bằng OTP thật và thời hạn do luồng đăng ký cung cấp.
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("otp", otp);
        context.setVariable("expiresInMinutes", minutes);
        String verificationUrl = frontendBaseUrl.strip().replaceAll("/+$", "") + "/verify-otp";
        context.setVariable("verificationUrl", verificationUrl);

        // Email có cả bản text để ứng dụng đọc mail không hỗ trợ HTML vẫn xem được mã.
        String plainText = "Bite to Business\n\nHồ sơ của bạn đã được duyệt. Mã OTP: " + otp
                + "\nMã có hiệu lực " + minutes
                + " phút. Không chia sẻ mã này. Sau khi xác minh, hãy tự tạo mật khẩu."
                + "\nMở trang xác minh: " + verificationUrl
                + "\n\nYour registration has been approved. Verification code: " + otp
                + "\nValid for " + minutes
                + " minutes. Do not share this code. Set your password after verification."
                + "\nVerify your account: " + verificationUrl;

        try {
            String html = templateEngine.process("email/registration-approved-otp", context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Bite to Business - Xác minh tài khoản / Verify your account");
            helper.setText(plainText, html);
            sender.send(message);
        } catch (MessagingException | MailException | TemplateEngineException ex) {
            // Không đưa nội dung SMTP/template hoặc OTP vào thông báo lỗi trả cho frontend.
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    public void sendRejection(String email, String reason) {
        if (from.isBlank())
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);

        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("reason", reason);
        context.setVariable("supportEmail", from);
        String plainText = "Bite to Business\n\nHồ sơ đăng ký của bạn chưa được chấp thuận."
                + "\nLý do từ chối: " + reason
                + "\nTài khoản chưa được kích hoạt. Phản hồi email này để được hướng dẫn bổ sung thông tin."
                + "\n\nYour registration was not approved.\nReason: " + reason
                + "\nYour account has not been activated. Reply to this email for guidance."
                + "\n\nLiên hệ hỗ trợ / Contact support: " + from;

        try {
            String html = templateEngine.process("email/registration-rejected", context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Bite to Business - Kết quả đăng ký / Registration result");
            helper.setText(plainText, html);
            sender.send(message);
        } catch (MessagingException | MailException | TemplateEngineException ex) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }
}
