package com.agentforge.commerce.runtime.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the provider-neutral runtime contract value types: evidence,
 * citations, confidence, freshness, source identity, tool results and envelopes.
 */
@DisplayName("Runtime contract value types")
class RuntimeContractTest {

    private static final Instant NOW = Instant.parse("2026-06-19T09:00:00Z");

    private static SourceIdentity liveSource() {
        return new SourceIdentity("wms", "Warehouse", SourceKind.LIVE, "WMS");
    }

    private static Evidence citedEvidence(SourceIdentity source) {
        Citation citation = new Citation(source.id(), "SKU-1", "qty=3", "hash", NOW);
        return new Evidence("e1", source, List.of(citation), "summary",
                Freshness.of(NOW, Duration.ofHours(1)), new Confidence(0.9, "r"));
    }

    @Test
    @DisplayName("Confidence enforces bounds and thresholds")
    void confidence() {
        assertThat(new Confidence(0.4, "r").isLow()).isTrue();
        assertThat(new Confidence(0.9, "r").isHigh()).isTrue();
        assertThat(Confidence.none("x").value()).isZero();
        assertThat(Confidence.certain("x").value()).isEqualTo(1.0);
        assertThatThrownBy(() -> new Confidence(1.5, "r")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Confidence(Double.NaN, "r")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Freshness computes age and staleness")
    void freshness() {
        Freshness fresh = Freshness.of(NOW, Duration.ofHours(1));
        assertThat(fresh.ageAt(NOW.plusSeconds(60))).isEqualTo(Duration.ofSeconds(60));
        assertThat(fresh.isStaleAt(NOW.plusSeconds(30))).isFalse();
        assertThat(fresh.isStaleAt(NOW.plus(Duration.ofHours(2)))).isTrue();
        assertThatThrownBy(() -> Freshness.of(NOW, Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SourceKind authority and factories")
    void sourceIdentity() {
        assertThat(liveSource().isAuthoritative()).isTrue();
        assertThat(SourceIdentity.fixture("f", "F").isAuthoritative()).isFalse();
        assertThat(SourceIdentity.simulated("s", "S").kind()).isEqualTo(SourceKind.SIMULATED);
        assertThat(SourceKind.DERIVED.isAuthoritative()).isFalse();
        assertThatThrownBy(() -> new SourceIdentity(" ", "d", SourceKind.LIVE, "s"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Evidence citation and staleness helpers")
    void evidence() {
        Evidence cited = citedEvidence(liveSource());
        assertThat(cited.isCited()).isTrue();
        assertThat(cited.isAuthoritative()).isTrue();
        assertThat(cited.isStaleAt(NOW.plus(Duration.ofHours(2)))).isTrue();
        Evidence uncited = new Evidence("e2", SourceIdentity.fixture("f", "F"), List.of(), "s",
                Freshness.of(NOW, Duration.ofHours(1)), new Confidence(0.5, "r"));
        assertThat(uncited.isCited()).isFalse();
        assertThat(uncited.isAuthoritative()).isFalse();
    }

    @Test
    @DisplayName("ResultStatus classification")
    void resultStatus() {
        assertThat(ResultStatus.OK.hasData()).isTrue();
        assertThat(ResultStatus.OK.isComplete()).isTrue();
        assertThat(ResultStatus.DEGRADED.hasData()).isTrue();
        assertThat(ResultStatus.DEGRADED.isDegraded()).isTrue();
        assertThat(ResultStatus.EMPTY.hasData()).isFalse();
        assertThat(ResultStatus.ERROR.hasData()).isFalse();
        assertThat(ResultStatus.PARTIAL.isDegraded()).isTrue();
    }

    @Test
    @DisplayName("TokenUsage sums and rejects negatives")
    void tokenUsage() {
        TokenUsage a = new TokenUsage(1, 100, 50, 200);
        TokenUsage b = new TokenUsage(1, 10, 5, 20);
        assertThat(a.totalTokens()).isEqualTo(150);
        assertThat(a.plus(b)).isEqualTo(new TokenUsage(2, 110, 55, 220));
        assertThatThrownBy(() -> new TokenUsage(-1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ToolResult.ok downgrades non-authoritative sources to DEGRADED")
    void toolResultOk() {
        SourceIdentity fixture = SourceIdentity.fixture("f", "F");
        ToolResult<String> result = ToolResult.ok(fixture, "payload",
                List.of(citedEvidence(liveSource())), Freshness.of(NOW, Duration.ofHours(1)),
                new Confidence(0.9, "r"), 10);
        assertThat(result.status()).isEqualTo(ResultStatus.DEGRADED);
        assertThat(result.isSimulatedOrFixture()).isTrue();
        assertThat(result.isGrounded()).isTrue();

        ToolResult<String> live = ToolResult.ok(liveSource(), "payload", List.of(),
                Freshness.of(NOW, Duration.ofHours(1)), new Confidence(0.9, "r"), 10);
        assertThat(live.status()).isEqualTo(ResultStatus.OK);
        assertThat(live.isSimulatedOrFixture()).isFalse();
    }

    @Test
    @DisplayName("ToolResult factories and staleness")
    void toolResultFactories() {
        assertThat(ToolResult.empty(liveSource(), "none").status()).isEqualTo(ResultStatus.EMPTY);
        assertThat(ToolResult.error(liveSource(), "boom").status()).isEqualTo(ResultStatus.ERROR);
        ToolResult<String> degraded = ToolResult.degraded(SourceIdentity.fixture("f", "F"), "p",
                List.of(citedEvidence(liveSource())), Freshness.of(NOW, Duration.ofHours(1)),
                new Confidence(0.5, "r"), "cache", 5);
        assertThat(degraded.hasStaleEvidenceAt(NOW.plus(Duration.ofHours(2)))).isTrue();
        assertThatThrownBy(() -> ToolResult.ok(liveSource(), "p", List.of(),
                Freshness.of(NOW, Duration.ofHours(1)), new Confidence(0.9, "r"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ToolRequest validation and expiry")
    void toolRequest() {
        ToolRequest request = ToolRequest.of("inventory.fetch", "R1", NOW.plusSeconds(60));
        assertThat(request.isExpiredAt(NOW)).isFalse();
        assertThat(request.isExpiredAt(NOW.plusSeconds(120))).isTrue();
        assertThat(request.parameters()).isEmpty();
        assertThatThrownBy(() -> ToolRequest.of(" ", "R1", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolRequest("t", "R1", java.util.Map.of(), null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AgentResultEnvelope grounding and degraded detection")
    void envelope() {
        AgentResultEnvelope grounded = new AgentResultEnvelope("A", ResultStatus.OK, "id", "hash",
                "azure-openai", "gpt-4o", "{}", List.of(citedEvidence(liveSource())),
                new Confidence(0.9, "r"), TokenUsage.ZERO, 12, true, List.of());
        assertThat(grounded.isGrounded()).isTrue();
        assertThat(grounded.citedEvidenceCount()).isEqualTo(1);
        assertThat(grounded.isDegraded()).isFalse();

        AgentResultEnvelope degraded = new AgentResultEnvelope("A", ResultStatus.DEGRADED, null, null,
                null, null, null, null, null, null, 0, false, List.of("reason"));
        assertThat(degraded.isDegraded()).isTrue();
        assertThat(degraded.provider()).isEmpty();
        assertThat(degraded.payloadJson()).isEmpty();
        assertThatThrownBy(() -> new AgentResultEnvelope(" ", ResultStatus.OK, "", "", "", "", "",
                List.of(), null, null, 0, true, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Hashes produce stable SHA-256")
    void hashes() {
        assertThat(Hashes.sha256Hex("abc")).isEqualTo(Hashes.sha256Hex("abc"));
        assertThat(Hashes.sha256Hex("abc")).isNotEqualTo(Hashes.sha256Hex("abd"));
        assertThat(Hashes.shortSha256("abc", 8)).hasSize(8);
        assertThat(Hashes.sha256Hex(null)).isEqualTo(Hashes.sha256Hex(""));
    }

    @Test
    @DisplayName("Citation bounds snippet and requires locator")
    void citation() {
        Citation citation = new Citation("s", "loc", "  snippet  ", null, NOW);
        assertThat(citation.snippet()).isEqualTo("snippet");
        assertThat(citation.valueHash()).isEmpty();
        assertThatThrownBy(() -> new Citation("s", " ", "x", "h", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
