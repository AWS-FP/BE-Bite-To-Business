package com.se203730.mvc.generateuibe.features.customer.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NewsletterMailService {
    private static final Logger log = LoggerFactory.getLogger(NewsletterMailService.class);
    private final JavaMailSender sender;
    private final TemplateEngine templates;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.support-email:}")
    private String supportEmail;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String websiteUrl;

    public void sendWelcome(String email) {
        if (from == null || from.isBlank()) {
            log.warn("Newsletter welcome mail was not sent: sender address is missing");
            return;
        }
        try {
            Context context = new Context(Locale.forLanguageTag("vi"));
            prepareBrandContext(context);
            String html = templates.process("email/newsletter-welcome", context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Bite to Business");
            helper.setTo(email);
            helper.setSubject("Bite to Business - Đăng ký nhận tin thành công");
            helper.setText("Cảm ơn bạn đã đăng ký nhận tin từ Bite to Business.\n" + websiteUrl, html);
            sender.send(message);
        } catch (MessagingException | MailException | org.thymeleaf.exceptions.TemplateEngineException
                | UnsupportedEncodingException ex) {
            log.warn("Could not send newsletter welcome mail to a subscribed address");
        }
    }

    /** Used by a rate-limited, opt-in-only campaign sender. */
    public boolean sendPromotion(String email, String title, String content, String imageUrl) {
        if (from == null || from.isBlank()) return false;
        try {
            Context context = new Context(Locale.forLanguageTag("vi"));
            prepareBrandContext(context);
            context.setVariable("title", title);
            context.setVariable("content", content);
            context.setVariable("imageUrl", imageUrl);
            String html = templates.process("email/newsletter-promotion", context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Bite to Business");
            helper.setTo(email);
            helper.setSubject("Bite to Business - " + title);
            helper.setText("Bite to Business: " + title + "\n" + (content == null ? "" : content)
                    + "\n" + websiteUrl, html);
            sender.send(message);
            return true;
        } catch (MessagingException | MailException | org.thymeleaf.exceptions.TemplateEngineException
                | UnsupportedEncodingException ex) {
            log.warn("Could not send a newsletter promotion message");
            return false;
        }
    }

    private void prepareBrandContext(Context context) {
        context.setVariable("websiteUrl", websiteUrl.strip().replaceAll("/+$", ""));
        context.setVariable("supportEmail", supportEmail);
    }
}
