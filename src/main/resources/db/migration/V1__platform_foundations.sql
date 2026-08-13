-- Layer 1 platform foundations: durable analysis runs, agent results,
-- approvals, audit trail and idempotency keys.
--
-- This migration targets PostgreSQL and is applied by Flyway in every
-- non-test profile. Tests build an equivalent schema from the JPA model on
-- H2, so column names/types here must stay in step with the entities.

CREATE TABLE analysis_run (
    id                  UUID          NOT NULL,
    retailer_id         VARCHAR(255)  NOT NULL,
    business_date       DATE          NOT NULL,
    status              VARCHAR(32)   NOT NULL,
    correlation_id      VARCHAR(64)   NOT NULL,
    idempotency_key     VARCHAR(200),
    requested_by        VARCHAR(255),
    trigger_source      VARCHAR(32)   NOT NULL,
    executive_summary   VARCHAR(8000),
    cross_insights      VARCHAR(8000),
    recommended_actions VARCHAR(8000),
    urgent_alerts       VARCHAR(8000),
    failure_reason      VARCHAR(2000),
    version             BIGINT        NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_analysis_run PRIMARY KEY (id),
    CONSTRAINT uq_analysis_run_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_analysis_run_retailer ON analysis_run (retailer_id, created_at);
CREATE INDEX ix_analysis_run_dedupe ON analysis_run (retailer_id, business_date, status);

CREATE TABLE agent_result (
    id          UUID          NOT NULL,
    run_id      UUID          NOT NULL,
    agent_name  VARCHAR(64)   NOT NULL,
    status      VARCHAR(32)   NOT NULL,
    priority    VARCHAR(16)   NOT NULL,
    content     VARCHAR(8000),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_agent_result PRIMARY KEY (id),
    CONSTRAINT fk_agent_result_run FOREIGN KEY (run_id) REFERENCES analysis_run (id)
);

CREATE INDEX ix_agent_result_run ON agent_result (run_id);

CREATE TABLE approval_request (
    id              UUID          NOT NULL,
    run_id          UUID          NOT NULL,
    action          VARCHAR(2000) NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    requested_by    VARCHAR(255),
    decided_by      VARCHAR(255),
    decision_reason VARCHAR(2000),
    version         BIGINT        NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_approval_request PRIMARY KEY (id),
    CONSTRAINT fk_approval_request_run FOREIGN KEY (run_id) REFERENCES analysis_run (id)
);

CREATE INDEX ix_approval_request_run ON approval_request (run_id);

CREATE TABLE audit_event (
    id             UUID          NOT NULL,
    event_type     VARCHAR(64)   NOT NULL,
    run_id         UUID,
    principal      VARCHAR(200),
    correlation_id VARCHAR(64),
    detail         VARCHAR(4000),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX ix_audit_event_run ON audit_event (run_id);
CREATE INDEX ix_audit_event_type ON audit_event (event_type, created_at);

CREATE TABLE idempotency_key (
    idempotency_key     VARCHAR(200)  NOT NULL,
    request_fingerprint VARCHAR(128)  NOT NULL,
    run_id              UUID          NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (idempotency_key)
);
