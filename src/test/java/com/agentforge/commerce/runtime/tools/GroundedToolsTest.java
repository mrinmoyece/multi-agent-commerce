package com.agentforge.commerce.runtime.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.agentforge.commerce.runtime.contract.ResultStatus;
import com.agentforge.commerce.runtime.contract.SourceKind;
import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.contract.ToolResult;
import com.agentforge.commerce.runtime.tools.fixture.FixtureInventoryTool;
import com.agentforge.commerce.runtime.tools.fixture.FixturePricingTool;
import com.agentforge.commerce.runtime.tools.fixture.FixtureSentimentTool;
import com.agentforge.commerce.runtime.tools.live.InventoryBackend;
import com.agentforge.commerce.runtime.tools.live.LiveInventoryTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for grounded tool adapters: deterministic fixtures (labelled as
 * non-authoritative) and the live adapter's fail-closed behaviour.
 */
@DisplayName("Grounded tools")
class GroundedToolsTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ToolRequest request() {
        return ToolRequest.of("t", "R1", NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("Fixture inventory tool returns grounded, non-authoritative data with citations")
    void fixtureInventory() {
        FixtureInventoryTool tool = new FixtureInventoryTool(mapper, clock);
        ToolResult<InventorySnapshot> result = tool.fetch(request());
        assertThat(result.status()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(result.isSimulatedOrFixture()).isTrue();
        assertThat(result.isGrounded()).isTrue();
        assertThat(result.payload().items()).isNotEmpty();
        assertThat(result.source().kind()).isEqualTo(SourceKind.FIXTURE);
        assertThat(tool.name()).isEqualTo("inventory.fetch");
    }

    @Test
    @DisplayName("Fixture pricing tool returns grounded observations")
    void fixturePricing() {
        FixturePricingTool tool = new FixturePricingTool(mapper, clock);
        ToolResult<PricingObservations> result = tool.fetch(request());
        assertThat(result.payload().observations()).isNotEmpty();
        assertThat(result.isGrounded()).isTrue();
        assertThat(result.payload().observations().get(0).isUndercut()).isIn(true, false);
    }

    @Test
    @DisplayName("Fixture sentiment tool returns grounded signals")
    void fixtureSentiment() {
        FixtureSentimentTool tool = new FixtureSentimentTool(mapper, clock);
        ToolResult<SentimentObservations> result = tool.fetch(request());
        assertThat(result.payload().signals()).isNotEmpty();
        assertThat(result.isGrounded()).isTrue();
        assertThat(tool.name()).isEqualTo("sentiment.fetch");
    }

    @Test
    @DisplayName("Inventory item and observation domain helpers")
    void domainHelpers() {
        InventorySnapshot.Item critical = new InventorySnapshot.Item("SKU-1", "n", "c", 1, 2, "CRITICAL");
        assertThat(critical.isCritical()).isTrue();
        InventorySnapshot.Item overstock = new InventorySnapshot.Item("SKU-2", "n", "c", 1, 200, "OVER");
        assertThat(overstock.isOverstocked()).isTrue();
        SentimentObservations.Signal signal = new SentimentObservations.Signal("t", 1, 5, 10, 6);
        assertThat(signal.isNegativeLeaning()).isTrue();
    }

    @Test
    @DisplayName("ToolPayloads measures size and enforces the ceiling")
    void toolPayloads() {
        assertThat(ToolPayloads.measure(mapper, null, 1024)).isZero();
        assertThat(ToolPayloads.measure(mapper, "hello", 1024)).isGreaterThan(0);
        assertThatThrownBy(() -> ToolPayloads.measure(mapper, "x".repeat(100), 4))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Live inventory tool fails closed when the backend is unconfigured")
    void liveUnconfigured() {
        @SuppressWarnings("unchecked")
        ObjectProvider<InventoryBackend> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(null);
        LiveInventoryTool tool = new LiveInventoryTool(provider, mapper, clock);
        ToolResult<InventorySnapshot> result = tool.fetch(request());
        assertThat(result.status()).isEqualTo(ResultStatus.ERROR);
        assertThat(tool.source().kind()).isEqualTo(SourceKind.LIVE);
    }

    @Test
    @DisplayName("Live inventory tool returns authoritative data from a configured backend")
    void liveConfigured() {
        InventoryBackend backend = mock(InventoryBackend.class);
        given(backend.systemOfRecord()).willReturn("WMS-1");
        given(backend.fetchSnapshot(org.mockito.ArgumentMatchers.any()))
                .willReturn(new InventorySnapshot("R1", NOW, java.util.List.of(
                        new InventorySnapshot.Item("SKU-1", "n", "c", 10, 5, "OK"))));
        @SuppressWarnings("unchecked")
        ObjectProvider<InventoryBackend> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(backend);
        LiveInventoryTool tool = new LiveInventoryTool(provider, mapper, clock);
        ToolResult<InventorySnapshot> result = tool.fetch(request());
        assertThat(result.status()).isEqualTo(ResultStatus.OK);
        assertThat(result.isSimulatedOrFixture()).isFalse();
    }

    @Test
    @DisplayName("Live inventory tool fails closed past the deadline")
    void liveDeadline() {
        InventoryBackend backend = mock(InventoryBackend.class);
        given(backend.systemOfRecord()).willReturn("WMS-1");
        @SuppressWarnings("unchecked")
        ObjectProvider<InventoryBackend> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(backend);
        LiveInventoryTool tool = new LiveInventoryTool(provider, mapper, clock);
        ToolResult<InventorySnapshot> result = tool.fetch(ToolRequest.of("t", "R1", NOW.minusSeconds(1)));
        assertThat(result.status()).isEqualTo(ResultStatus.ERROR);
    }
}
