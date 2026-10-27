package com.gas.ecommerce.modules.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.ecommerce.modules.product.vo.CategoryVO;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryTreeServiceTests {
    @Test void buildsSortedMultipleLevelsAndHidesUnreachableNodes() {
        var tree = CategoryTreeService.build(List.of(
                c(3, 2, 0), c(2, 1, 0), c(1, 0, 2), c(4, 0, 1),
                c(5, 99, 0), c(6, 7, 0), c(7, 6, 0),
                new CategoryVO(8L, 0L, "hidden", 0, "inactive"), c(9, 8, 0)));
        assertEquals(List.of(4L, 1L), tree.stream().map(n -> n.id()).toList());
        assertEquals(3L, tree.get(1).children().getFirst().children().getFirst().id());
        assertTrue(tree.getFirst().children().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test void cacheHitSkipsCatalogAndFailureFallsBack() {
        var catalog = mock(ProductBrowseService.class);
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var service = new CategoryTreeService(catalog, redis, new ObjectMapper(), Duration.ofMinutes(5));
        when(values.get(CategoryTreeService.CACHE_KEY)).thenReturn("[]");
        assertTrue(service.tree().isEmpty());
        verifyNoInteractions(catalog);
        when(values.get(CategoryTreeService.CACHE_KEY)).thenThrow(new IllegalStateException("offline"));
        when(catalog.categories()).thenReturn(List.of(c(1, 0, 0)));
        assertEquals(1L, service.tree().getFirst().id());
        verify(values).set(eq(CategoryTreeService.CACHE_KEY), anyString(), eq(Duration.ofMinutes(5)));
        service.invalidate(new CategoryTreeService.CatalogChanged());
        verify(redis).delete(CategoryTreeService.CACHE_KEY);
    }

    private CategoryVO c(long id, long parent, int sort) {
        return new CategoryVO(id, parent, "category" + id, sort, "active");
    }
}
