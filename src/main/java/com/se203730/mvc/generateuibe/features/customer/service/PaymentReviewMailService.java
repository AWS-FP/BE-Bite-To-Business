package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.features.customer.repository.SepayWebhookEventRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

/** Email cho khách khi khoản chuyển khoản không tự khớp đơn, và khi Admin đã hoàn tiền / khôi phục đơn. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReviewMailService {
    private static final Locale VI = Locale.forLanguageTag("vi");

    private final JavaMailSender sender;
    private final TemplateEngine templates;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.support-email:}")
    private String supportEmail;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String websiteUrl;

    /** "Chúng tôi đã nhận tiền nhưng chưa ghi nhận được cho đơn": gửi một lần cho mỗi khoản. */
    public boolean sendReceived(SepayWebhookEventRepository.NoticeRow row) {
        String reason = switch (row.getReviewReason() == null ? "" : row.getReviewReason()) {
            case "RESERVATION_EXPIRED" -> "Đơn hàng đã hết thời gian giữ hàng trước khi khoản thanh toán tới.";
            case "PAYMENT_STATE_OR_AMOUNT_MISMATCH" ->
                    "Số tiền không khớp với đơn, hoặc đơn đã được thanh toán / đã huỷ trước đó.";
            default -> "Hệ thống không thể tự ghi nhận khoản thanh toán này cho đơn.";
        };
        Context context = context(row);
        context.setVariable("reason", reason);
        return send(row, "email/payment-review-received",
                "Bite to Business - Đã nhận thanh toán cho đơn " + row.getOrderNumber(),
                "Chúng tôi đã nhận " + money(row.getAmount()) + " cho đơn " + row.getOrderNumber()
                        + " nhưng chưa thể ghi nhận tự động. " + reason
                        + " Chúng tôi sẽ hoàn tiền hoặc khôi phục đơn và báo lại cho bạn.",
                context);
    }

    /** Kết quả xử lý: REFUNDED (kèm mã giao dịch hoàn) hoặc RESTORED (đơn đã được ghi nhận thanh toán). */
    public boolean sendResolved(SepayWebhookEventRepository.NoticeRow row) {
        boolean refunded = "REFUNDED".equals(row.getResolution());
        Context context = context(row);
        context.setVariable("refunded", refunded);
        context.setVariable("refundReference", row.getRefundReference());
        String subject = refunded
                ? "Bite to Business - Đã hoàn tiền cho đơn " + row.getOrderNumber()
                : "Bite to Business - Đơn " + row.getOrderNumber() + " đã được ghi nhận thanh toán";
        String text = refunded
                ? "Chúng tôi đã hoàn " + money(row.getAmount()) + " cho đơn " + row.getOrderNumber()
                        + ". Mã giao dịch hoàn: " + row.getRefundReference() + "."
                : "Đơn " + row.getOrderNumber() + " đã được khôi phục và ghi nhận thanh toán "
                        + money(row.getAmount()) + ".";
        return send(row, "email/payment-review-resolved", subject, text, context);
    }

    private Context context(SepayWebhookEventRepository.NoticeRow row) {
        Context context = new Context(VI);
        context.setVariable("websiteUrl", websiteUrl.strip().replaceAll("/+$", ""));
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("contactName", row.getContactName());
        context.setVariable("orderNumber", row.getOrderNumber());
        context.setVariable("amount", money(row.getAmount()));
        return context;
    }

    private boolean send(SepayWebhookEventRepository.NoticeRow row, String template, String subject, String text,
                         Context context) {
        if (from == null || from.isBlank() || row.getContactEmail() == null || row.getContactEmail().isBlank()) {
            log.warn("Payment review mail was not sent: sender or recipient address is missing");
            return false;
        }
        try {
            String html = templates.process(template, context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Bite to Business");
            helper.setTo(row.getContactEmail());
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
            return true;
        } catch (MessagingException | MailException | org.thymeleaf.exceptions.TemplateEngineException
                | UnsupportedEncodingException ex) {
            log.warn("Could not send payment review mail for SePay transaction {}", row.getTransactionId());
            return false;
        }
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(VI).format(amount) + " đ";
    }
}
