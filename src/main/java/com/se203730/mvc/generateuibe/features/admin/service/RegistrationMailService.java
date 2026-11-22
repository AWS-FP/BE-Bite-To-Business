package com.se203730.mvc.generateuibe.features.admin.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Gửi kết quả đăng ký và OTP qua Gmail SMTP; không tự tạo hoặc xác minh OTP.
 */
@Service
@Slf4j
public class RegistrationMailService {
    private final ClassPathResource logo = new ClassPathResource("email/images/brand-logo.png");
    private final ClassPathResource produce = new ClassPathResource("email/images/fresh-produce.png");
    private final ClassPathResource seafood = new ClassPathResource("email/images/fresh-seafood.png");

    @Autowired
    private JavaMailSender sender;

    @Autowired
    private TemplateEngine templateEngine;

    // Giá trị chuỗi lấy từ cấu hình nên dùng @Value.
    @Value("${spring.mail.username:}")
    private String from;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public void sendOtp(String email, String otp, long minutes) {
        String verificationUrl = frontendUrl("/verify-otp");
        // Email có cả bản text để ứng dụng đọc mail không hỗ trợ HTML vẫn xem được mã.
        String plainText = "Bite to Business\n\nMã xác minh: " + otp
                + "\nMã có hiệu lực " + minutes
                + " phút. Không chia sẻ mã này. Sau khi xác minh, hãy tự tạo mật khẩu."
                + "\nMở trang xác minh: " + verificationUrl;
        sendOtpMail(email, otp, minutes, verificationUrl, "email/registration-approved-otp",
                "Bite to Business - Xác minh tài khoản", plainText);
    }

    /** Gửi mã đặt lại mật khẩu; link chỉ mở trang nhập mã, không chứa OTP hoặc token. */
    public void sendPasswordResetOtp(String email, String otp, long minutes) {
        String resetUrl = frontendUrl("/forgot-password");
        String plainText = "Bite to Business\n\nMã đặt lại mật khẩu: " + otp
                + "\nMã có hiệu lực " + minutes
                + " phút. Không chia sẻ mã này. Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email."
                + "\nMở trang đặt lại mật khẩu: " + resetUrl;
        sendOtpMail(email, otp, minutes, resetUrl, "email/password-reset-otp",
                "Bite to Business - Đặt lại mật khẩu", plainText);
    }

    private String frontendUrl(String path) {
        return frontendBaseUrl.strip().replaceAll("/+$", "") + path;
    }

    private void sendOtpMail(String email, String otp, long minutes, String actionUrl, String template,
            String subject, String plainText) {
        if (from.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }

        // Thay các biến trong template bằng OTP thật và thời hạn do luồng gọi cung cấp.
        Context context = new Context(Locale.forLanguageTag("vi"));
        prepareBrandContext(context);
        context.setVariable("otp", otp);
        context.setVariable("expiresInMinutes", minutes);
        context.setVariable("verificationUrl", actionUrl);

        try {
            String html = templateEngine.process(template, context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Bite to Business");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            addBrandImages(helper);
            sender.send(message);
        } catch (MailSendException ex) {
            Throwable cause = ex;
            if (!ex.getFailedMessages().isEmpty()) {
                cause = ex.getFailedMessages().values().iterator().next();
            }
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            log.warn("OTP email failed: errorType={}, causeType={}", ex.getClass().getSimpleName(), cause.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        } catch (MessagingException | MailException | TemplateEngineException
                | UnsupportedEncodingException ex) {
            // Không đưa nội dung SMTP/template hoặc OTP vào thông báo lỗi trả cho frontend.
            Throwable cause = ex;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            log.warn("OTP email failed: errorType={}, causeType={}", ex.getClass().getSimpleName(), cause.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    public void sendRejection(String email, String reason) {
        if (from.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }

        Context context = new Context(Locale.forLanguageTag("vi"));
        prepareBrandContext(context);
        context.setVariable("reason", reason);
        String plainText = "Bite to Business\n\nHồ sơ đăng ký của bạn chưa được chấp thuận."
                + "\nLý do từ chối: " + reason
                + "\nLiên hệ hỗ trợ để được hướng dẫn: " + from;

        try {
            String html = templateEngine.process("email/registration-rejected", context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Bite to Business");
            helper.setTo(email);
            helper.setSubject("Bite to Business - Kết quả đăng ký");
            helper.setText(plainText, html);
            addBrandImages(helper);
            sender.send(message);
        } catch (MessagingException | MailException | TemplateEngineException
                | UnsupportedEncodingException ex) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    private void prepareBrandContext(Context context) {
        context.setVariable("websiteUrl", frontendBaseUrl.strip().replaceAll("/+$", ""));
        context.setVariable("supportEmail", from);
        context.setVariable("showBrandPhotos", produce.exists() && seafood.exists());
    }

    private void addBrandImages(MimeMessageHelper helper) throws MessagingException {
        helper.addInline("btb-logo", logo, "image/png");
        if (produce.exists() && seafood.exists()) {
            helper.addInline("btb-produce", produce, "image/png");
            helper.addInline("btb-seafood", seafood, "image/png");
        }
    }
}
