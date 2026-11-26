package com.se203730.mvc.generateuibe.features.ai.service.a2ui;

import com.se203730.mvc.generateuibe.features.ai.service.context.AiPromptLibrary;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class A2uiCompositionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiPromptLibrary library = new AiPromptLibrary(mapper, Set.of());
    private final A2uiValidator validator = new A2uiValidator(library);

    private JsonNode message(String components) {
        return mapper.readTree("""
                {"version":"v0.9","updateComponents":{"surfaceId":"page-test","components":%s}}
                """.formatted(components));
    }

    private JsonNode layout(String mode, String path) {
        return message("""
                [
                  {"id":"root","component":"LayoutGrid","layout":"%s","children":["list","spotlight"]},
                  {"id":"list","component":"ProductList","items":{"path":"%s"},"maxItems":8},
                  {"id":"spotlight","component":"ProductSpotlight","product":{"path":"/products/0"}}
                ]
                """.formatted(mode, path));
    }

    @Test
    void acceptsAllResponsiveLayoutsInPageAndChat() {
        for (String mode : Set.of("equal", "thirds", "sidebar-left", "sidebar-right")) {
            assertEquals(java.util.List.of(), validator.validate(layout(mode, "/products"), Map.of("/products", 2), true));
            assertEquals(java.util.List.of(), validator.validate(layout(mode, "/products"), 2));
        }
    }

    @Test
    void rejectsInvalidLayoutAndWrongDataSources() {
        assertFalse(validator.validate(layout("invented", "/products"), 2).isEmpty());
        assertFalse(validator.validate(layout("equal", "/categories"), Map.of("/products", 2, "/categories", 3), true).isEmpty());
        assertFalse(validator.validate(layout("equal", "/products"), 0).isEmpty());
        assertFalse(validator.validate(message("""
                [{"id":"root","component":"ProductSpotlight","product":{"path":"/categories/0"}}]
                """), Map.of("/categories", 1), true).isEmpty());
        assertFalse(validator.validate(message("""
                [{"id":"root","component":"ProductSpotlight","product":{"path":"/products/2"}}]
                """), 2).isEmpty());
    }

    @Test
    void rejectsUnsupportedPropsAndEmptyLayout() {
        assertFalse(validator.validate(message("""
                [{"id":"root","component":"LayoutGrid","children":[]}]
                """), 1).isEmpty());
        assertFalse(validator.validate(message("""
                [{"id":"root","component":"ProductList","items":{"path":"/products"},"maxItems":21}]
                """), 1).isEmpty());
        assertFalse(validator.validate(message("""
                [{"id":"root","component":"ProductSpotlight","product":{"path":"/products/0"},"html":"unsafe"}]
                """), 1).isEmpty());
    }
}

