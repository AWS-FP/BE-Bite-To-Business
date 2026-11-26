package com.se203730.mvc.generateuibe.features.ai.service.context;

import com.se203730.mvc.generateuibe.features.ai.repository.UiProfileQueryRepository;
import com.se203730.mvc.generateuibe.features.ai.repository.UiProfileQueryRepository.ComponentUse;
import com.se203730.mvc.generateuibe.features.ai.repository.UiProfileQueryRepository.Count;
import com.se203730.mvc.generateuibe.features.ai.repository.UiProfileQueryRepository.ProductViews;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hồ sơ thói quen giao diện (long-term memory dạng có cấu trúc) dựng từ ui_interaction_events.
 *
 * <p>Tính bằng quy tắc thống kê, không dùng LLM, để tái lập và giải thích được:
 * <ul>
 *   <li>Sản phẩm quan tâm: điểm = lượt xem + 3 × thêm giỏ − 2 × back nhanh (+1 nếu ở lại trang lâu).</li>
 *   <li>Sản phẩm nên tránh đưa lên đầu: back nhanh ≥ 2 lần và chưa từng thêm giỏ.</li>
 *   <li>Component được dùng: tỷ lệ bấm/hiển thị ≥ 20% với ít nhất 3 lần hiển thị; bị bỏ qua: hiển thị ≥ 5 lần
 *       mà không lần nào bấm.</li>
 *   <li>Kiểu hiển thị ưa thích: so tỷ lệ bấm của ProductTable với ProductGrid.</li>
 * </ul>
 * Dưới {@link #MIN_EVENTS} sự kiện thì coi là chưa đủ bằng chứng và không cá nhân hóa. Hồ sơ chỉ ảnh hưởng
 * cách chọn bố cục/khối, không bao giờ thay đổi giá, tồn hay quyền.
 */
@Service
@Slf4j
public class UiProfileService {
    /** Số sự kiện tối thiểu trước khi dùng hồ sơ để cá nhân hóa. */
    public static final int MIN_EVENTS = 10;
    private static final Duration WINDOW = Duration.ofDays(60);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final long LONG_DWELL_MS = 20_000;

    private final UiProfileQueryRepository queries;
    /** Cache ngắn để không chạy lại 6 truy vấn tổng hợp mỗi lượt; key là user hoặc anon. */
    private final Map<String, CachedProfile> cache = new ConcurrentHashMap<>();

    private record CachedProfile(UiProfile profile, Instant at) {
    }

    /**
     * @param personalized     true khi đủ bằng chứng để cá nhân hóa
     * @param recentlyViewed   sản phẩm xem gần đây, mới nhất trước
     * @param interested       sản phẩm quan tâm nhất theo điểm
     * @param avoided          sản phẩm hay back nhanh, không nên đặt lên đầu
     * @param preferredView    "table" | "grid" | null
     */
    public record UiProfile(boolean personalized, long evidenceEvents, List<UUID> recentlyViewed,
            List<UUID> interested, List<UUID> avoided, String preferredView, List<String> likedComponents,
            List<String> ignoredComponents, List<String> frequentSearches, boolean usesAiPages) {

        public static UiProfile empty() {
            return new UiProfile(false, 0, List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
                    false);
        }

        /**
         * Bản tóm tắt đưa vào prompt (khối &lt;ui_profile&gt;): chỉ có tên sản phẩm, loại component, từ khóa —
         * không có ID nội bộ, giá hay dữ liệu cá nhân.
         */
        public Map<String, Object> toPrompt(Map<UUID, String> productNames) {
            Map<String, Object> prompt = new LinkedHashMap<>();
            prompt.put("evidenceEvents", evidenceEvents);
            prompt.put("preferredView", preferredView);
            prompt.put("likesComponents", likedComponents);
            prompt.put("ignoresComponents", ignoredComponents);
            prompt.put("interestedIn", interested.stream().map(productNames::get).filter(name -> name != null)
                    .toList());
            prompt.put("oftenLeavesQuickly", avoided.stream().map(productNames::get).filter(name -> name != null)
                    .toList());
            prompt.put("frequentSearches", frequentSearches);
            prompt.put("usesAiPages", usesAiPages);
            return prompt;
        }
    }

    public UiProfileService(UiProfileQueryRepository queries) {
        this.queries = queries;
    }

    /** Hồ sơ của Customer (userId) và/hoặc Guest (anonId). Lỗi truy vấn thì trả hồ sơ rỗng, không chặn request. */
    public UiProfile profile(UUID userId, String anonId) {
        if (userId == null && (anonId == null || anonId.isBlank())) {
            return UiProfile.empty();
        }
        String key = userId != null ? "u:" + userId + ":" + anonId : "a:" + anonId;
        CachedProfile cached = cache.get(key);
        if (cached != null && cached.at().isAfter(Instant.now().minus(CACHE_TTL))) {
            return cached.profile();
        }
        UiProfile profile;
        try {
            profile = compute(userId, anonId);
        } catch (RuntimeException exception) {
            log.warn("UI profile unavailable: {}", exception.getClass().getSimpleName());
            profile = UiProfile.empty();
        }
        if (cache.size() > 5_000) {
            cache.clear();
        }
        cache.put(key, new CachedProfile(profile, Instant.now()));
        return profile;
    }

    private UiProfile compute(UUID userId, String anonId) {
        Instant since = Instant.now().minus(WINDOW);
        long events = queries.eventCount(userId, anonId, since);
        if (events == 0) {
            return UiProfile.empty();
        }
        List<ProductViews> views = queries.productViews(userId, anonId, since, 20);
        Map<String, Long> quickBacks = toMap(queries.productTargets(userId, anonId, since, "quick_back"));
        Map<String, Long> carts = toMap(queries.productTargets(userId, anonId, since, "add_to_cart"));

        // Điểm quan tâm cho mỗi sản phẩm đã xem hoặc đã thêm giỏ.
        Map<UUID, Double> score = new HashMap<>();
        for (ProductViews view : views) {
            double value = view.views();
            if (view.averageDwellMs() != null && view.averageDwellMs() >= LONG_DWELL_MS) {
                value += 1;
            }
            score.merge(view.productId(), value, Double::sum);
        }
        carts.forEach((id, count) -> score.merge(UUID.fromString(id), 3.0 * count, Double::sum));
        quickBacks.forEach((id, count) -> score.merge(UUID.fromString(id), -2.0 * count, Double::sum));

        List<UUID> interested = score.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(6).map(Map.Entry::getKey).toList();
        List<UUID> avoided = quickBacks.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2 && !carts.containsKey(entry.getKey()))
                .map(entry -> UUID.fromString(entry.getKey())).limit(6).toList();
        List<UUID> recent = views.stream().map(ProductViews::productId).limit(8).toList();

        List<String> liked = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        double tableRate = -1;
        double gridRate = -1;
        for (ComponentUse use : queries.componentUse(userId, anonId, since)) {
            if (use.type() == null) {
                continue;
            }
            double rate = use.impressions() == 0 ? 0 : (double) use.actions() / use.impressions();
            if (use.impressions() >= 3 && rate >= 0.2) {
                liked.add(use.type());
            }
            if (use.impressions() >= 5 && use.actions() == 0) {
                ignored.add(use.type());
            }
            if ("ProductTable".equals(use.type()) && use.impressions() >= 2) {
                tableRate = rate;
            }
            if ("ProductGrid".equals(use.type()) && use.impressions() >= 2) {
                gridRate = rate;
            }
        }
        String preferredView = tableRate < 0 || gridRate < 0 ? null
                : tableRate > gridRate * 1.5 ? "table" : gridRate > tableRate * 1.5 ? "grid" : null;
        List<String> searches = queries.searches(userId, anonId, since).stream().map(Count::key).toList();
        boolean aiPages = queries.aiPageViews(userId, anonId, since) >= 2;
        return new UiProfile(events >= MIN_EVENTS, events, recent, interested, avoided, preferredView, liked,
                ignored, searches, aiPages);
    }

    private static Map<String, Long> toMap(List<Count> counts) {
        Map<String, Long> map = new LinkedHashMap<>();
        counts.forEach(count -> map.put(count.key().toLowerCase(), count.count()));
        return map;
    }
}
