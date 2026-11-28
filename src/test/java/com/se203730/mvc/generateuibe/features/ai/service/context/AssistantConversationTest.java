package com.se203730.mvc.generateuibe.features.ai.service.context;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AssistantConversationTest {
 @Test void respondsToVietnameseAndEnglishGreetings(){assertTrue(AssistantConversation.reply("Chào bạn!","vi").startsWith("Chào bạn!"));assertTrue(AssistantConversation.reply("Hello","en").startsWith("Hi!"));}
 @Test void preservesMixedShoppingQuestions(){assertNull(AssistantConversation.reply("Chào bạn, tìm thịt bò cho mình","vi"));assertNull(AssistantConversation.reply("thank you, compare beef and pork","en"));}
 @Test void thanksAndGoodbyeStayConversational(){assertTrue(AssistantConversation.reply("Cảm ơn bạn","vi").contains("Không có gì"));assertNotNull(AssistantConversation.reply("Tạm biệt","vi"));}
}
