package com.se203730.mvc.generateuibe.features.ai.service.context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Nạp prompt, business context, catalog A2UI và ví dụ few-shot từ classpath một lần khi khởi động.
 * Dataset EVAL không nằm ở đây; chỉ ví dụ split EXAMPLE có trạng thái được phép mới được đưa vào prompt.
 */
@Component
public class AiPromptLibrary {
    private static final String BASE = "ai/";

    private final String systemPrompt;
    private final String businessContext;
    private final String intentPrompt;
    private final String uiPrompt;
    private final JsonNode contract;
    private final JsonNode catalogSchema;
    private final List<JsonNode> uiExamples;

    public AiPromptLibrary(
            ObjectMapper mapper,
            @Value("${app.ai.examples.allowed-statuses:APPROVED,DRAFT}") Set<String> allowedStatuses) {
        this.systemPrompt = stripComments(read(BASE + "system-prompt.md"));
        this.businessContext = stripComments(read(BASE + "business-context.md"));
        this.intentPrompt = stripComments(read(BASE + "prompts/intent.md"));
        this.uiPrompt = stripComments(read(BASE + "prompts/ui.md"));
        this.contract = mapper.readTree(read(BASE + "catalog.json"));
        this.catalogSchema = mapper.readTree(read("a2ui/catalog/bite-to-business.catalog.json"));
        List<JsonNode> examples = new ArrayList<>();
        for (String file : List.of("examples/ui-single-turn.jsonl", "examples/ui-multi-turn.jsonl")) {
            for (String line : read(BASE + file).split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode example = mapper.readTree(line);
                if ("EXAMPLE".equals(example.path("split").asString())
                        && allowedStatuses.contains(example.path("status").asString())
                        && example.path("output").isObject()) {
                    examples.add(example);
                }
            }
        }
        this.uiExamples = List.copyOf(examples);
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String businessContext() {
        return businessContext;
    }

    public String intentPrompt() {
        return intentPrompt;
    }

    public String uiPrompt() {
        return uiPrompt;
    }

    public JsonNode contract() {
        return contract;
    }

    public JsonNode catalogSchema() {
        return catalogSchema;
    }

    public String contractVersion() {
        return contract.path("contractVersion").asString();
    }

    /** Chọn tối đa {@code limit} ví dụ có intent trùng nhiều nhất; ưu tiên ví dụ cùng tình huống kết quả rỗng/nhiều. */
    public List<JsonNode> pickUiExamples(List<String> intents, int resultCount, boolean hasPreference, int limit) {
        Set<String> wanted = new HashSet<>(intents);
        return uiExamples.stream()
                .sorted(Comparator.comparingInt((JsonNode example) -> -score(example, wanted, resultCount, hasPreference))
                        .thenComparing(example -> example.path("id").asString(""))
                        .thenComparing(example -> example.path("conversation_id").asString(""))
                        .thenComparingInt(example -> example.path("turn").asInt()))
                .limit(limit)
                .toList();
    }

    private static int score(JsonNode example, Set<String> wanted, int resultCount, boolean hasPreference) {
        int score = 0;
        for (JsonNode intent : example.path("intent").path("intents")) {
            if (wanted.contains(intent.asString())) {
                score += 3;
            }
        }
        int exampleCount = example.path("data_model_summary").path("resultCount").asInt();
        if ((exampleCount == 0) == (resultCount == 0)) {
            score += 2;
        }
        if ((exampleCount == 1) == (resultCount == 1)) {
            score += 1;
        }
        if (hasPreference && !example.path("ui_preference").isNull() && !example.path("ui_preference").isMissingNode()) {
            score += 1;
        }
        return score;
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?s)<!--.*?-->", "").trim();
    }

    private static String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Missing AI resource: " + path, exception);
        }
    }
}
