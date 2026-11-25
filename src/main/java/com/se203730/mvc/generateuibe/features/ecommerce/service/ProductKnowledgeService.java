package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductAttributeSearchRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.ProductAttributeSearchRepository.Match;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tra cứu catalog theo thông tin mô tả sản phẩm (xuất xứ, bảo quản, thành phần, dinh dưỡng, VSATTP).
 * Chỉ đọc sản phẩm PUBLISHED — cùng phạm vi Guest xem được — nên AI tool gọi được cho cả Guest và Customer.
 */
@Service
@Transactional(readOnly = true)
public class ProductKnowledgeService {
    private static final int MAX_RESULTS = 20;

    private final ProductAttributeSearchRepository repository;

    public ProductKnowledgeService(ProductAttributeSearchRepository repository) {
        this.repository = repository;
    }

    /**
     * Điều kiện tìm; mỗi trường là danh sách từ khóa (null/rỗng = không lọc). Ví dụ "bảo quản đông lạnh, xuất xứ
     * Úc" → {@code storage=["đông lạnh"], origin=["úc","australia"]}.
     */
    public record AttributeQuery(List<String> origin, List<String> storage, List<String> contains,
            List<String> nutrient, List<String> compliance, List<String> text) {

        public boolean isEmpty() {
            return blank(origin) && blank(storage) && blank(contains) && blank(nutrient) && blank(compliance)
                    && blank(text);
        }

        private static boolean blank(List<String> words) {
            return words == null || words.stream().allMatch(word -> word == null || word.isBlank());
        }
    }

    public List<Match> search(AttributeQuery query, int limit) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        return repository.search(query.origin(), query.storage(), query.contains(), query.nutrient(),
                query.compliance(), query.text(), Math.max(1, Math.min(limit, MAX_RESULTS)));
    }
}
