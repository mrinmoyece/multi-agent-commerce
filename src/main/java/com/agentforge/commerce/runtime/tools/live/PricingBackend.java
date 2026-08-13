package com.agentforge.commerce.runtime.tools.live;

import com.agentforge.commerce.runtime.contract.ToolRequest;
import com.agentforge.commerce.runtime.tools.PricingObservations;

/**
 * Production boundary to a real pricing/competitor-intelligence system. See
 * {@link InventoryBackend} for the contract and the fail-closed behaviour when no
 * implementation is wired.
 */
public interface PricingBackend {

    /** @return authoritative pricing observations for the request, or throws on failure. */
    PricingObservations fetchObservations(ToolRequest request);

    /** @return a description of the backing system of record (never a secret/URL credential). */
    String systemOfRecord();
}
