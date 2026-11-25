package com.se203730.mvc.generateuibe.features.ai.service.tools;

import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerOrderDetailResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerOrderItemResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.CustomerOrderSummaryResponse;
import com.se203730.mvc.generateuibe.features.customer.service.CustomerOrderQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool "mua lại" cho widget ReorderList: đọc các đơn đã hoàn tất gần nhất của chính Customer đang đăng nhập
 * (qua CustomerOrderQueryService, đã kiểm tra quyền sở hữu), gom theo sản phẩm. Giá/tồn hiển thị luôn là giá
 * hiện hành từ ProductSearchTool.byIds; số lượng lần trước chỉ để gợi ý, khách vẫn tự chỉnh trong giỏ.
 */
@Component
@Slf4j
public class ReorderTool {
    private static final int RECENT_ORDERS = 5;
    private static final int MAX_PRODUCTS = 8;

    private final CustomerOrderQueryService orders;
    private final ProductSearchTool products;

    public ReorderTool(CustomerOrderQueryService orders, ProductSearchTool products) {
        this.orders = orders;
        this.products = products;
    }

    private record Usage(BigDecimal lastQuantity, int timesOrdered, Instant lastOrderedAt) {
    }

    public List<Map<String, Object>> reorderItems(UUID customerId, String locale) {
        if (customerId == null) {
            return List.of();
        }
        Map<UUID, Usage> usage = new LinkedHashMap<>();
        try {
            for (CustomerOrderSummaryResponse order : orders.list(customerId, "FINISHED", 0, RECENT_ORDERS)
                    .content()) {
                if (!"COMPLETED".equals(order.status())) {
                    continue;
                }
                CustomerOrderDetailResponse detail = orders.get(customerId, order.orderId());
                for (CustomerOrderItemResponse item : detail.items()) {
                    // Đơn mới nhất đến trước nên lần gặp đầu tiên là số lượng gần nhất.
                    usage.merge(item.productId(),
                            new Usage(item.quantity(), 1, order.createdAt()),
                            (old, next) -> new Usage(old.lastQuantity(), old.timesOrdered() + 1,
                                    old.lastOrderedAt()));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Reorder data unavailable: {}", exception.getClass().getSimpleName());
            return List.of();
        }
        List<UUID> ids = usage.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue().timesOrdered(),
                        left.getValue().timesOrdered()))
                .map(Map.Entry::getKey).limit(MAX_PRODUCTS).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : products.byIds(ids, locale)) {
            Usage used = usage.get(UUID.fromString((String) row.get("productId")));
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("lastQuantity", used.lastQuantity());
            item.put("timesOrdered", used.timesOrdered());
            item.put("lastOrderedAt", used.lastOrderedAt());
            rows.add(item);
        }
        return rows;
    }
}
