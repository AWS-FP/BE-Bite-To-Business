package com.se203730.mvc.generateuibe.features.customer.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePageProductResponse;
import com.se203730.mvc.generateuibe.features.customer.dto.response.HomePagePromotionResponse;
import com.se203730.mvc.generateuibe.features.customer.repository.HomePageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomePageServiceTest {
    private final HomePageRepository repository = mock(HomePageRepository.class);
    private final HomePageService service = new HomePageService(repository);
    private final UUID id = UUID.randomUUID();

    @Test
    void loadsOnlyRequestedCampaignPageAndUsesExtraRowForHasNext() {
        var promotion = new HomePagePromotionResponse(id, "Khuyến mãi", "Promotion", null, null,
            Instant.EPOCH, Instant.EPOCH.plusSeconds(3600));
        var first = mock(HomePageProductResponse.class);
        var second = mock(HomePageProductResponse.class);
        var extra = mock(HomePageProductResponse.class);
        when(repository.activePromotion(id)).thenReturn(Optional.of(promotion));
        when(repository.promotionProducts(id,3,2L)).thenReturn(List.of(first,second,extra));

        var result = service.promotionProducts(id,1,2);
        assertEquals(promotion,result.promotion());
        assertEquals(List.of(first,second),result.items());
        assertTrue(result.hasNext());
        verify(repository).promotionProducts(id,3,2L);
    }

    @Test
    void inactiveOrUnknownCampaignDoesNotLoadProducts() {
        when(repository.activePromotion(id)).thenReturn(Optional.empty());
        var error = assertThrows(BusinessException.class, () -> service.promotionProducts(id,0,12));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,error.getErrorCode());
        verify(repository).activePromotion(id);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        assertEquals(ErrorCode.INVALID_REQUEST,
            assertThrows(BusinessException.class, () -> service.promotionProducts(id,0,49)).getErrorCode());
        verifyNoInteractions(repository);
    }
}
