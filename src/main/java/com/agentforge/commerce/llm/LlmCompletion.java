package com.agentforge.commerce.llm;

import com.agentforge.commerce.runtime.contract.TokenUsage;

/**
 * The result of a provider completion together with its measured token usage.
 *
 * <p>Capturing usage at the gateway boundary is what lets the runtime enforce and
 * reconcile per-run token/cost budgets against the provider's own reported
 * accounting rather than an estimate. The cost field is left at zero here; the
 * budget ledger computes cost from the authoritative per-model rate.</p>
 *
 * @param content the validated, non-empty completion text
 * @param usage   provider-reported token usage (cost filled in by the ledger)
 */
public record LlmCompletion(String content, TokenUsage usage) {

    public LlmCompletion {
        if (content == null) {
            throw new IllegalArgumentException("completion content must be provided");
        }
        usage = usage == null ? TokenUsage.ZERO : usage;
    }
}
