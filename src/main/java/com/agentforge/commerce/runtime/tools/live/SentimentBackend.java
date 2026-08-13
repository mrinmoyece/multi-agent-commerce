package com.agentforge.commerce.runtime.tools.live;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.SentimentObservations;

/**
 * Production boundary to a real customer-sentiment system (reviews, support,
 * social, returns). See {@link InventoryBackend} for the contract and the
 * fail-closed behaviour when no implementation is wired.
 */
public interface SentimentBackend {

    /** @return authoritative sentiment observations for the request, or throws on failure. */
    SentimentObservations fetchObservations(ToolRequest request);

    /** @return a description of the backing system of record (never a secret/URL credential). */
    String systemOfRecord();
}
