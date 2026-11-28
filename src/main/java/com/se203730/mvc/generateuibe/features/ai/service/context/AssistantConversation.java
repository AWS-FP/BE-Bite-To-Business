package com.se203730.mvc.generateuibe.features.ai.service.context;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Exact short social messages; mixed product questions continue through the shopping pipeline. */
public final class AssistantConversation {
    private AssistantConversation() {}
    public static String reply(String message,String locale) {
        String text=Normalizer.normalize(message.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").replace('đ','d').replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
        boolean en="en".equals(locale);
        if(Set.of("chao","chao ban","xin chao","xin chao ban","hi","hello","hey","alo","chao shop","chao em").contains(text))
            return en?"Hi! I'm the Bite to Business shopping assistant. What would you like to find today? I can help with products, prices, availability and comparisons.":"Chào bạn! Mình là trợ lý mua hàng của Bite to Business. Bạn đang cần tìm sản phẩm gì? Mình có thể giúp xem giá, tồn kho và so sánh sản phẩm nhé.";
        if(Set.of("cam on","cam on ban","cam on nhe","thanks","thank you","thank you very much","ok cam on").contains(text))
            return en?"You're welcome! Let me know if you'd like to find or compare anything else.":"Không có gì nha! Nếu cần tìm hay so sánh thêm sản phẩm, bạn cứ nhắn mình nhé.";
        if(Set.of("tam biet","bye","goodbye","hen gap lai").contains(text))
            return en?"Goodbye! I'm here whenever you need help shopping.":"Tạm biệt bạn! Khi nào cần tìm sản phẩm, mình luôn sẵn sàng hỗ trợ nhé.";
        if(Set.of("ban la ai","ban lam duoc gi","ban co the giup gi","giup minh voi","giup tui voi","help","what can you do","who are you").contains(text))
            return en?"I can help you find products, check prices and stock, compare product details, and understand shopping on Bite to Business. What do you need?":"Mình có thể giúp bạn tìm sản phẩm, xem giá và tồn kho, so sánh thông tin sản phẩm hoặc giải đáp cách mua hàng trên Bite to Business. Bạn cần mình hỗ trợ việc gì?";
        return null;
    }
}
