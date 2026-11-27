package com.se203730.mvc.generateuibe.features.ai.service.orchestration;

import com.se203730.mvc.generateuibe.features.ai.service.tools.ProductSearchTool;
import com.se203730.mvc.generateuibe.features.customer.service.HomePageService;
import com.se203730.mvc.generateuibe.features.customer.service.ReviewService;
import com.se203730.mvc.generateuibe.features.ecommerce.dto.response.ProductPageResponse;
import com.se203730.mvc.generateuibe.features.ecommerce.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductSearchTermsTest {
    @Test
    void fruitClassifierVariantsReachTheSameCatalogSearchWithoutDroppingColourOrVariety() {
        var catalog = mock(ProductService.class);
        var home = mock(HomePageService.class);
        when(home.categories()).thenReturn(List.of());
        when(catalog.getAll(anyString(), isNull(), isNull(), anyString(), anyInt(), anyInt()))
                .thenReturn(new ProductPageResponse(List.of(), 0, 12, 0, 0, false, false));
        var tool = new ProductSearchTool(catalog, mock(ProductPricingService.class),
                mock(ProductKnowledgeService.class), mock(ReviewService.class), home);
        for (String term : List.of("trái cam vàng", "quả cam vàng", "cam vàng")) {
            assertEquals("cam vàng", tool.search(List.of(term), "relevance", 12, "vi").query());
        }
        verify(catalog, times(3)).getAll("cam vàng", null, null, "relevance", 0, 12);
        assertEquals("cam vàng navel nhập khẩu", ProductSearchTool.searchTerm("Trái cam vàng navel nhập khẩu"));
        assertEquals("trái cây", ProductSearchTool.searchTerm("trái cây"));
        assertEquals("trái cây sấy", ProductSearchTool.searchTerm("trái cây sấy"));
        assertEquals("củ cải trắng", ProductSearchTool.searchTerm("củ cải trắng"));
    }
}
