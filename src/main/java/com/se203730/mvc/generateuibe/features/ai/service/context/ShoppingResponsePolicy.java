package com.se203730.mvc.generateuibe.features.ai.service.context;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared guard for customer-visible text; structured A2UI events are validated separately. */
public final class ShoppingResponsePolicy {
    private static final Pattern STRUCTURED_TEXT = Pattern.compile(
            "```|[\\{\\[]\\s*\\\"[^\\\"\\r\\n]+\\\"\\s*:|<\\s*/?\\s*(script|html|iframe|style)\\b",
            Pattern.CASE_INSENSITIVE);

    private ShoppingResponsePolicy() {}

    public static String unknown(String locale) {
        return "en".equals(locale) ? "I don't know." : "Tôi không biết.";
    }

    public static boolean containsCode(String text) {
        return text != null && STRUCTURED_TEXT.matcher(text).find();
    }

    /** Fast rejection of explicit non-shopping requests, before guardrail or model calls. */
    public static boolean outsideShopping(String text) {
        if (containsCode(text)) return true;
        String value = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replaceAll("[^a-z0-9]+", " ").trim();
        boolean shoppingDate = value.matches(".*\\b(san pham|han su dung|het han|ngay san xuat|giao hang|don hang|expiry|expiration|delivery|order)\\b.*");
        return value.matches(".*\\b(tao|ve|sinh|generate|create|draw)(?: (?:mot|1|buc|tam|giup|cho|toi|tui)){0,4} (anh|hinh|image|picture|poster|banner|logo|video)\\b.*")
                || value.matches(".*\\b(web|website|trang web|he thong|ung dung)\\b.*\\b(mau|color|toc do|phan hoi|bao mat|kien truc|cau hinh|server|may chu|cong nghe)\\b.*")
                || value.matches(".*\\b(mau|color|toc do|phan hoi|bao mat|kien truc|cau hinh|cong nghe)\\b.*\\b(web|website|trang web|he thong|ung dung)\\b.*")
                || !shoppingDate && (value.matches(".*\\b(hom nay|bay gio|today|current)\\b.*\\b(thu may|ngay may|may gio|ngay gi|weekday|date|time)\\b.*")
                    || value.matches(".*\\b(what day|what time)\\b.*"))
                || value.matches(".*\\b(thoi tiet|weather|chinh tri|politics|tu vi|horoscope|quang cao|advertisement|marketing|prompt|system prompt|api key|access key|credentials|secret|token|mat khau he thong|sql|json|html|css|javascript)\\b.*")
                || value.matches(".*\\b(bo qua|ignore|override)\\b.*\\b(quy tac|chi dan|rules|instructions)\\b.*")
                || value.matches(".*\\b(thuoc|nhuan trang|laxative|dieu tri|chua benh|chan doan|di ia|treat disease|diagnose)\\b.*")
                || value.matches(".*\\b(khach hang khac|nguoi khac|tai khoan khac|another customer|other customers|someone else)\\b.*");
    }
}
