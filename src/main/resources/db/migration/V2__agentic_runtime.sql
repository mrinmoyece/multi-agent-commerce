-- Layer 2 agentic runtime: durable semantic memory plus runtime metadata on
-- runs and agent results (prompt provenance, token/cost accounting, confidence,
-- degraded flags and human-in-the-loop risk reasons).
--
-- This migration targets PostgreSQL and is applied by Flyway in every non-test
-- profile. Tests build an equivalent schema from the JPA model on H2, so column
-- names/types here must stay in step with the entities.

-- Runtime metadata for a run (aggregated from the coordinator envelope + report).
ALTER TABLE analysis_run ADD COLUMN prompt_id          VARCHAR(128);
ALTER TABLE analysis_run ADD COLUMN prompt_hash        VARCHAR(64);
ALTER TABLE analysis_run ADD COLUMN provider           VARCHAR(64);
ALTER TABLE analysis_run ADD COLUMN model              VARCHAR(64);
ALTER TABLE analysis_run ADD COLUMN prompt_tokens      BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN completion_tokens  BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN cost_micros        BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN tool_calls         INTEGER       NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN agent_rounds       INTEGER       NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN min_confidence     DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE analysis_run ADD COLUMN degraded           BOOLEAN       NOT NULL DEFAULT FALSE;
ALTER TABLE analysis_run ADD COLUMN risk_reasons       VARCHAR(2000);

-- Per-agent runtime metadata (prompt provenance, usage, confidence, grounding).
ALTER TABLE agent_result ADD COLUMN prompt_id            VARCHAR(128);
ALTER TABLE agent_result ADD COLUMN prompt_hash          VARCHAR(64);
ALTER TABLE agent_result ADD COLUMN provider             VARCHAR(64);
ALTER TABLE agent_result ADD COLUMN model                VARCHAR(64);
ALTER TABLE agent_result ADD COLUMN prompt_tokens        BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE agent_result ADD COLUMN completion_tokens    BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE agent_result ADD COLUMN cost_micros          BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE agent_result ADD COLUMN confidence           DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE agent_result ADD COLUMN schema_valid         BOOLEAN       NOT NULL DEFAULT TRUE;
ALTER TABLE agent_result ADD COLUMN cited_evidence_count BIGINT        NOT NULL DEFAULT 0;
ALTER TABLE agent_result ADD COLUMN degraded_reasons     VARCHAR(1000);

-- Durable, retailer-scoped semantic memory. Only validated, evidence-backed
-- facts are written; tenant isolation is enforced by always querying retailer_id
-- and deduplication by the unique (retailer_id, fact_key, value_hash) constraint.
CREATE TABLE memory_fact (
    id                UUID          NOT NULL,
    retailer_id       VARCHAR(128)  NOT NULL,
    fact_key          VARCHAR(128)  NOT NULL,
    category          VARCHAR(64)   NOT NULL,
    value             VARCHAR(512)  NOT NULL,
    source_id         VARCHAR(128)  NOT NULL,
    source_kind       VARCHAR(16)   NOT NULL,
    confidence        DOUBLE PRECISION NOT NULL,
    value_hash        VARCHAR(64)   NOT NULL,
    provenance_run_id VARCHAR(64),
    observed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_memory_fact PRIMARY KEY (id),
    CONSTRAINT uq_memory_fact_dedup UNIQUE (retailer_id, fact_key, value_hash)
);

CREATE INDEX ix_memory_fact_retailer_key ON memory_fact (retailer_id, fact_key);
CREATE INDEX ix_memory_fact_expires ON memory_fact (expires_at);
