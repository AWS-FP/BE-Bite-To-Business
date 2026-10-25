package com.se203730.mvc.generateuibe.features.notification.service;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Chi render email va dung SMTP mock, khong gui thu that.
@ExtendWith(MockitoExtension.class)
class RegistrationMailServiceTest {
    @Mock JavaMailSender sender;
    @Spy TemplateEngine templateEngine = createTemplateEngine();
    @InjectMocks RegistrationMailService service;

    @Test
    void approvalEmailLinksToVerificationPageWithoutPuttingOtpInUrl() throws Exception {
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://shop.example.com/");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);

        service.sendOtp("customer@gmail.com", "012345", 10);

        verify(sender).send(message);
        String content = messageText(message.getContent());
        assertThat(content).contains("012345", "https://shop.example.com/verify-otp");
        assertThat(content).contains("href=\"https://shop.example.com/verify-otp\"");
        assertThat(content).doesNotContain("?otp=", "?token=", "http://localhost:5173");
    }

    private static TemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private String messageText(Object content) throws Exception {
        if (content instanceof String text) return text;
        StringBuilder text = new StringBuilder();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                text.append(messageText(multipart.getBodyPart(index).getContent()));
            }
        }
        return text.toString();
    }
}
