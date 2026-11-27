package com.se203730.mvc.generateuibe.features.ecommerce.service;

import com.se203730.mvc.generateuibe.features.customer.dto.response.ReviewImageResponse;
import com.se203730.mvc.generateuibe.features.customer.repository.ReviewRepository;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.EcommercePageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.repository.EcommerceExecutiveRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutiveOperationsServiceTest {
    private final EcommerceExecutiveRepository db = mock(EcommerceExecutiveRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ExecutiveOperationsService service = new ExecutiveOperationsService(
        db, null, null, null, null, null, null, null, reviews);

    @Test
    void addsAllImageUrlsWithOneBatchLookupAndKeepsReviewsWithoutImages() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Map<String, Object> one = new LinkedHashMap<>(), two = new LinkedHashMap<>();
        one.put("id", first);
        two.put("id", second);
        when(db.page(anyString(), anyString(), any(MapSqlParameterSource.class), eq(0), eq(20)))
            .thenReturn(new EcommercePageResponse<>(List.of(one, two), 2, 0, 20));
        when(reviews.imagesForReviews(List.of(first, second))).thenReturn(Map.of(first, List.of(
            new ReviewImageResponse(UUID.randomUUID(), "https://example.test/one.jpg", 0),
            new ReviewImageResponse(UUID.randomUUID(), "https://example.test/two.jpg", 1))));
        var result = service.reviews(null, null, 0, 20);
        assertEquals(List.of("https://example.test/one.jpg", "https://example.test/two.jpg"), result.items().get(0).get("imageUrls"));
        assertEquals(List.of(), result.items().get(1).get("imageUrls"));
        verify(reviews).imagesForReviews(List.of(first, second));
        verifyNoMoreInteractions(reviews);
    }

    @Test
    void emptyPageDoesNotQueryImages() {
        when(db.page(anyString(), anyString(), any(MapSqlParameterSource.class), eq(0), eq(20)))
            .thenReturn(new EcommercePageResponse<>(List.of(), 0, 0, 20));
        assertEquals(List.of(), service.reviews(null, null, 0, 20).items());
        verifyNoInteractions(reviews);
    }
}
