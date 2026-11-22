package com.se203730.mvc.generateuibe.features.ai.service.a2ui;

import com.se203730.mvc.generateuibe.features.ai.service.context.AiPromptLibrary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Kiểm tra message updateComponents do model sinh theo catalog Bite to Business: component/thuộc tính cho phép,
 * enum, path được bind, action allowlist, cấu trúc cây và không chép số tiền vào literal.
 */
@Component
public class A2uiValidator {
    private static final Pattern BINDABLE = Pattern.compile("^/(query/(text|sort)|resultCount|products(/\\d{1,2})?)$");
    private static final Pattern PRODUCT_INDEX = Pattern.compile("^/products/(\\d+)$");
    private static final Pattern MONEY = Pattern.compile("\\d[\\d.,]*\\s*(đ|vnd|₫|%)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Set<String> PRODUCT_COMPONENTS = Set.of("ProductGrid", "ProductTable", "ProductCard",
            "PriceTiers", "InventoryBadge");

    private record ComponentSpec(Map<String, JsonNode> props, Set<String> required) {
    }

    private final Map<String, ComponentSpec> components = new HashMap<>();
    private final Map<String, JsonNode> actions = new LinkedHashMap<>();
    private final Pattern surfaceId;
    private final int maxComponents;
    private final int maxDepth;
    private final int maxTextLength;

    public A2uiValidator(AiPromptLibrary library) {
        JsonNode catalog = library.catalogSchema();
        for (Map.Entry<String, JsonNode> entry : catalog.path("components").properties()) {
            Map<String, JsonNode> props = new HashMap<>();
            Set<String> required = new HashSet<>(Set.of("id"));
            props.put("accessibility", null);
            props.put("weight", null);
            for (JsonNode part : entry.getValue().path("allOf")) {
                if (part.path("$ref").asString("").endsWith("Checkable")) {
                    props.put("checks", null);
                }
                for (Map.Entry<String, JsonNode> prop : part.path("properties").properties()) {
                    props.put(prop.getKey(), prop.getValue());
                }
                part.path("required").forEach(name -> required.add(name.asString()));
            }
            components.put(entry.getKey(), new ComponentSpec(props, required));
        }
        for (JsonNode option : catalog.path("$defs").path("BtbAction").path("oneOf")) {
            JsonNode event = option.path("properties").path("event").path("properties");
            actions.put(event.path("name").path("const").asString(), event.path("context"));
        }
        JsonNode contract = library.contract();
        this.surfaceId = Pattern.compile(contract.path("surface").path("idPattern").asString());
        this.maxComponents = contract.path("limits").path("maxComponents").asInt(25);
        this.maxDepth = contract.path("limits").path("maxDepth").asInt(6);
        this.maxTextLength = contract.path("limits").path("maxTextLength").asInt(300);
    }

    /** Trả danh sách lỗi; rỗng nghĩa là message hợp lệ. */
    public List<String> validate(JsonNode message, int resultCount) {
        List<String> errors = new ArrayList<>();
        if (message == null || !message.isObject()) {
            errors.add("Kết quả không phải JSON object");
            return errors;
        }
        if (!"v0.9".equals(message.path("version").asString(""))) {
            errors.add("version phải là v0.9");
        }
        if (message.size() != 2 || !message.has("updateComponents")) {
            errors.add("Chỉ được sinh đúng một message updateComponents");
            return errors;
        }
        JsonNode update = message.path("updateComponents");
        if (!surfaceId.matcher(update.path("surfaceId").asString("")).matches()) {
            errors.add("surfaceId không hợp lệ");
        }
        JsonNode list = update.path("components");
        if (!list.isArray() || list.isEmpty()) {
            errors.add("components phải là mảng không rỗng");
            return errors;
        }
        if (list.size() > maxComponents) {
            errors.add("Tối đa " + maxComponents + " component");
        }
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode component : list) {
            String id = component.path("id").asString("");
            if (!ID.matcher(id).matches()) {
                errors.add("id không hợp lệ: " + id);
            }
            if (byId.put(id, component) != null) {
                errors.add("id bị trùng: " + id);
            }
        }
        if (!byId.containsKey("root")) {
            errors.add("Thiếu component id root");
        }
        Map<String, String> parent = new HashMap<>();
        for (JsonNode component : list) {
            checkComponent(component, byId, parent, resultCount, errors);
        }
        checkTree(byId, parent, errors);
        if (resultCount == 0) {
            for (JsonNode component : list) {
                if (PRODUCT_COMPONENTS.contains(component.path("component").asString(""))) {
                    errors.add("Không có sản phẩm nhưng vẫn dùng " + component.path("component").asString());
                }
            }
        }
        return errors;
    }

    private void checkComponent(JsonNode component, Map<String, JsonNode> byId, Map<String, String> parent,
            int resultCount, List<String> errors) {
        String id = component.path("id").asString("");
        String type = component.path("component").asString("");
        ComponentSpec spec = components.get(type);
        if (spec == null) {
            errors.add(id + ": component không có trong catalog: " + type);
            return;
        }
        for (String required : spec.required()) {
            if (!component.has(required)) {
                errors.add(id + ": thiếu " + required);
            }
        }
        for (Map.Entry<String, JsonNode> entry : component.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if ("id".equals(key) || "component".equals(key)) {
                continue;
            }
            if (!spec.props().containsKey(key)) {
                errors.add(id + ": thuộc tính lạ " + key);
                continue;
            }
            if ("children".equals(key)) {
                if (!value.isArray()) {
                    errors.add(id + ": children phải là mảng id");
                    continue;
                }
                for (JsonNode child : value) {
                    linkChild(id, child.asString(""), byId, parent, errors);
                }
            } else if ("child".equals(key)) {
                linkChild(id, value.asString(""), byId, parent, errors);
            } else if (spec.props().get(key) != null) {
                checkValue(id + "." + key, key, spec.props().get(key), value, resultCount, errors);
            }
        }
    }

    private void linkChild(String id, String child, Map<String, JsonNode> byId, Map<String, String> parent,
            List<String> errors) {
        if (!byId.containsKey(child)) {
            errors.add(id + ": tham chiếu id không tồn tại " + child);
        } else if (parent.put(child, id) != null) {
            errors.add(child + ": có nhiều hơn một component cha");
        }
    }

    private void checkTree(Map<String, JsonNode> byId, Map<String, String> parent, List<String> errors) {
        if (parent.containsKey("root")) {
            errors.add("root không được là con của component khác");
        }
        for (String id : byId.keySet()) {
            String current = id;
            int depth = 1;
            Set<String> seen = new HashSet<>();
            while (parent.containsKey(current)) {
                if (!seen.add(current)) {
                    errors.add(id + ": có vòng lặp");
                    break;
                }
                current = parent.get(current);
                depth++;
            }
            if (!"root".equals(current)) {
                errors.add(id + ": không nối tới root");
            }
            if (depth > maxDepth) {
                errors.add(id + ": sâu quá " + maxDepth + " tầng");
            }
        }
    }

    private void checkValue(String where, String key, JsonNode schema, JsonNode value, int resultCount,
            List<String> errors) {
        if (schema.has("const") && !schema.path("const").equals(value)) {
            errors.add(where + " phải là " + schema.path("const"));
            return;
        }
        if (schema.has("enum") && !contains(schema.path("enum"), value)) {
            errors.add(where + " không thuộc " + schema.path("enum"));
            return;
        }
        String type = schema.path("type").asString("");
        if ("integer".equals(type) && !value.canConvertToExactIntegral()) {
            errors.add(where + " phải là số nguyên");
            return;
        }
        if ("boolean".equals(type) && !value.isBoolean()) {
            errors.add(where + " phải là boolean");
            return;
        }
        if (schema.has("minimum") && value.isNumber() && value.asDouble() < schema.path("minimum").asDouble()) {
            errors.add(where + " nhỏ hơn " + schema.path("minimum"));
        }
        if (schema.has("maximum") && value.isNumber() && value.asDouble() > schema.path("maximum").asDouble()) {
            errors.add(where + " lớn hơn " + schema.path("maximum"));
        }
        if ("array".equals(type)) {
            checkArray(where, schema, value, errors);
            return;
        }
        String ref = schema.path("$ref").asString("");
        if (ref.endsWith("DataBinding") && !isBinding(value)) {
            errors.add(where + " phải là {\"path\": ...}");
            return;
        }
        if (ref.endsWith("DynamicString")) {
            if (value.isString()) {
                checkText(where, value.asString(), errors);
            } else if (!isBinding(value)) {
                errors.add(where + " phải là chuỗi hoặc {\"path\": ...}");
                return;
            }
        }
        if (isBinding(value)) {
            checkBinding(where, key, value.path("path").asString(""), resultCount, errors);
        }
        if (ref.endsWith("BtbAction")) {
            checkAction(where, value, errors);
        }
    }

    private void checkArray(String where, JsonNode schema, JsonNode value, List<String> errors) {
        if (!value.isArray()) {
            errors.add(where + " phải là mảng");
            return;
        }
        if (schema.has("minItems") && value.size() < schema.path("minItems").asInt()) {
            errors.add(where + " ít hơn " + schema.path("minItems").asInt() + " phần tử");
        }
        if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) {
            errors.add(where + " nhiều hơn " + schema.path("maxItems").asInt() + " phần tử");
        }
        JsonNode items = schema.path("items");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                errors.add(where + " chỉ chứa chuỗi");
                continue;
            }
            if (schema.path("uniqueItems").asBoolean() && !seen.add(item.asString())) {
                errors.add(where + " có phần tử trùng");
            }
            if (items.has("enum") && !contains(items.path("enum"), item)) {
                errors.add(where + " có giá trị không hợp lệ: " + item.asString());
            }
            if (items.has("maxLength") && item.asString().length() > items.path("maxLength").asInt()) {
                errors.add(where + " có phần tử quá dài");
            }
            checkText(where, item.asString(), errors);
        }
    }

    private void checkText(String where, String text, List<String> errors) {
        if (text.length() > maxTextLength) {
            errors.add(where + " dài quá " + maxTextLength + " ký tự");
        }
        if (MONEY.matcher(text).find()) {
            errors.add(where + " không được ghi số tiền hoặc % vào literal");
        }
        if (text.contains("<") && text.contains(">")) {
            errors.add(where + " không được chứa HTML");
        }
    }

    private void checkBinding(String where, String key, String path, int resultCount, List<String> errors) {
        if (!BINDABLE.matcher(path).matches()) {
            errors.add(where + " bind path không cho phép: " + path);
            return;
        }
        if ("items".equals(key) && !"/products".equals(path)) {
            errors.add(where + " phải bind /products");
        }
        Matcher index = PRODUCT_INDEX.matcher(path);
        if (index.matches() && Integer.parseInt(index.group(1)) >= resultCount) {
            errors.add(where + " trỏ tới sản phẩm không tồn tại: " + path);
        }
    }

    private void checkAction(String where, JsonNode value, List<String> errors) {
        JsonNode event = value.path("event");
        String name = event.path("name").asString("");
        if (value.size() != 1 || !actions.containsKey(name)) {
            errors.add(where + " action không cho phép: " + name);
            return;
        }
        JsonNode contextSchema = actions.get(name);
        JsonNode context = event.path("context");
        for (JsonNode required : contextSchema.path("required")) {
            if (!context.has(required.asString())) {
                errors.add(where + " action " + name + " thiếu context." + required.asString());
            }
        }
        for (Map.Entry<String, JsonNode> entry : context.properties()) {
            JsonNode schema = contextSchema.path("properties").path(entry.getKey());
            if (schema.isMissingNode()) {
                errors.add(where + " action " + name + " có context lạ: " + entry.getKey());
            } else if (schema.has("enum") && !contains(schema.path("enum"), entry.getValue())) {
                errors.add(where + " action " + name + "." + entry.getKey() + " không hợp lệ");
            }
        }
    }

    private static boolean isBinding(JsonNode value) {
        return value != null && value.isObject() && value.size() == 1 && value.path("path").isString();
    }

    private static boolean contains(JsonNode options, JsonNode value) {
        for (JsonNode option : options) {
            if (option.equals(value) || option.isNumber() && value.isNumber()
                    && option.asDouble() == value.asDouble()) {
                return true;
            }
        }
        return false;
    }
}
