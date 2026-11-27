package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tìm sản phẩm đang bán theo thuộc tính mô tả trong DB (không chỉ theo tên): xuất xứ, cách bảo quản/sơ chế,
 * thành phần/nguyên liệu, dinh dưỡng, chứng nhận VSATTP. So khớp không dấu bằng {@code catalog_search.fold_v1}
 * (V50), mỗi nhóm điều kiện là danh sách từ (khớp một từ là đủ), các nhóm được AND với nhau.
 *
 * <p>Nguồn văn bản của từng nhóm:
 * <ul>
 *   <li>origin: products.origin + product_details.origin_content_vi/en</li>
 *   <li>storage: product_details.kitchen_storage_content_vi/en</li>
 *   <li>contains: tên, mô tả, thông số kỹ thuật, danh mục (con và cha)</li>
 *   <li>nutrient: tên thành phần dinh dưỡng VI/EN trong product_nutrition_facts</li>
 *   <li>compliance: product_details.compliance_vat_content_vi/en</li>
 *   <li>text: bất kỳ trường nào ở trên + thương hiệu</li>
 * </ul>
 * Mọi giá trị là tham số; không ghép chuỗi từ input vào SQL.
 */
@Repository
public class ProductAttributeSearchRepository {
    private static final String SQL = """
            WITH docs AS MATERIALIZED (
                SELECT p.id,
                       lower(p.name_vi) AS name_sort,
                       CASE WHEN EXISTS (SELECT 1 FROM public.product_detail_evidence e
                           WHERE e.product_id=p.id AND e.sources @> '[{"type":"origin_label"}]'::jsonb)
                           THEN coalesce(p.origin, '') || ' ' || coalesce(d.origin_content_vi, '') || ' '
                           || coalesce(d.origin_content_en, '') ELSE '' END AS origin_txt,
                       coalesce(d.kitchen_storage_content_vi, '') || ' '
                           || coalesce(d.kitchen_storage_content_en, '') AS storage_txt,
                       coalesce(p.name_vi, '') || ' ' || coalesce(p.name_en, '') || ' '
                           || coalesce(p.description_vi, '') || ' ' || coalesce(p.description_en, '') || ' '
                           || coalesce(d.technical_content_vi, '') || ' ' || coalesce(d.technical_content_en, '')
                           || ' ' || coalesce(c.name_vi, '') || ' ' || coalesce(c.name_en, '') || ' '
                           || coalesce(pc.name_vi, '') || ' ' || coalesce(pc.name_en, '') AS content_txt,
                       coalesce((SELECT string_agg(n.nutrient || ' ' || n.nutrient_en, ' ')
                           FROM public.product_nutrition_facts n WHERE n.product_id = p.id
                             AND EXISTS (SELECT 1 FROM public.product_detail_evidence e
                                         WHERE e.product_id=p.id AND e.nutrition_verified)), '') AS nutrient_txt,
                       CASE WHEN EXISTS (SELECT 1 FROM public.product_detail_evidence e
                           WHERE e.product_id=p.id AND e.sources @> '[{"type":"certificate"}]'::jsonb)
                           THEN coalesce(d.compliance_vat_content_vi, '') || ' '
                           || coalesce(d.compliance_vat_content_en, '') ELSE '' END AS compliance_txt,
                       coalesce(b.name, '') AS brand_txt
                FROM public.products p
                LEFT JOIN public.product_details d ON d.product_id = p.id
                LEFT JOIN public.categories c ON c.id = p.category_id
                LEFT JOIN public.categories pc ON pc.id = c.parent_id
                LEFT JOIN public.brands b ON b.id = p.brand_id
                WHERE p.status = 'PUBLISHED'
            ), keys AS MATERIALIZED (
                -- Mỗi nhóm có hai khóa bọc dấu cách để khớp nguyên từ: _f đã bỏ dấu, _r giữ dấu (chữ thường, NFC).
                SELECT id, name_sort,
                       %7$s
                FROM docs
            ), matched AS (
                SELECT keys.*,
                       %1$s AS m_origin, %2$s AS m_storage, %3$s AS m_contains, %4$s AS m_nutrient,
                       %5$s AS m_compliance, %6$s AS m_text
                FROM keys
            )
            SELECT id, m_origin, m_storage, m_contains, m_nutrient, m_compliance, m_text
            FROM matched
            WHERE (CAST(:origin AS text[]) IS NULL OR m_origin)
              AND (CAST(:storage AS text[]) IS NULL OR m_storage)
              AND (CAST(:contains AS text[]) IS NULL OR m_contains)
              AND (CAST(:nutrient AS text[]) IS NULL OR m_nutrient)
              AND (CAST(:compliance AS text[]) IS NULL OR m_compliance)
              AND (CAST(:text AS text[]) IS NULL OR m_text)
            ORDER BY m_contains DESC, name_sort, id
            LIMIT :limit
            """.formatted(
            any("origin", "origin"),
            any("storage", "storage"),
            any("contains", "content"),
            any("nutrient", "nutrient"),
            any("compliance", "compliance"),
            any("text", "all"),
            keys());

    private final NamedParameterJdbcTemplate jdbc;

    public ProductAttributeSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Balanced catalog sample for goal-based recommendations, favoring products with documented facts. */
    public List<UUID> recommendationCandidates(int limit) {
        return jdbc.query("""
                WITH ranked AS (
                    SELECT p.id,
                           row_number() OVER (
                               PARTITION BY coalesce(c.parent_id, c.id)
                               ORDER BY EXISTS (SELECT 1 FROM public.product_detail_evidence e
                                                WHERE e.product_id=p.id AND e.nutrition_verified) DESC,
                                        EXISTS (SELECT 1 FROM public.product_nutrition_facts n
                                                WHERE n.product_id = p.id) DESC,
                                        (coalesce(p.description_vi, '') <> '') DESC, p.id
                           ) AS position
                    FROM public.products p
                    LEFT JOIN public.categories c ON c.id = p.category_id
                    WHERE p.status = 'PUBLISHED'
                )
                SELECT id FROM ranked ORDER BY position, id LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), (rs, row) -> rs.getObject("id", UUID.class));
    }

    /** Kết quả: ID sản phẩm và nhóm điều kiện nào đã khớp (để giải thích vì sao sản phẩm xuất hiện). */
    public record Match(UUID productId, boolean origin, boolean storage, boolean contains, boolean nutrient,
            boolean compliance, boolean text) {
    }

    public record NutritionRow(UUID productId, String nutrient, String nutrientEn, String amount, String basis) {}
    public record Evidence(UUID productId, boolean nutritionVerified, String notesVi, String notesEn, String sources) {}

    public List<NutritionRow> verifiedNutrition() {
        return jdbc.query("""
                SELECT n.product_id,n.nutrient,n.nutrient_en,n.amount,d.nutrition_basis
                FROM public.product_nutrition_facts n
                JOIN public.products p ON p.id=n.product_id AND p.status='PUBLISHED'
                JOIN public.product_details d ON d.product_id=p.id
                JOIN public.product_detail_evidence e ON e.product_id=p.id AND e.nutrition_verified
                """, new MapSqlParameterSource(), (rs, row) -> new NutritionRow(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    public List<Evidence> evidence(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("SELECT product_id,nutrition_verified,notes_vi,notes_en,sources::text FROM public.product_detail_evidence WHERE product_id IN (:ids)",
                new MapSqlParameterSource("ids", ids), (rs, row) -> new Evidence(rs.getObject(1, UUID.class),
                rs.getBoolean(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    /** Mỗi tham số là danh sách từ (null = không lọc theo nhóm đó). */
    public List<Match> search(List<String> origin, List<String> storage, List<String> contains,
            List<String> nutrient, List<String> compliance, List<String> text, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("origin", array(origin))
                .addValue("storage", array(storage))
                .addValue("contains", array(contains))
                .addValue("nutrient", array(nutrient))
                .addValue("compliance", array(compliance))
                .addValue("text", array(text))
                .addValue("limit", limit);
        return jdbc.query(SQL, params, (rs, row) -> new Match(rs.getObject("id", UUID.class),
                rs.getBoolean("m_origin"), rs.getBoolean("m_storage"), rs.getBoolean("m_contains"),
                rs.getBoolean("m_nutrient"), rs.getBoolean("m_compliance"), rs.getBoolean("m_text")));
    }

    /**
     * Chuẩn hóa giữ dấu, không phụ thuộc locale của DB: tách dấu (NFD) để chữ gốc là ASCII rồi mới lower()
     * (locale C không lower được "Ú"), đổi "Đ" → "đ" (không tách được), và chỉ coi khoảng trắng/dấu câu là ranh
     * giới từ ([:alnum:] ở locale C không nhận chữ có dấu nên không dùng).
     */
    private static String raw(String expression) {
        return "btrim(regexp_replace(translate(lower(normalize(" + expression + ", NFD)), 'Đ', 'đ'),"
                + " '[[:space:][:punct:]]+', ' ', 'g'))";
    }

    /** Cột khóa _f (bỏ dấu) và _r (giữ dấu) cho từng nhóm, cộng nhóm "all" gộp mọi trường cho điều kiện text. */
    private static String keys() {
        StringBuilder columns = new StringBuilder();
        String all = "origin_txt || ' ' || storage_txt || ' ' || content_txt || ' ' || nutrient_txt || ' ' "
                + "|| compliance_txt || ' ' || brand_txt";
        String[][] groups = {{"origin", "origin_txt"}, {"storage", "storage_txt"}, {"content", "content_txt"},
                {"nutrient", "nutrient_txt"}, {"compliance", "compliance_txt"}, {"all", all}};
        for (String[] group : groups) {
            if (columns.length() > 0) {
                columns.append(",\n                       ");
            }
            columns.append("' ' || catalog_search.fold_v1(").append(group[1]).append(") || ' ' AS ")
                    .append(group[0]).append("_f, ' ' || ").append(raw(group[1])).append(" || ' ' AS ")
                    .append(group[0]).append("_r");
        }
        return columns.toString();
    }

    /**
     * Một nhóm khớp khi văn bản chứa ít nhất một cụm từ của nhóm như <b>nguyên từ</b> (không khớp giữa chữ).
     * Cụm có dấu ("úc") so với văn bản giữ dấu để không nhầm với "ức", "trực"; cụm không dấu ("uc", "haccp")
     * so với văn bản đã bỏ dấu. Chuẩn hóa giữ dấu ra rỗng (locale không nhận chữ Việt) thì quay về bỏ dấu.
     */
    private static String any(String param, String group) {
        String rawTerm = raw("t");
        return "coalesce((SELECT bool_or(CASE"
                + " WHEN " + rawTerm + " <> '' AND catalog_search.fold_v1(t) <> " + rawTerm
                + " THEN " + group + "_r LIKE '% ' || " + rawTerm + " || ' %'"
                + " ELSE " + group + "_f LIKE '% ' || catalog_search.fold_v1(t) || ' %' END)"
                + " FROM unnest(CAST(:" + param + " AS text[])) AS t"
                + " WHERE catalog_search.fold_v1(t) <> ''), false)";
    }

    /** Postgres text[] literal; null khi không có từ nào để nhóm đó không lọc. */
    private static String array(List<String> words) {
        if (words == null) {
            return null;
        }
        List<String> clean = words.stream().filter(word -> word != null && !word.isBlank())
                .map(word -> word.trim().length() > 60 ? word.trim().substring(0, 60) : word.trim())
                .distinct().limit(6).toList();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.stream().map(word -> "\"" + word.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce((left, right) -> left + "," + right).map(value -> "{" + value + "}").orElse(null);
    }
}
