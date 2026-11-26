package com.se203730.mvc.generateuibe.features.ai.service.orchestration;

import com.se203730.mvc.generateuibe.features.admin.entity.UserProfile;
import com.se203730.mvc.generateuibe.features.admin.entity.UserSearchHistory;
import com.se203730.mvc.generateuibe.features.admin.repository.UserProfileRepository;
import com.se203730.mvc.generateuibe.features.admin.repository.UserSearchHistoryRepository;
import com.se203730.mvc.generateuibe.features.ai.client.BedrockClient;
import com.se203730.mvc.generateuibe.features.ai.client.BedrockClient.ModelReply;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput.AgentState;
import com.se203730.mvc.generateuibe.features.ai.dto.RunAgentInput.ChatMessage;
import com.se203730.mvc.generateuibe.features.ai.repository.AiModelRequestRepository;
import com.se203730.mvc.generateuibe.features.ai.service.a2ui.A2uiValidator;
import com.se203730.mvc.generateuibe.features.ai.service.context.AiPromptLibrary;
import com.se203730.mvc.generateuibe.features.ai.service.context.UiProfileService;
import com.se203730.mvc.generateuibe.features.ai.service.context.UiProfileService.UiProfile;
import com.se203730.mvc.generateuibe.features.ai.service.tools.PageDataTool;
import com.se203730.mvc.generateuibe.features.ecommerce.service.ProductKnowledgeService.AttributeQuery;
import com.se203730.mvc.generateuibe.features.ai.service.tools.ProductSearchTool;
import com.se203730.mvc.generateuibe.features.ai.service.tools.ProductSearchTool.SearchResult;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pipeline Generate UI cho Guest/Customer: nhận diện ý định → gọi tool catalog/pricing → model sinh bố cục A2UI
 * → validate theo catalog (thử lại 1 lần, sau đó dùng bố cục mặc định) → phát AG-UI events.
 * Ý định ngoài phạm vi tạo UI được trả lời bằng văn bản dựa trên business context.
 */
@Service
@Slf4j
public class GenerativeUiService {
    /** Nơi nhận AG-UI event; AgentService ghi ra SSE. */
    @FunctionalInterface
    public interface EventSink {
        void send(Map<String, Object> event) throws IOException;
    }

    record Intent(List<String> intents, JsonNode slots, JsonNode requests, boolean needsClarification, String question) {
    }

    private enum Route {
        GENERATE_UI, NAVIGATE, ANSWER, UNSUPPORTED
    }

    private static final Set<String> UI_INTENTS = Set.of("SEARCH_PRODUCT", "FILTER_PRODUCTS", "ASK_PRODUCT_INFO",
            "CHECK_TIER_PRICE",
            "CHECK_STOCK_AVAILABILITY", "CHECK_MOQ_AND_SPEC", "COMPARE_PRODUCTS", "CHANGE_UI_PREFERENCE",
            "ADD_TO_CART", "RECOMMEND_SUBSTITUTE", "RECOMMEND_BUNDLE");
    private static final Map<String, String> NAVIGATE_PAGES = Map.ofEntries(
            Map.entry("VIEW_CART", "cart"), Map.entry("MODIFY_CART", "cart"),
            Map.entry("UPDATE_CART_QUANTITY", "cart"), Map.entry("CALCULATE_CART_TOTAL", "cart"),
            Map.entry("APPLY_COUPON", "cart"), Map.entry("CREATE_RFQ", "cart"), Map.entry("MANAGE_PACKAGE", "cart"),
            Map.entry("PREVIEW_PACKAGE_SPLIT", "cart"), Map.entry("INITIATE_PAYMENT", "orders"),
            Map.entry("TRACK_ORDER", "orders"), Map.entry("TRACK_ORDER_PACKAGES", "orders"),
            Map.entry("SEARCH_ORDER_HISTORY", "orders"), Map.entry("REQUEST_EINVOICE", "orders"),
            Map.entry("REPORT_DELIVERY_ISSUE", "orders"), Map.entry("UPDATE_BILLING_INFO", "profile"),
            Map.entry("MANAGE_BRANCH_ADDRESS", "profile"), Map.entry("UPDATE_DELIVERY_NOTES", "profile"));
    private static final Set<String> UNSUPPORTED_INTENTS = Set.of("CANCEL_ORDER", "CANCEL_RFQ",
            "UPDATE_DELIVERY_SLOT", "UPDATE_ORDER_ADDRESS", "CHECK_CREDIT_LIMIT", "CHECKOUT_WITH_CREDIT",
            "CHECK_LOYALTY_TIER", "SETUP_RECURRING_ORDER", "REORDER_PAST_ORDER", "REORDER_WITH_MODIFICATION");
    private static final int HISTORY_TURNS = 6;
    private static final int UI_EXAMPLES = 4;

    private final BedrockClient bedrock;
    private final AiPromptLibrary library;
    private final A2uiValidator validator;
    private final ProductSearchTool products;
    private final AiModelRequestRepository modelRequests;
    private final ObjectMapper mapper;

    private final PageDataTool pageData;
    private final UiProfileService profiles;
    private final UserProfileRepository customers;
    private final UserSearchHistoryRepository searches;

    public GenerativeUiService(BedrockClient bedrock, AiPromptLibrary library, A2uiValidator validator,
            ProductSearchTool products, PageDataTool pageData, UiProfileService profiles,
            UserProfileRepository customers, UserSearchHistoryRepository searches,
            AiModelRequestRepository modelRequests, ObjectMapper mapper) {
        this.customers = customers;
        this.searches = searches;
        this.bedrock = bedrock;
        this.library = library;
        this.validator = validator;
        this.products = products;
        this.pageData = pageData;
        this.profiles = profiles;
        this.modelRequests = modelRequests;
        this.mapper = mapper;
    }

    public void run(RunAgentInput input, EventSink sink) throws IOException {
        run(input, null, sink);
    }

    /**
     * @param customerId Customer đã đăng nhập (đã được AgentService kiểm tra role), hoặc null với Guest.
     *                   Chỉ dùng để lấy dữ liệu của chính khách đó (yêu thích) khi dựng trang.
     */
    public void run(RunAgentInput input, UUID customerId, EventSink sink) throws IOException {
        AgentState state = input.state();
        String locale = state != null && "en".equals(state.locale()) ? "en" : "vi";
        List<ChatMessage> messages = input.messages();
        String latest = messages.get(messages.size() - 1).content().trim();
        String history = history(messages);

        // Chỉ kiểm câu người dùng vừa gõ; bị chặn thì trả lời từ chối và không gọi model.
        BedrockClient.GuardrailCheck guard = bedrock.checkUserInput(latest);
        if (guard.blocked()) {
            log.info("AI request blocked by guardrail");
            sendText(sink, blockedText(guard.message(), locale));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("locale", locale);
            if (state != null) {
                snapshot.put("view", state.view());
                snapshot.put("density", state.density());
                snapshot.put("lastQuery", state.lastQuery());
                snapshot.put("lastSort", state.lastSort());
            }
            snapshot.put("lastIntents", List.of("BLOCKED"));
            sink.send(event("STATE_SNAPSHOT", "snapshot", snapshot));
            return;
        }

        boolean personalize = customerId != null && state != null && Boolean.TRUE.equals(state.personalizationEnabled());
        UiProfile profile = personalize ? profiles.profile(customerId, null) : UiProfile.empty();
        Map<String, Object> context = personalize ? shoppingContext(customerId, profile) : Map.of();
        Intent intent = classify(history, latest, state, context);
        if ("page".equals(input.mode())) {
            log.info("AI page intent {}", intent.intents());
            generatePage(intent, latest, state, locale, customerId, personalize, profile, sink);
            return;
        }
        Route route = route(intent);
        log.info("AI intent {} → {}", intent.intents(), route);
        if (route == Route.GENERATE_UI) {
            generateUi(intent, latest, state, locale, profile, sink);
            return;
        }
        if (route == Route.NAVIGATE) {
            // Trợ lý không có dữ liệu giỏ/đơn/hồ sơ, nên câu trả lời cố định để tránh model đoán trạng thái.
            String page = intent.intents().stream().filter(NAVIGATE_PAGES::containsKey).map(NAVIGATE_PAGES::get)
                    .findFirst().orElse("catalog");
            sendText(sink, navigateText(page, locale));
            sendSurface(sink, newSurfaceId("chat"), products.empty().dataModel(), navigateLayout(page, locale));
        } else {
            sendText(sink, answer(intent, route, history, latest));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("locale", locale);
        if (state != null) {
            snapshot.put("view", state.view());
            snapshot.put("density", state.density());
            snapshot.put("lastQuery", state.lastQuery());
            snapshot.put("lastSort", state.lastSort());
        }
        snapshot.put("lastIntents", intent.intents());
        sink.send(event("STATE_SNAPSHOT", "snapshot", snapshot));
    }

    private static String blockedText(String guardrailMessage, String locale) {
        if ("en".equals(locale)) {
            return "Sorry, I can only help with products and orders on Bite to Business.";
        }
        return guardrailMessage == null || guardrailMessage.isBlank()
                ? "Xin lỗi, mình chỉ hỗ trợ các câu hỏi về sản phẩm và đặt hàng trên Bite to Business."
                : guardrailMessage;
    }

    private static String navigateText(String page, String locale) {
        boolean en = "en".equals(locale);
        return switch (page) {
            case "cart" -> en ? "You can review items, packages, coupons and totals on the cart page."
                    : "Bạn xem sản phẩm, chia gói giao, áp coupon và kiểm tra tổng tiền ở trang giỏ hàng.";
            case "orders" -> en ? "Your orders, package status and refund requests are on the orders page."
                    : "Đơn hàng, trạng thái từng gói và yêu cầu hoàn tiền nằm ở trang đơn hàng của bạn.";
            case "profile" -> en ? "You can update your delivery address in your profile."
                    : "Bạn cập nhật địa chỉ giao hàng trong trang hồ sơ.";
            default -> en ? "You can browse all products in the catalog." : "Bạn xem toàn bộ sản phẩm ở trang danh mục.";
        };
    }

    // ---------- Bước 1: ý định ----------

    /**
     * Bước 1 — nhận diện ý định bằng Bedrock (prompts/intent.md, temperature 0). Trả về intent + slot đã trích
     * (product_name có dấu, product_names, target_view…). Model trả JSON hỏng thì coi như không rõ ý định
     * (intents rỗng) để luồng trả lời văn bản xử lý, không làm vỡ request.
     */
    private Map<String, Object> shoppingContext(UUID customerId, UiProfile profile) {
        Map<String, Object> context = new LinkedHashMap<>();
        try {
            customers.findById(customerId).map(UserProfile::getCustomerType)
                    .ifPresent(type -> context.put("customer_type", type.name()));
            List<String> recent = searches.findTop5ByUserIdOrderBySearchedAtDescIdDesc(customerId).stream()
                    .map(UserSearchHistory::getKeyword).toList();
            if (!recent.isEmpty()) context.put("recent_searches", recent);
        } catch (org.springframework.dao.DataAccessException exception) {
            log.warn("Customer shopping context unavailable: {}", exception.getClass().getSimpleName());
        }
        if (profile.personalized()) context.put("ui_profile", profile.toPrompt(Map.of()));
        return context;
    }

    private Intent classify(String history, String latest, AgentState state, Map<String, Object> context) {
        String system = library.systemPrompt() + "\n\n" + library.intentPrompt();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("latest_message", latest);
        input.put("conversation", history);
        if (state != null && state.lastQuery() != null) input.put("last_query", state.lastQuery());
        input.put("shopping_context", context);
        ModelReply reply = callModel(system, json(input), 1100, 0F, "INTENT");
        JsonNode json = parseJson(reply.text());
        if (json == null || !json.path("intents").isArray()) {
            record(reply, "FAILED", "INTENT_PARSE");
            return new Intent(List.of(), mapper.createObjectNode(), mapper.createArrayNode(), false, null);
        }
        record(reply, "SUCCEEDED", null);
        List<String> intents = new ArrayList<>();
        json.path("intents").forEach(node -> intents.add(node.asString("")));
        JsonNode slots = json.path("slots").isObject() ? json.path("slots") : mapper.createObjectNode();
        String question = json.path("clarification_question").isString()
                ? json.path("clarification_question").asString()
                : null;
        var requests = mapper.createArrayNode();
        JsonNode parts = json.path("requests");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (!part.isObject() || !intents.contains(part.path("intent").asString(""))) continue;
                ObjectNode item = requests.addObject();
                for (String key : List.of("intent", "subject", "constraint", "subject_source", "unit")) {
                    JsonNode field = part.path(key);
                    if (field.isString() && field.asString().length() <= 160) item.put(key, field.asString());
                }
                JsonNode quantity = part.path("quantity");
                if (quantity.isNumber() && quantity.decimalValue().signum() > 0) item.set("quantity", quantity);
                if (requests.size() == 6) break;
            }
        }
        return new Intent(intents, slots, requests, json.path("needs_clarification").asBoolean(false), question);
    }

    /**
     * Chế độ chat: chọn cách xử lý theo intent. Có intent tìm/so sánh/xem giá → tạo UI; giỏ/đơn/hồ sơ → điều
     * hướng bằng câu cố định; chức năng chưa có → nói chưa hỗ trợ; còn lại → trả lời từ business context.
     */
    private static Route route(Intent intent) {
        if (intent.intents().stream().anyMatch(UI_INTENTS::contains)) {
            return Route.GENERATE_UI;
        }
        if (intent.intents().contains("CHECK_PROMOTIONS") && !slot(intent, "product_name").isBlank()) {
            return Route.GENERATE_UI;
        }
        if (intent.intents().stream().anyMatch(NAVIGATE_PAGES::containsKey)) {
            return Route.NAVIGATE;
        }
        if (intent.intents().stream().anyMatch(UNSUPPORTED_INTENTS::contains)) {
            return Route.UNSUPPORTED;
        }
        return Route.ANSWER;
    }

    // ---------- Bước 2: tool + sinh UI ----------

    /**
     * Chế độ chat — bước 2–4: xác định từ khóa (slot hoặc dùng lại từ khóa lượt trước khi chỉ đổi kiểu hiển thị),
     * gọi ProductSearchTool lấy dữ liệu thật, cho model chọn bố cục, rồi phát text + surface + STATE_SNAPSHOT
     * (FE gửi lại snapshot ở lượt sau để giữ ngắn hạn: kiểu hiển thị, từ khóa, sort).
     */
    private void generateUi(Intent intent, String latest, AgentState state, String locale, UiProfile profile,
            EventSink sink)
            throws IOException {
        List<String> keywords = new ArrayList<>();
        intent.slots().path("product_names").forEach(node -> keywords.add(node.asString("")));
        if (keywords.stream().allMatch(String::isBlank)) {
            keywords.clear();
            String name = slot(intent, "product_name");
            if (!name.isBlank()) {
                keywords.add(name);
            }
        }
        boolean reuseLast = intent.intents().contains("CHANGE_UI_PREFERENCE") && keywords.isEmpty()
                && state != null && state.lastQuery() != null && !state.lastQuery().isBlank()
                && !intent.needsClarification();
        if (reuseLast) {
            keywords.add(state.lastQuery());
        }
        String sort = slot(intent, "sort");
        if (sort.isBlank() && reuseLast) {
            sort = state.lastSort();
        }
        boolean browseNewest = keywords.isEmpty() && "newest".equals(sort);
        AttributeQuery attributes = attributes(intent, keywords);
        String question = intent.question();
        SearchResult result;
        if (isBroadHealthyRequest(latest)) {
            result = products.search(List.of("rau", "củ", "quả"), sort, 12, locale);
        } else if (keywords.isEmpty() && !browseNewest && attributes == null) {
            result = products.empty();
            if (question == null || question.isBlank()) {
                question = "en".equals(locale) ? "Which product are you looking for?" : "Bạn đang tìm sản phẩm nào?";
            }
        } else {
            result = findProducts(intent, keywords, attributes, sort, locale, browseNewest ? 8 : 12);
        }

        Map<String, Object> preference = preference(intent, state);
        String surfaceId = newSurfaceId("chat");
        Intent shown = new Intent(intent.intents(), intent.slots(), intent.requests(), question != null && result.resultCount() == 0,
                result.resultCount() == 0 ? question : null);
        String profilePrompt = profile.personalized() ? json(profile.toPrompt(Map.of())) : "null";
        JsonNode components = generateComponents(shown, latest, result, preference, profilePrompt, surfaceId);

        sendText(sink, summaryText(result, shown.question(), locale));
        sendSurface(sink, surfaceId, result.dataModel(), components);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("locale", locale);
        snapshot.put("view", preference.get("view"));
        snapshot.put("density", preference.get("density"));
        snapshot.put("lastQuery", result.query());
        snapshot.put("lastSort", ((Map<?, ?>) result.dataModel().get("query")).get("sort"));
        snapshot.put("lastIntents", intent.intents());
        sink.send(event("STATE_SNAPSHOT", "snapshot", snapshot));
    }

    /**
     * Cho model sinh updateComponents cho khối chat. Model chỉ thấy tóm tắt không có giá; kết quả phải qua
     * A2uiValidator. Sai thì gửi danh sách lỗi để model sửa đúng 1 lần, sau đó dùng defaultLayout.
     */
    private JsonNode generateComponents(Intent intent, String latest, SearchResult result,
            Map<String, Object> preference, String profilePrompt, String surfaceId) {
        String system = uiSystemPrompt(intent, result.resultCount(), !preference.isEmpty());
        String user = "<latest_message>\n" + latest + "\n</latest_message>\n<intent>\n" + json(intentJson(intent))
                + "\n</intent>\n<data_model_summary>\n" + json(result.summary()) + "\n</data_model_summary>\n"
                + "<ui_preference>\n" + (preference.isEmpty() ? "null" : json(preference)) + "\n</ui_preference>\n"
                + "<ui_profile>\n" + profilePrompt + "\n</ui_profile>\n"
                + "<surface_id>" + surfaceId + "</surface_id>";
        String prompt = user;
        for (int attempt = 1; attempt <= 2; attempt++) {
            ModelReply reply;
            try {
                reply = callModel(system, prompt, 1500, 0.2F, "UI");
            } catch (RuntimeException exception) {
                return defaultLayout(result, surfaceId);
            }
            JsonNode message = parseJson(reply.text());
            if (message != null && message.path("updateComponents").isObject()) {
                ((ObjectNode) message.path("updateComponents")).put("surfaceId", surfaceId);
            }
            List<String> errors = validator.validate(message, result.resultCount());
            if (errors.isEmpty()) {
                record(reply, "SUCCEEDED", null);
                return message;
            }
            record(reply, "FAILED", "UI_INVALID");
            log.info("A2UI validation failed (attempt {}): {}", attempt, errors);
            prompt = user + "\n\nKết quả trước của bạn chưa hợp lệ:\n- "
                    + String.join("\n- ", errors.subList(0, Math.min(errors.size(), 10)))
                    + "\nHãy sửa và trả lại đúng một JSON updateComponents.";
        }
        return defaultLayout(result, surfaceId);
    }

    /** Thuộc tính và giới hạn lấy cùng schema mà validator dùng để tránh prompt lệch catalog. */
    private ObjectNode componentCatalog() {
        ObjectNode guide = mapper.createObjectNode();
        for (Map.Entry<String, JsonNode> entry : library.catalogSchema().path("components").properties()) {
            ObjectNode component = guide.putObject(entry.getKey());
            component.set("description", library.contract().path("components").path(entry.getKey()));
            ObjectNode props = component.putObject("properties");
            var required = component.putArray("required");
            for (JsonNode part : entry.getValue().path("allOf")) {
                for (Map.Entry<String, JsonNode> property : part.path("properties").properties()) {
                    if (!"component".equals(property.getKey())) props.set(property.getKey(), property.getValue());
                }
                part.path("required").forEach(field -> {
                    if (!"component".equals(field.asString())) required.add(field.asString());
                });
            }
        }
        return guide;
    }

    private String uiSystemPrompt(Intent intent, int resultCount, boolean hasPreference) {
        JsonNode contract = library.contract();
        ObjectNode catalog = mapper.createObjectNode();
        catalog.set("components", componentCatalog());
        catalog.set("actions", contract.path("actions"));
        catalog.set("limits", contract.path("modes").path("chat"));
        catalog.set("bindablePaths", contract.path("dataModel").path("bindablePaths"));
        StringBuilder system = new StringBuilder()
                .append(library.systemPrompt()).append("\n\n")
                .append(library.uiPrompt()).append("\n\n<catalog>\n").append(json(catalog)).append("\n</catalog>\n");
        List<JsonNode> examples = library.pickUiExamples(intent.intents(), resultCount, hasPreference, UI_EXAMPLES);
        if (!examples.isEmpty()) {
            system.append("\n# Ví dụ tham khảo (tên sản phẩm là giả lập)\n");
        }
        int index = 1;
        for (JsonNode example : examples) {
            system.append("\n## Ví dụ ").append(index++).append("\n<latest_message>")
                    .append(example.path("input").asString()).append("</latest_message>\n<intent>")
                    .append(json(example.path("intent"))).append("</intent>\n<data_model_summary>")
                    .append(json(example.path("data_model_summary"))).append("</data_model_summary>\n<ui_preference>")
                    .append(json(example.path("ui_preference"))).append("</ui_preference>\nKết quả:\n")
                    .append(json(example.path("output"))).append("\n");
        }
        return system.toString();
    }

    /** Bố cục cố định khi model lỗi hoặc sai schema hai lần; không phụ thuộc AI. */
    JsonNode defaultLayout(SearchResult result, String surfaceId) {
        List<Map<String, Object>> components = new ArrayList<>();
        if (result.resultCount() == 0) {
            components.add(Map.of("id", "root", "component", "Column", "children", List.of("empty")));
            components.add(Map.of("id", "empty", "component", "Notice", "tone", "empty", "text",
                    "Chưa có sản phẩm phù hợp. Bạn thử từ khóa khác nhé."));
        } else {
            components.add(Map.of("id", "root", "component", "Column", "children", List.of("title", "results")));
            components.add(Map.of("id", "title", "component", "Text", "variant", "h3", "text",
                    result.query() == null ? "Sản phẩm" : (Object) Map.of("path", "/query/text")));
            components.add(Map.of("id", "results", "component", "ProductGrid", "items", Map.of("path", "/products"),
                    "columns", 3));
        }
        return updateComponents(surfaceId, components);
    }

    /** Surface chỉ có một nút open_page tới trang có sẵn (giỏ, đơn hàng, hồ sơ); dựng cố định, không qua model. */
    private JsonNode navigateLayout(String page, String locale) {
        boolean en = "en".equals(locale);
        String label = switch (page) {
            case "cart" -> en ? "Open cart" : "Mở giỏ hàng";
            case "orders" -> en ? "View my orders" : "Xem đơn hàng của tôi";
            case "profile" -> en ? "Open profile" : "Mở hồ sơ";
            default -> en ? "Browse products" : "Xem sản phẩm";
        };
        List<Map<String, Object>> components = List.of(
                Map.of("id", "root", "component", "Column", "children", List.of("open-btn")),
                Map.of("id", "open-btn", "component", "Button", "child", "open-label", "variant", "primary", "action",
                        Map.of("event", Map.of("name", "open_page", "context", Map.of("page", page)))),
                Map.of("id", "open-label", "component", "Text", "text", label));
        return updateComponents(null, components);
    }

    /** Gói danh sách component thành message A2UI v0.9 updateComponents. */
    private JsonNode updateComponents(String surfaceId, List<Map<String, Object>> components) {
        ObjectNode message = mapper.createObjectNode();
        message.put("version", "v0.9");
        ObjectNode update = message.putObject("updateComponents");
        update.put("surfaceId", surfaceId);
        update.set("components", mapper.valueToTree(components));
        return message;
    }

    // ---------- Chế độ Page: AI ghép cả trang ----------

    /**
     * Chế độ page: AI ghép cả trang thay trang mặc định. Lấy mọi nguồn dữ liệu trang có thể dùng (PageDataTool,
     * kèm danh sách cá nhân theo hồ sơ thói quen), cho model chọn section, validate, rồi phát surface.
     * STATE_SNAPSHOT kèm pageFallback/validationErrors/personalized để bảng cây widget và nghiên cứu theo dõi.
     */
    private void generatePage(Intent intent, String latest, AgentState state, String locale, UUID customerId,
            boolean personalize, UiProfile profile, EventSink sink) throws IOException {
        UUID recommendationOwner = personalize ? customerId : null;
        List<String> keywords = new ArrayList<>();
        intent.slots().path("product_names").forEach(node -> {
            if (!node.asString("").isBlank()) {
                keywords.add(node.asString());
            }
        });
        String name = slot(intent, "product_name");
        if (keywords.isEmpty() && !name.isBlank()) {
            keywords.add(name);
        }
        String sort = slot(intent, "sort");
        AttributeQuery attributes = attributes(intent, keywords);
        PageDataTool.PageData data = isBroadHealthyRequest(latest)
                ? pageData.build(products.search(List.of("rau", "củ", "quả"), sort, 12, locale), locale, recommendationOwner, profile)
                : keywords.isEmpty() && attributes == null
                ? pageData.build(keywords, sort, locale, recommendationOwner, profile)
                : pageData.build(findProducts(intent, keywords, attributes, sort, locale, 12), locale, recommendationOwner,
                        profile);
        Map<String, Object> preference = preference(intent, state);
        String surfaceId = newSurfaceId("page");
        List<String> diagnostics = new ArrayList<>();
        String profilePrompt = profile.personalized() ? json(profile.toPrompt(data.productNames())) : "null";
        JsonNode components = generatePageComponents(intent, latest, data, preference, profilePrompt,
                customerId != null, surfaceId, diagnostics);
        sendSurface(sink, surfaceId, data.dataModel(), components);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("locale", locale);
        snapshot.put("view", preference.get("view"));
        snapshot.put("density", preference.get("density"));
        snapshot.put("lastQuery", data.query());
        snapshot.put("lastSort", data.sort());
        snapshot.put("lastIntents", intent.intents());
        // Cho bảng cây widget/nghiên cứu: trang có phải bố cục dự phòng không và vì sao.
        snapshot.put("pageFallback", !diagnostics.isEmpty());
        snapshot.put("personalized", profile.personalized());
        snapshot.put("profileEvents", profile.evidenceEvents());
        snapshot.put("validationErrors", diagnostics.stream().limit(8).toList());
        sink.send(event("STATE_SNAPSHOT", "snapshot", snapshot));
    }

    /**
     * Prompt chế độ page: system-prompt + business-context + prompts/page.md + catalog; nội dung lượt gồm yêu cầu,
     * intent, tóm tắt từng danh sách (count + tên, không giá), lựa chọn hiển thị, hồ sơ thói quen và loại khách.
     * {@code diagnostics} nhận lỗi validate của lần thử cuối (rỗng nếu thành công).
     */
    private JsonNode generatePageComponents(Intent intent, String latest, PageDataTool.PageData data,
            Map<String, Object> preference, String profilePrompt, boolean customer, String surfaceId,
            List<String> diagnostics) {
        JsonNode contract = library.contract();
        ObjectNode catalog = mapper.createObjectNode();
        catalog.set("components", componentCatalog());
        catalog.set("actions", contract.path("actions"));
        catalog.set("dataModel", contract.path("dataModel"));
        catalog.set("limits", contract.path("modes").path("page"));
        String system = library.systemPrompt() + "\n\n<business_context>\n" + library.businessContext()
                + "\n</business_context>\n\n" + library.pagePrompt() + "\n\n<catalog>\n" + json(catalog)
                + "\n</catalog>";
        String user = "<latest_message>\n" + latest + "\n</latest_message>\n<intent>\n" + json(intentJson(intent))
                + "\n</intent>\n<data_model_summary>\n" + json(data.summary()) + "\n</data_model_summary>\n"
                + "<ui_preference>\n" + (preference.isEmpty() ? "null" : json(preference)) + "\n</ui_preference>\n"
                + "<ui_profile>\n" + profilePrompt + "\n</ui_profile>\n"
                + "<customer>" + (customer ? "CUSTOMER" : "GUEST") + "</customer>\n<surface_id>" + surfaceId
                + "</surface_id>";
        String prompt = user;
        for (int attempt = 1; attempt <= 2; attempt++) {
            ModelReply reply;
            try {
                reply = callModel(system, prompt, 3000, 0.3F, "PAGE");
            } catch (RuntimeException exception) {
                diagnostics.add("MODEL_ERROR: " + exception.getClass().getSimpleName());
                return defaultPageLayout(data, latest, surfaceId);
            }
            JsonNode message = parseJson(reply.text());
            if (message != null && message.path("updateComponents").isObject()) {
                ((ObjectNode) message.path("updateComponents")).put("surfaceId", surfaceId);
            }
            List<String> errors = validator.validate(message, data.lists(), true);
            if (errors.isEmpty()) {
                record(reply, "SUCCEEDED", null);
                diagnostics.clear();
                return message;
            }
            record(reply, "FAILED", "PAGE_INVALID");
            diagnostics.clear();
            diagnostics.addAll(errors);
            log.info("A2UI page validation failed (attempt {}): {}", attempt, errors);
            prompt = user + "\n\nKết quả trước của bạn chưa hợp lệ:\n- "
                    + String.join("\n- ", errors.subList(0, Math.min(errors.size(), 12)))
                    + "\nHãy sửa và trả lại đúng một JSON updateComponents.";
        }
        return defaultPageLayout(data, latest, surfaceId);
    }

    /** Trang mặc định khi model lỗi hoặc sai schema hai lần; chỉ dùng danh sách có dữ liệu. */
    JsonNode defaultPageLayout(PageDataTool.PageData data, String latest, String surfaceId) {
        List<Map<String, Object>> components = new ArrayList<>();
        List<String> sections = new ArrayList<>(List.of("header"));
        components.add(Map.of("id", "header", "component", "PageHeader", "title",
                data.query() == null ? "Gợi ý mua sắm" : "Kết quả cho “" + data.query() + "”"));
        Map<String, Integer> lists = data.lists();
        if (lists.getOrDefault("/products", 0) > 0) {
            sections.add("main");
            components.add(Map.of("id", "main", "component", "Section", "title", "Sản phẩm phù hợp", "variant",
                    "highlight", "child", "main-grid"));
            components.add(Map.of("id", "main-grid", "component", "ProductGrid", "items",
                    Map.of("path", "/products"), "columns", 3));
        } else if (data.query() != null) {
            sections.add("empty");
            components.add(Map.of("id", "empty", "component", "Notice", "tone", "empty", "text",
                    "Chưa có sản phẩm khớp yêu cầu. Bạn xem thử các nhóm hàng bên dưới."));
        }
        String side = lists.getOrDefault("/favorites", 0) > 0 ? "/favorites"
                : lists.getOrDefault("/home/bestSelling", 0) > 0 ? "/home/bestSelling" : null;
        if (side != null) {
            sections.add("side");
            components.add(Map.of("id", "side", "component", "Section", "title",
                    "/favorites".equals(side) ? "Sản phẩm bạn đã lưu" : "Bán chạy", "child", "side-row"));
            components.add(Map.of("id", "side-row", "component", "ProductCarousel", "items", Map.of("path", side)));
        }
        if (lists.getOrDefault("/categories", 0) > 0) {
            sections.add("cats");
            components.add(Map.of("id", "cats", "component", "Section", "title", "Nhóm hàng", "variant", "card",
                    "child", "cat-grid"));
            components.add(Map.of("id", "cat-grid", "component", "CategoryGrid", "items",
                    Map.of("path", "/categories")));
        }
        components.add(0, Map.of("id", "root", "component", "Column", "children", sections));
        return updateComponents(surfaceId, components);
    }

    // ---------- Tra cứu thông tin sản phẩm (bảo quản, xuất xứ, thành phần, dinh dưỡng) ----------

    /**
     * Tìm sản phẩm cho cả hai chế độ:
     * <ul>
     *   <li>có slot {@code attributes} (xuất xứ, bảo quản, thành phần, dinh dưỡng, chứng nhận) → tìm theo thông tin
     *       mô tả trong DB (ProductKnowledgeService), tên sản phẩm (nếu có) thành điều kiện "từ khóa";</li>
     *   <li>ý định ASK_PRODUCT_INFO ("ba chỉ bò bảo quản thế nào?") → tìm theo tên rồi kèm facts cho vài
     *       sản phẩm đầu để widget ProductFacts hiển thị;</li>
     *   <li>còn lại → tìm theo tên/SKU/thương hiệu/danh mục như thường.</li>
     * </ul>
     */
    private SearchResult findProducts(Intent intent, List<String> keywords, AttributeQuery attributes, String sort,
            String locale, int size) {
        if (attributes != null) {
            return products.searchByAttributes(attributes, locale, size);
        }
        SearchResult result = products.search(keywords, sort, size, locale);
        if (intent.intents().contains("ASK_PRODUCT_INFO") && result.resultCount() > 0) {
            return products.withFacts(result, locale, 4);
        }
        return result;
    }

    /** Only broaden general healthy requests; never discard a specific dietary condition. */
    private static boolean isBroadHealthyRequest(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ").trim();
        boolean healthIntent = normalized.matches(".*\\b(healthy|health|khoe|lanh manh|dinh duong)\\b.*");
        if (!healthIntent) return false;
        String remainder = normalized.replaceAll(
                "\\b(san|pham|thuc|an|do|mon|hang|loai|nao|co|khong|ko|khong|k|tot|cho|suc|khoe|healthy|health|lanh|manh|dinh|duong|tu|nhien|ban|tim|kiem|minh|toi|tui|gi|goi|y|muon|can|xin|xem|voi|nhe|a|duoc|of|food|foods|product|products|healthy|health|for|me|any|some|are|there|do|you|have|please|recommend|show)\\b", " ")
                .replaceAll("\\s+", "").trim();
        return remainder.isEmpty();
    }

    /** Đọc slot attributes của bước ý định; không có điều kiện thuộc tính nào thì trả null (tìm theo tên). */
    private static AttributeQuery attributes(Intent intent, List<String> keywords) {
        JsonNode node = intent.slots().path("attributes");
        if (!node.isObject()) {
            return null;
        }
        AttributeQuery query = new AttributeQuery(words(node.path("origin")), words(node.path("storage")),
                words(node.path("contains")), words(node.path("nutrient")), words(node.path("compliance")),
                keywords.isEmpty() ? null : keywords);
        AttributeQuery onlyAttributes = new AttributeQuery(query.origin(), query.storage(), query.contains(),
                query.nutrient(), query.compliance(), null);
        return onlyAttributes.isEmpty() ? null : query;
    }

    /** Slot có thể là chuỗi hoặc mảng chuỗi; tối đa 6 từ, bỏ chuỗi rỗng. */
    private static List<String> words(JsonNode node) {
        List<String> words = new ArrayList<>();
        if (node.isString() && !node.asString().isBlank()) {
            words.add(node.asString().trim());
        } else if (node.isArray()) {
            node.forEach(item -> {
                if (item.isString() && !item.asString().isBlank() && words.size() < 6) {
                    words.add(item.asString().trim());
                }
            });
        }
        return words.isEmpty() ? null : words;
    }

    // ---------- Trả lời bằng văn bản ----------

    /**
     * Trả lời bằng văn bản cho câu hỏi chính sách/chức năng chưa hỗ trợ, dựa trên business-context.md.
     * Prompt cấm khẳng định trạng thái giỏ/đơn của khách vì model không có dữ liệu đó.
     */
    private String answer(Intent intent, Route route, String history, String latest) {
        String guidance = switch (route) {
            case UNSUPPORTED -> "Chức năng này chưa được hỗ trợ. Nói rõ điều đó và gợi ý cách làm hiện có nếu "
                    + "business context có nêu.";
            default -> "Trả lời dựa trên business context. Nếu không có thông tin, nói chưa có dữ liệu.";
        };
        String system = library.systemPrompt() + "\n\n<business_context>\n" + library.businessContext()
                + "\n</business_context>\n\nÝ định đã nhận diện: " + intent.intents() + ". " + guidance
                + " Bạn không có dữ liệu giỏ hàng, đơn hàng hay tài khoản của người dùng: không khẳng định họ có"
                + " hay không có đơn, hàng hay số tiền nào. Trả lời tối đa 5 câu, văn bản thường, không tạo nút giả"
                + " như [NÚT].";
        String user = "<conversation>\n" + history + "\n</conversation>\n<latest_message>\n" + latest
                + "\n</latest_message>";
        user += "\n<intent>\n" + json(intentJson(intent)) + "\n</intent>";
        ModelReply reply = callModel(system, user, 500, 0.3F, "ANSWER");
        record(reply, "SUCCEEDED", null);
        return reply.text().trim();
    }

    // ---------- Tiện ích ----------

    /** Gọi Bedrock một lượt; lỗi thì ghi FAILED kèm bước (INTENT/UI/PAGE/ANSWER) rồi ném tiếp cho nơi gọi fallback. */
    private ModelReply callModel(String system, String user, int maxTokens, float temperature, String stage) {
        try {
            return bedrock.converse(system, user, maxTokens, temperature);
        } catch (RuntimeException exception) {
            String code = (stage + "_" + exception.getClass().getSimpleName());
            modelRequests.record(bedrock.modelId(), "FAILED", null, null, null,
                    code.length() > 100 ? code.substring(0, 100) : code);
            throw exception;
        }
    }

    /** Ghi token/thời gian/kết quả của một lượt gọi vào ai_model_requests; lỗi ghi log không được làm hỏng request. */
    private void record(ModelReply reply, String status, String errorCode) {
        try {
            modelRequests.record(bedrock.modelId(), status,
                    reply.inputTokens() == null ? null : reply.inputTokens().longValue(),
                    reply.outputTokens() == null ? null : reply.outputTokens().longValue(), reply.durationMs(),
                    errorCode);
        } catch (RuntimeException exception) {
            log.warn("Could not record AI model request: {}", exception.getClass().getSimpleName());
        }
    }

    /** Lấy JSON object đầu tiên trong câu trả lời của model (bỏ chữ thừa/markdown quanh nó); không đọc được thì null. */
    private JsonNode parseJson(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (JacksonException exception) {
            return null;
        }
    }

    /** Phát một tin nhắn văn bản theo AG-UI: TEXT_MESSAGE_START → CONTENT → END. */
    private void sendText(EventSink sink, String text) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }
        String messageId = UUID.randomUUID().toString();
        sink.send(event("TEXT_MESSAGE_START", "messageId", messageId, "role", "assistant"));
        sink.send(event("TEXT_MESSAGE_CONTENT", "messageId", messageId, "delta", text));
        sink.send(event("TEXT_MESSAGE_END", "messageId", messageId));
    }

    /** A2UI đi trong AG-UI CUSTOM event: createSurface → updateDataModel (backend) → updateComponents (model). */
    private void sendSurface(EventSink sink, String surfaceId, Map<String, Object> dataModel, JsonNode components)
            throws IOException {
        ((ObjectNode) components.path("updateComponents")).put("surfaceId", surfaceId);
        Map<String, Object> create = Map.of("version", "v0.9", "createSurface",
                Map.of("surfaceId", surfaceId, "catalogId", library.contract().path("catalogId").asString()));
        Map<String, Object> data = Map.of("version", "v0.9", "updateDataModel",
                Map.of("surfaceId", surfaceId, "path", "/", "value", dataModel));
        sink.send(event("CUSTOM", "name", "a2ui", "value", List.of(create, data, components)));
    }

    /** Lựa chọn hiển thị của phiên: người dùng vừa nói (slot) thắng state FE gửi lại; chỉ nhận grid/table, compact/comfortable. */
    private Map<String, Object> preference(Intent intent, AgentState state) {
        Map<String, Object> preference = new LinkedHashMap<>();
        String view = slot(intent, "target_view");
        String density = slot(intent, "density");
        if (view.isBlank() && state != null && state.view() != null) {
            view = state.view();
        }
        if (density.isBlank() && state != null && state.density() != null) {
            density = state.density();
        }
        if (Set.of("grid", "table").contains(view)) {
            preference.put("view", view);
        }
        if (Set.of("compact", "comfortable").contains(density)) {
            preference.put("density", density);
        }
        return preference;
    }

    /** Câu chữ ngắn đi kèm khối UI trong chat; dựng cố định từ số kết quả, không để model viết số. */
    private static String summaryText(SearchResult result, String question, String locale) {
        boolean en = "en".equals(locale);
        if (question != null && !question.isBlank()) {
            return question;
        }
        if (result.resultCount() == 0) {
            return en ? "I couldn't find a matching product." : "Mình chưa tìm thấy sản phẩm phù hợp.";
        }
        if (result.query() == null) {
            return en ? "Here are the latest products." : "Đây là các sản phẩm mới nhất.";
        }
        return en ? "Found " + result.resultCount() + " products for “" + result.query() + "”."
                : "Mình tìm thấy " + result.resultCount() + " sản phẩm cho “" + result.query() + "”.";
    }

    private Map<String, Object> intentJson(Intent intent) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("intents", intent.intents());
        value.put("slots", intent.slots());
        value.put("requests", intent.requests());
        value.put("needs_clarification", intent.needsClarification());
        value.put("clarification_question", intent.question());
        return value;
    }

    /** Ngắn hạn: 6 lượt gần nhất (mỗi lượt tối đa 500 ký tự) làm ngữ cảnh hội thoại cho bước nhận diện ý định. */
    private static String history(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        int from = Math.max(0, messages.size() - 1 - HISTORY_TURNS);
        for (ChatMessage message : messages.subList(from, messages.size() - 1)) {
            String content = message.content() == null ? "" : message.content().trim();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            text.append("user".equals(message.role()) ? "user" : "assistant").append(": ").append(content)
                    .append('\n');
        }
        return text.toString().trim();
    }

    private static String slot(Intent intent, String name) {
        JsonNode value = intent.slots().path(name);
        return value.isString() ? value.asString().trim() : "";
    }

    private static String newSurfaceId(String mode) {
        return mode + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private static Map<String, Object> event(String type, Object... pairs) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        for (int i = 0; i < pairs.length; i += 2) {
            event.put((String) pairs[i], pairs[i + 1]);
        }
        return event;
    }
}
