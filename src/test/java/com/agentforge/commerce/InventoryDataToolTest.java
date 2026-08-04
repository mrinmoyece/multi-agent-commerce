package com.agentforge.commerce;

import com.agentforge.commerce.tool.InventoryDataTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InventoryDataTool synthetic data and fallback.
 * No external calls — validates data shape and business invariants.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryDataTool — unit tests")
class InventoryDataToolTest {

    @Spy
    InventoryDataTool inventoryDataTool = new InventoryDataTool();

    @Test
    @DisplayName("getCurrentStockLevels returns 20 SKUs with all required fields populated")
    void currentStockLevels_returnsFullDataset() {
        List<InventoryDataTool.StockLevel> levels = inventoryDataTool.getCurrentStockLevels();

        assertThat(levels).hasSize(20);
        levels.forEach(s -> {
            assertThat(s.getSku()).isNotBlank();
            assertThat(s.getProductName()).isNotBlank();
            assertThat(s.getCategory()).isNotBlank();
            assertThat(s.getCurrentQty()).isGreaterThanOrEqualTo(0);
            assertThat(s.getDaysOfStockRemaining()).isGreaterThanOrEqualTo(0);
            assertThat(s.getUnitCost()).isNotNull();
            assertThat(s.getStatus()).isNotNull();
        });
    }

    @Test
    @DisplayName("CRITICAL items have fewer than 7 days of stock")
    void criticalItems_haveUnder7Days() {
        List<InventoryDataTool.StockLevel> levels = inventoryDataTool.getCurrentStockLevels();

        levels.stream()
              .filter(s -> s.getStatus() == InventoryDataTool.StockLevel.StockStatus.CRITICAL)
              .forEach(s -> assertThat(s.getDaysOfStockRemaining()).isLessThan(7));
    }

    @Test
    @DisplayName("OVERSTOCK items have more than 90 days of stock")
    void overstockItems_haveOver90Days() {
        List<InventoryDataTool.StockLevel> levels = inventoryDataTool.getCurrentStockLevels();

        levels.stream()
              .filter(s -> s.getStatus() == InventoryDataTool.StockLevel.StockStatus.OVERSTOCK)
              .forEach(s -> assertThat(s.getDaysOfStockRemaining()).isGreaterThan(90));
    }

    @Test
    @DisplayName("Fallback method returns data when called directly")
    void fallbackMethod_returnsData() {
        List<InventoryDataTool.StockLevel> fallback =
                inventoryDataTool.stockLevelsFallback(new RuntimeException("WMS down"));

        assertThat(fallback).isNotEmpty();
        assertThat(fallback).allSatisfy(s -> assertThat(s.getSku()).isNotBlank());
    }

    @Test
    @DisplayName("Data is deterministic across calls (seeded random)")
    void deterministicData_sameAcrossCalls() {
        List<InventoryDataTool.StockLevel> first  = inventoryDataTool.getCurrentStockLevels();
        List<InventoryDataTool.StockLevel> second = inventoryDataTool.getCurrentStockLevels();

        assertThat(first).hasSameSizeAs(second);
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i).getSku()).isEqualTo(second.get(i).getSku());
            assertThat(first.get(i).getCurrentQty()).isEqualTo(second.get(i).getCurrentQty());
        }
    }
}
