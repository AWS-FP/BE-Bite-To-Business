package com.se203730.mvc.generateuibe.features.ai.service.context;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Exact short social messages; mixed product questions continue through the shopping pipeline. */
public final class AssistantConversation {
    private static final Set<String> GREETINGS = Set.of(
            "chao", "chao ban", "xin chao", "xin chao ban", "hi", "hello", "hey", "alo",
            "chao shop", "chao em", "xin chao shop", "hello shop", "hi shop", "co ai khong", "ban oi", "shop oi");
    private static final Set<String> THANKS = Set.of(
            "cam on", "cam on ban", "cam on nhe", "cam on nhieu", "cam on ban nhe", "thanks",
            "thank you", "thank you very much", "ok cam on");
    private static final Set<String> GOODBYES = Set.of("tam biet", "bye", "goodbye", "hen gap lai");
    private static final Set<String> HELP = Set.of(
            "ban la ai", "ban la ai vay", "ban lam duoc gi", "ban co the giup gi", "giup minh voi",
            "giup tui voi", "help", "what can you do", "who are you");

    private AssistantConversation() {}

    public static String reply(String message, String locale) {
        if (message == null || message.isBlank()) return null;
        String text = Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
        boolean en = "en".equals(locale);
        if (GREETINGS.contains(text))
            return en?"Hi! I'm the Bite to Business shopping assistant. What would you like to find today? I can help with products, prices, availability and comparisons.":"Chào bạn! Mình là trợ lý mua hàng của Bite to Business. Bạn đang cần tìm sản phẩm gì? Mình có thể giúp xem giá, tồn kho và so sánh sản phẩm nhé.";
        if (THANKS.contains(text))
            return en?"You're welcome! Let me know if you'd like to find or compare anything else.":"Không có gì nha! Nếu cần tìm hay so sánh thêm sản phẩm, bạn cứ nhắn mình nhé.";
        if (GOODBYES.contains(text))
            return en?"Goodbye! I'm here whenever you need help shopping.":"Tạm biệt bạn! Khi nào cần tìm sản phẩm, mình luôn sẵn sàng hỗ trợ nhé.";
        if (HELP.contains(text))
            return en?"I can help you find products, check prices and stock, compare product details, and understand shopping on Bite to Business. What do you need?":"Mình có thể giúp bạn tìm sản phẩm, xem giá và tồn kho, so sánh thông tin sản phẩm hoặc giải đáp cách mua hàng trên Bite to Business. Bạn cần mình hỗ trợ việc gì?";
        return null;
    }
}
