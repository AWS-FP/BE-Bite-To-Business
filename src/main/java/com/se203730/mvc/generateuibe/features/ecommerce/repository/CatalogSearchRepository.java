package com.se203730.mvc.generateuibe.features.ecommerce.repository;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.ecommerce.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CatalogSearchRepository {
    private final EntityManager em;

    public Page<Product> search(String keyword, UUID brand, Set<UUID> categories,
                                String sort, PageRequest page) {
        String from = " FROM catalog_search.ranked(:q, CAST(:brand AS uuid), CAST(:categories AS uuid[])) r"
            + " JOIN public.products p ON p.id=r.product_id";
        String categoryArray = categories == null ? null : categories.stream().map(UUID::toString)
            .collect(Collectors.joining(",", "{", "}"));
        String order = switch (sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT)) {
            case "", "relevance" -> "r.relevance DESC, lower(p.name_vi)";
            case "name_asc" -> "lower(p.name_vi)";
            case "name_desc" -> "lower(p.name_vi) DESC";
            case "newest" -> "p.created_at DESC";
            case "oldest" -> "p.created_at";
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        Query rows = em.createNativeQuery("SELECT p.*" + from + " ORDER BY " + order +
            ", p.id LIMIT :limit OFFSET :offset", Product.class);
        Query count = em.createNativeQuery("SELECT count(*)" + from);
        for (Query query : List.of(rows, count)) {
            query.setParameter("q", keyword);
            query.setParameter("brand", brand == null ? null : brand.toString());
            query.setParameter("categories", categoryArray);
        }
        rows.setParameter("limit", page.getPageSize()).setParameter("offset", page.getOffset());
        @SuppressWarnings("unchecked")
        List<Product> content = rows.getResultList();
        return PageableExecutionUtils.getPage(content, page, () -> ((Number) count.getSingleResult()).longValue());
    }
}
