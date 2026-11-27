package com.se203730.mvc.generateuibe.features.ai.service.orchestration;

import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.entity.UserSearchHistory;
import com.se203730.mvc.generateuibe.features.admin.enums.CustomerType;
import com.se203730.mvc.generateuibe.features.admin.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserSearchHistoryRepository;
import com.se203730.mvc.generateuibe.features.ai.client.BedrockClient;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput;
import com.se203730.mvc.generateuibe.features.ai.repository.AiModelRequestRepository;
import com.se203730.mvc.generateuibe.features.ai.service.context.AiPromptLibrary;
import com.se203730.mvc.generateuibe.features.ai.service.context.UiProfileService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShoppingContextTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID customerId = UUID.randomUUID();
    private final BedrockClient model = mock(BedrockClient.class);
    private final UiProfileService profiles = mock(UiProfileService.class);
    private final UserProfileRepository customers = mock(UserProfileRepository.class);
    private final UserSearchHistoryRepository searches = mock(UserSearchHistoryRepository.class);

    private JsonNode classify(Boolean consent) throws Exception {
        var service = new GenerativeUiService(model, new AiPromptLibrary(mapper, Set.of()), null, null, null,
                profiles, customers, searches, mock(AiModelRequestRepository.class), mapper);
        String latest = "Bây giờ tôi muốn hỏi về giao hàng";
        when(model.checkUserInput(latest)).thenReturn(new BedrockClient.GuardrailCheck(false, null));
        String classification = """
                {"scope":"SHOPPING","intents":["INQUIRE_POLICY"],"requests":[{"intent":"INQUIRE_POLICY","subject":"giao hàng",
                "subject_source":"message"}],"slots":{},"needs_clarification":false}
                """;
        when(model.converse(anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn(new BedrockClient.ModelReply(classification, 1, 1, 1),
                        new BedrockClient.ModelReply("OK", 1, 1, 1));
        var state = new RunAgentInput.AgentState("vi", null, null, null, null, "untrusted-browser", consent);
        service.run(new RunAgentInput("thread", "run", List.of(new RunAgentInput.ChatMessage("user", latest)),
                state, "chat"), customerId, event -> {});
        var inputs = ArgumentCaptor.forClass(String.class);
        verify(model).converse(anyString(), inputs.capture(), anyInt(), anyFloat());
        var input = mapper.readTree(inputs.getAllValues().getFirst());
        assertEquals(latest, input.path("latest_message").asString());
        return input.path("shopping_context");
    }

    @Test
    void ownRecentSearchIsAvailableEvenBeforeBehaviorThreshold() throws Exception {
        UserProfile customer = new UserProfile();
        customer.setCustomerType(CustomerType.BUSINESS_COOPERATIVE);
        customer.setDeliveryAddress("private address");
        UserSearchHistory search = new UserSearchHistory();
        search.setKeyword("cà phê hạt");
        when(profiles.profile(customerId, null)).thenReturn(UiProfileService.UiProfile.empty());
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
        when(searches.findTop5ByUserIdOrderBySearchedAtDescIdDesc(customerId)).thenReturn(List.of(search));
        var context = classify(true);
        assertEquals("BUSINESS_COOPERATIVE", context.path("customer_type").asString());
        assertEquals("cà phê hạt", context.path("recent_searches").get(0).asString());
        assertEquals(2, context.size());
    }

    @Test
    void optedOutSkipsPersonalLookups() throws Exception {
        assertEquals(0, classify(false).size());
        verifyNoInteractions(profiles, customers, searches);
    }

    @Test
    void missingConsentSkipsPersonalLookups() throws Exception {
        assertEquals(0, classify(null).size());
        verifyNoInteractions(profiles, customers, searches);
    }
}
