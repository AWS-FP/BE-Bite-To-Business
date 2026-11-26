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
    /** Danh sách sản phẩm backend có thể ghi; component sản phẩm chỉ được bind các path này. */
    public static final Set<String> PRODUCT_LISTS = Set.of("/products", "/favorites", "/home/featured",
            "/home/bestSelling", "/home/deepDiscount", "/home/topRated", "/home/flashSale", "/recentlyViewed",
            "/forYou", "/reorder");
    /** Danh sách không phải sản phẩm và component duy nhất được dùng nó. */
    private static final Map<String, String> OTHER_LISTS = Map.of("/categories", "CategoryGrid", "/home/combos",
            "ComboList", "/home/banners", "BannerCarousel", "/home/promotions", "PromotionList");
    /** Component chỉ được bind đúng một danh sách sản phẩm riêng. */
    private static final Map<String, String> DEDICATED_PRODUCT_LIST = Map.of("ReorderList", "/reorder");
    private static final Set<String> LIST_PATHS = new HashSet<>();
    static {
        LIST_PATHS.addAll(PRODUCT_LISTS);
        LIST_PATHS.addAll(OTHER_LISTS.keySet());
    }
    private static final Pattern BINDABLE = Pattern.compile(
            "^/(query/(text|sort)|resultCount|products|favorites|categories|recentlyViewed|forYou|reorder"
                    + "|home/(featured|bestSelling|deepDiscount|topRated|flashSale|combos|banners|promotions))"
                    + "(/\\d{1,2})?$");
    private static final Pattern LIST_INDEX = Pattern.compile(
            "^(/products|/favorites|/recentlyViewed|/forYou|/reorder"
                    + "|/home/(?:featured|bestSelling|deepDiscount|topRated|flashSale))/(\\d+)$");
    private static final Set<String> PAGE_ONLY = Set.of("PageHeader", "Section");
    private static final Set<String> PRODUCT_COMPONENTS = Set.of("ProductGrid", "ProductTable", "ProductCarousel");
    private static final int CHAT_MAX_COMPONENTS = 25;
    private static final int CHAT_MAX_DEPTH = 6;
    private static final int PAGE_MAX_SECTIONS = 6;
    private static final Pattern MONEY = Pattern.compile("\\d[\\d.,]*\\s*(đ|vnd|₫|%)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");

    private record ComponentSpec(Map<String, JsonNode> props, Set<String> required) {
    }

    private final Map<String, ComponentSpec> components = new HashMap<>();
    private final Map<String, JsonNode> actions = new LinkedHashMap<>();
    private final Pattern surfaceId;
    private final int maxComponents;
    private final int maxDepth;
    private final int maxTextLength;

    /**
     * Đọc catalog JSON Schema một lần: gom thuộc tính cho phép/bắt buộc của từng component (gộp các phần allOf)
     * và context cho phép của từng action trong $defs.BtbAction; giới hạn lấy từ ai/catalog.json.
     */
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
    /** Chế độ chat: khối nhỏ trong khung trợ lý. */
    public List<String> validate(JsonNode message, int resultCount) {
        return validate(message, Map.of("/products", resultCount), false);
    }

    /**
     * Trả danh sách lỗi; rỗng nghĩa là message hợp lệ.
     *
     * @param lists số phần tử của từng danh sách backend đã ghi vào data model (path → số phần tử)
     * @param page  true với chế độ dựng cả trang (giới hạn số component/độ sâu lớn hơn)
     */
    public List<String> validate(JsonNode message, Map<String, Integer> lists, boolean page) {
        int maxComponents = page ? this.maxComponents : CHAT_MAX_COMPONENTS;
        int maxDepth = page ? this.maxDepth : CHAT_MAX_DEPTH;
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
            checkComponent(component, byId, parent, lists, errors);
        }
        checkTree(byId, parent, maxDepth, errors);
        checkPageRules(list, page, errors);
        return errors;
    }

    /** Quy tắc bố cục: chat không dùng khung trang; trang có tối đa 1 PageHeader, 6 Section, 1 Section highlight. */
    private void checkPageRules(JsonNode list, boolean page, List<String> errors) {
        int headers = 0;
        int sections = 0;
        int highlights = 0;
        for (JsonNode component : list) {
            String type = component.path("component").asString("");
            if (!page && PAGE_ONLY.contains(type)) {
                errors.add(type + " chỉ dùng khi dựng cả trang");
            }
            if ("PageHeader".equals(type)) {
                headers++;
            }
            if ("Section".equals(type)) {
                sections++;
                if ("highlight".equals(component.path("variant").asString(""))) {
                    highlights++;
                }
            }
        }
        if (headers > 1) {
            errors.add("Chỉ được 1 PageHeader");
        }
        if (sections > PAGE_MAX_SECTIONS) {
            errors.add("Tối đa " + PAGE_MAX_SECTIONS + " Section");
        }
        if (highlights > 1) {
            errors.add("Chỉ được 1 Section highlight");
        }
    }

    /** Kiểm một component: có trong catalog, đủ thuộc tính bắt buộc, không có thuộc tính lạ, child tham chiếu đúng. */
    private void checkComponent(JsonNode component, Map<String, JsonNode> byId, Map<String, String> parent,
            Map<String, Integer> lists, List<String> errors) {
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
                if ("items".equals(key)) {
                    checkItemsSource(id, type, value, errors);
                }
                checkValue(id + "." + key, key, spec.props().get(key), value, lists, errors);
            }
        }
    }

    /** Component danh sách chỉ được bind loại dữ liệu đúng của nó (sản phẩm, danh mục, combo, banner, khuyến mãi). */
    private void checkItemsSource(String id, String type, JsonNode value, List<String> errors) {
        String path = value.path("path").asString("");
        boolean ok = PRODUCT_COMPONENTS.contains(type) ? PRODUCT_LISTS.contains(path)
                : DEDICATED_PRODUCT_LIST.containsKey(type) ? DEDICATED_PRODUCT_LIST.get(type).equals(path)
                : type.equals(OTHER_LISTS.get(path));
        if (!ok) {
            errors.add(id + ": " + type + " không được bind " + path);
        }
    }

    /** Ghi quan hệ cha–con; id con phải tồn tại và chỉ có một cha (cây, không phải đồ thị). */
    private void linkChild(String id, String child, Map<String, JsonNode> byId, Map<String, String> parent,
            List<String> errors) {
        if (!byId.containsKey(child)) {
            errors.add(id + ": tham chiếu id không tồn tại " + child);
        } else if (parent.put(child, id) != null) {
            errors.add(child + ": có nhiều hơn một component cha");
        }
    }

    /** Mọi component phải nối về root, không vòng lặp, không sâu quá giới hạn của chế độ. */
    private void checkTree(Map<String, JsonNode> byId, Map<String, String> parent, int maxDepth,
            List<String> errors) {
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

    /**
     * Kiểm giá trị một thuộc tính theo schema: const/enum/kiểu/khoảng, DataBinding, DynamicString (literal hoặc
     * path), action allowlist. Literal chữ còn bị kiểm không chứa số tiền/% và HTML.
     */
    private void checkValue(String where, String key, JsonNode schema, JsonNode value, Map<String, Integer> lists,
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
            checkBinding(where, key, value.path("path").asString(""), lists, errors);
        }
        if (ref.endsWith("BtbAction")) {
            checkAction(where, value, errors);
        }
    }

    /** Mảng chuỗi (cột bảng, lựa chọn, gợi ý): số phần tử, không trùng, giá trị thuộc enum, độ dài. */
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

    /** Literal do model viết: giới hạn độ dài, cấm số tiền/% (phải bind data model) và cấm thẻ HTML. */
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

    /** Path bind phải nằm trong danh sách cho phép, phần tử /list/N phải tồn tại, danh sách được bind không rỗng. */
    private void checkBinding(String where, String key, String path, Map<String, Integer> lists, List<String> errors) {
        if (!BINDABLE.matcher(path).matches()) {
            errors.add(where + " bind path không cho phép: " + path);
            return;
        }
        Matcher index = LIST_INDEX.matcher(path);
        if (index.matches()) {
            Integer count = lists.get(index.group(1));
            if (count == null || Integer.parseInt(index.group(2)) >= count) {
                errors.add(where + " trỏ tới phần tử không tồn tại: " + path);
            }
            return;
        }
        if (LIST_PATHS.contains(path) && lists.getOrDefault(path, 0) == 0) {
            errors.add(where + " bind danh sách rỗng hoặc không có dữ liệu: " + path);
        }
    }

    /** Action phải là event có tên trong allowlist, đủ context bắt buộc, không có context lạ, enum hợp lệ. */
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
