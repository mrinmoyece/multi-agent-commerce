package com.agentforge.commerce.runtime.tools;

/**
 * Grounded inventory tool. Returns a provenanced {@link InventorySnapshot} so the
 * inventory specialist reasons only over cited, bounded evidence.
 */
public interface InventoryTool extends GroundedTool<InventorySnapshot> {
}
