package com.agentforge.commerce.runtime.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge.commerce.runtime.config.RuntimeProperties;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for per-run budgets, the reservation ledger, model/provider
 * allowlists and per-agent model resolution.
 */
@DisplayName("Runtime budgets and model policy")
class RuntimeBudgetTest {

    private static RunBudget budget() {
        return new RunBudget(10_000, 5, Duration.ofSeconds(60), 100_000, 2_500, 10_000);
    }

    @Test
    @DisplayName("Budget estimates cost from token rates")
    void estimateCost() {
        RunBudget budget = budget();
        assertThat(budget.estimateCostMicros(1000, 1000)).isEqualTo(12_500);
        assertThatThrownBy(() -> new RunBudget(0, 1, Duration.ofSeconds(1), 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunBudget(1, 1, Duration.ZERO, 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Ledger reserves, settles with actuals and snapshots headroom")
    void reserveSettle() {
        BudgetLedger ledger = new BudgetLedger(budget());
        BudgetLedger.Reservation reservation = ledger.reserve(100, 200);
        ledger.settle(reservation, new TokenUsage(1, 120, 180, 3_000), 250);
        BudgetSnapshot snapshot = ledger.snapshot();
        assertThat(snapshot.used().totalTokens()).isEqualTo(300);
        assertThat(snapshot.used().requests()).isEqualTo(1);
        assertThat(snapshot.latencyMillis()).isEqualTo(250);
        assertThat(snapshot.remainingTokens()).isEqualTo(9_700);
        assertThat(snapshot.remainingRequests()).isEqualTo(4);
        assertThat(snapshot.remainingCostMicros()).isEqualTo(97_000);
        assertThat(snapshot.exhausted()).isFalse();
    }

    @Test
    @DisplayName("Ledger trips the token budget before spending")
    void tokenBudget() {
        BudgetLedger ledger = new BudgetLedger(new RunBudget(150, 10, Duration.ofSeconds(60), 100_000, 1, 1));
        assertThatThrownBy(() -> ledger.reserve(100, 100))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(e -> assertThat(((BudgetExceededException) e).dimension()).isEqualTo("tokens"));
    }

    @Test
    @DisplayName("Ledger trips the request budget")
    void requestBudget() {
        BudgetLedger ledger = new BudgetLedger(new RunBudget(10_000, 1, Duration.ofSeconds(60), 100_000, 1, 1));
        ledger.settle(ledger.reserve(1, 1), new TokenUsage(1, 1, 1, 1), 1);
        assertThatThrownBy(() -> ledger.reserve(1, 1))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(e -> assertThat(((BudgetExceededException) e).dimension()).isEqualTo("requests"));
    }

    @Test
    @DisplayName("Ledger trips the cost budget")
    void costBudget() {
        BudgetLedger ledger = new BudgetLedger(new RunBudget(1_000_000, 10, Duration.ofSeconds(60), 100, 10_000, 10_000));
        assertThatThrownBy(() -> ledger.reserve(100, 100))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(e -> assertThat(((BudgetExceededException) e).dimension()).isEqualTo("cost"));
    }

    @Test
    @DisplayName("Ledger trips the latency budget before the next call")
    void latencyBudget() {
        BudgetLedger ledger = new BudgetLedger(new RunBudget(10_000, 10, Duration.ofMillis(100), 100_000, 1, 1));
        ledger.settle(ledger.reserve(1, 1), new TokenUsage(1, 1, 1, 1), 200);
        assertThatThrownBy(() -> ledger.reserve(1, 1))
                .isInstanceOf(BudgetExceededException.class)
                .satisfies(e -> assertThat(((BudgetExceededException) e).dimension()).isEqualTo("latency"));
    }

    @Test
    @DisplayName("Abort keeps the conservative estimate counted")
    void abort() {
        BudgetLedger ledger = new BudgetLedger(budget());
        BudgetLedger.Reservation reservation = ledger.reserve(50, 50);
        ledger.abort(reservation, 40);
        assertThat(ledger.snapshot().used().requests()).isEqualTo(1);
        assertThatThrownBy(() -> ledger.reserve(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RuntimeBudgets derives ledgers from configuration")
    void runtimeBudgets() {
        RuntimeBudgets budgets = new RuntimeBudgets(new RuntimeProperties());
        assertThat(budgets.budget().maxTokens()).isEqualTo(120_000);
        assertThat(budgets.newLedger()).isNotNull();
    }

    @Test
    @DisplayName("ModelPolicy enforces provider and model allowlists")
    void modelPolicy() {
        ModelPolicy policy = new ModelPolicy(new RuntimeProperties());
        policy.checkProvider("azure-openai");
        assertThat(policy.resolveModel("InventoryAgent")).isEqualTo("gpt-4o");
        assertThatThrownBy(() -> policy.checkProvider("rogue-provider"))
                .isInstanceOf(ModelNotAllowedException.class);
        assertThatThrownBy(() -> policy.checkProvider(null)).isInstanceOf(ModelNotAllowedException.class);
    }

    @Test
    @DisplayName("ModelPolicy honours per-agent overrides and rejects off-list models")
    void perAgentModel() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.getModels().setPerAgent(Map.of("PricingAgent", "gpt-4o-mini", "Rogue", "banned-model"));
        ModelPolicy policy = new ModelPolicy(properties);
        assertThat(policy.resolveModel("PricingAgent")).isEqualTo("gpt-4o-mini");
        assertThatThrownBy(() -> policy.resolveModel("Rogue")).isInstanceOf(ModelNotAllowedException.class);
    }

    @Test
    @DisplayName("ModelPolicy rejects a default model outside the allowlist")
    void defaultModelOffList() {
        RuntimeProperties properties = new RuntimeProperties();
        properties.getModels().setDefaultModel("not-allowed");
        assertThatThrownBy(() -> new ModelPolicy(properties)).isInstanceOf(IllegalStateException.class);
    }
}
