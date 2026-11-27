package com.se203730.mvc.generateuibe.features.customer.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutStockServiceTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), combo = UUID.randomUUID(), second = UUID.randomUUID();

    private BigDecimal n(String value) { return new BigDecimal(value); }
    private CheckoutStockService.Line line(UUID id, String qty) { return new CheckoutStockService.Line(id, n(qty)); }
    private CheckoutStockService.Component component(UUID id, String factor, String available, boolean bundle) {
        return new CheckoutStockService.Component(id, n(factor), n(available), bundle);
    }

    @Test
    void bottleneckAndWholeCombos() {
        var result = CheckoutStockService.calculateAvailability(List.of(line(combo, "2")), Map.of(combo, List.of(
                component(a, "10", "100", true), component(b, "10", "29", true))));
        assertEquals(n("2"), result.get(combo));
    }

    @Test
    void standaloneAndTwoCombosShareStock() {
        var result = CheckoutStockService.calculateAvailability(List.of(line(combo, "2"), line(second, "1"), line(a, "5")), Map.of(
                combo, List.of(component(a, "10", "30", true)), second, List.of(component(a, "10", "30", true)),
                a, List.of(component(a, "1", "30", false))));
        assertEquals(n("1"), result.get(combo));
        assertEquals(n("0"), result.get(second));
        assertEquals(n("0"), result.get(a));
    }

    @Test
    void missingRecipeCannotBeOrdered() {
        assertEquals(BigDecimal.ZERO, CheckoutStockService.calculateAvailability(List.of(line(combo, "1")), Map.of()).get(combo));
    }

    @Test
    void decimalIngredientAndExactStock() {
        var result = CheckoutStockService.calculateAvailability(List.of(line(combo, "4")), Map.of(combo, List.of(
                component(a, "0.25", "1.0", true))));
        assertEquals(n("4"), result.get(combo));
    }
}
