package com.agentforge.commerce.runtime.contract;

/**
 * Central, immutable bounds for the agent runtime. Every envelope enforces these
 * so that no tool, agent, or model output can push an unbounded payload through
 * the system. Keeping the limits in one place makes the safety posture auditable
 * and lets tests assert against a single source of truth.
 */
public final class RuntimeLimits {

    /** Hard ceiling on a single tool payload, in bytes. */
    public static final int MAX_TOOL_PAYLOAD_BYTES = 64 * 1024;

    /** Hard ceiling on a single evidence summary, in characters. */
    public static final int MAX_EVIDENCE_SUMMARY_CHARS = 2_000;

    /** Hard ceiling on a citation snippet, in characters. */
    public static final int MAX_CITATION_SNIPPET_CHARS = 512;

    /** Hard ceiling on a validated structured agent payload, in characters. */
    public static final int MAX_AGENT_PAYLOAD_CHARS = 16 * 1024;

    /** Maximum number of evidence items retained on a single envelope. */
    public static final int MAX_EVIDENCE_ITEMS = 64;

    /** Maximum number of citations retained on a single evidence item. */
    public static final int MAX_CITATIONS_PER_EVIDENCE = 64;

    /** Maximum number of entries a run's working memory may hold. */
    public static final int MAX_WORKING_MEMORY_ENTRIES = 128;

    private RuntimeLimits() {
    }
}
