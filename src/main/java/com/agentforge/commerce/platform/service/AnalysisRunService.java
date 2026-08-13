package com.agentforge.commerce.platform.service;

import com.agentforge.commerce.coordinator.AgentCoordinator.OrchestrationResult;
import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyBriefing;
import com.agentforge.commerce.platform.config.PlatformProperties;
import com.agentforge.commerce.platform.coordination.LockHandle;
import com.agentforge.commerce.platform.coordination.RunStatusCache;
import com.agentforge.commerce.platform.persistence.domain.AgentResultStatus;
import com.agentforge.commerce.platform.persistence.domain.ApprovalStatus;
import com.agentforge.commerce.platform.persistence.domain.RunStatus;
import com.agentforge.commerce.platform.persistence.entity.AgentResultEntity;
import com.agentforge.commerce.platform.persistence.entity.AnalysisRunEntity;
import com.agentforge.commerce.platform.persistence.entity.ApprovalRequestEntity;
import com.agentforge.commerce.platform.persistence.entity.IdempotencyKeyEntity;
import com.agentforge.commerce.platform.persistence.repo.AgentResultRepository;
import com.agentforge.commerce.platform.persistence.repo.AnalysisRunRepository;
import com.agentforge.commerce.platform.persistence.repo.IdempotencyKeyRepository;
import com.agentforge.commerce.platform.service.exception.IdempotencyConflictException;
import com.agentforge.commerce.platform.service.exception.InvalidStateTransitionException;
import com.agentforge.commerce.platform.service.exception.RunNotFoundException;
import com.agentforge.commerce.runtime.contract.AgentResultEnvelope;
import com.agentforge.commerce.runtime.contract.TokenUsage;
import com.agentforge.commerce.runtime.engine.RuntimeRunReport;
import com.agentforge.commerce.runtime.policy.RiskDecision;
import com.agentforge.commerce.runtime.policy.RiskPolicy;
import com.agentforge.commerce.runtime.telemetry.RuntimeTelemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transactional owner of the analysis-run aggregate. Enforces the run state
 * machine, drives idempotent creation, records audit events, and maintains the
 * short-lived status cache. All write methods are transactional so the run,
 * its agent results, approvals and audit trail move together or not at all.
 */
@Service
public class AnalysisRunService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisRunService.class);
    private static final int TEXT_LIMIT = 8000;

    private final AnalysisRunRepository runRepository;
    private final AgentResultRepository agentResultRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditService auditService;
    private final ApprovalService approvalService;
    private final com.agentforge.commerce.platform.coordination.DistributedLock distributedLock;
    private final RunStatusCache statusCache;
    private final PlatformProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RiskPolicy riskPolicy;
    private final RuntimeTelemetry telemetry;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AnalysisRunService(
            AnalysisRunRepository runRepository,
            AgentResultRepository agentResultRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            AuditService auditService,
            ApprovalService approvalService,
            com.agentforge.commerce.platform.coordination.DistributedLock distributedLock,
            RunStatusCache statusCache,
            PlatformProperties properties,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            RiskPolicy riskPolicy,
            RuntimeTelemetry telemetry) {
        this.runRepository = runRepository;
        this.agentResultRepository = agentResultRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.auditService = auditService;
        this.approvalService = approvalService;
        this.distributedLock = distributedLock;
        this.statusCache = statusCache;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.riskPolicy = riskPolicy;
        this.telemetry = telemetry;
    }

    /**
     * Durably create a run. If an idempotency key is supplied and already known,
     * the previously created run is returned unchanged (safe retry); reusing a key
     * with a different payload is a conflict. Newly created runs publish a
     * post-commit event that triggers exactly-once asynchronous execution.
     */
    @Transactional
    public AnalysisRunEntity createRun(CreateRunCommand command) {
        Optional<LockHandle> lock = acquireIdempotencyLock(command.idempotencyKey());
        try {
            if (command.idempotencyKey() != null) {
                Optional<AnalysisRunEntity> replay = replayExisting(command);
                if (replay.isPresent()) {
                    return replay.get();
                }
            }

            AnalysisRunEntity run = new AnalysisRunEntity();
            Instant now = Instant.now();
            run.setId(UUID.randomUUID());
            run.setRetailerId(command.retailerId());
            run.setBusinessDate(command.businessDate());
            run.setStatus(RunStatus.PENDING);
            run.setCorrelationId(command.correlationId() != null
                    ? command.correlationId() : UUID.randomUUID().toString());
            run.setIdempotencyKey(command.idempotencyKey());
            run.setRequestedBy(command.requestedBy());
            run.setTriggerSource(command.triggerSource());
            run.setVersion(0L);
            run.setCreatedAt(now);
            run.setUpdatedAt(now);
            AnalysisRunEntity saved = runRepository.save(run);

            if (command.idempotencyKey() != null) {
                IdempotencyKeyEntity key = new IdempotencyKeyEntity();
                key.setKey(command.idempotencyKey());
                key.setRequestFingerprint(command.requestFingerprint());
                key.setRunId(saved.getId());
                key.setCreatedAt(now);
                idempotencyKeyRepository.saveAndFlush(key);
            }

            auditService.record("RUN_CREATED", saved.getId(), command.requestedBy(),
                    "retailer=" + command.retailerId() + " date=" + command.businessDate());
            cacheStatus(saved);
            eventPublisher.publishEvent(new RunCreatedEvent(saved.getId()));
            return saved;
        } finally {
            lock.ifPresent(LockHandle::close);
        }
    }

    private Optional<AnalysisRunEntity> replayExisting(CreateRunCommand command) {
        Optional<IdempotencyKeyEntity> existing =
                idempotencyKeyRepository.findById(command.idempotencyKey());
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyKeyEntity key = existing.get();
        if (!key.getRequestFingerprint().equals(command.requestFingerprint())) {
            throw new IdempotencyConflictException(
                    "Idempotency key reused with a different request payload");
        }
        return Optional.of(runRepository.findById(key.getRunId())
                .orElseThrow(() -> new RunNotFoundException(key.getRunId())));
    }

    private Optional<LockHandle> acquireIdempotencyLock(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return distributedLock.tryAcquire("idem:" + idempotencyKey, Duration.ofSeconds(10));
    }

    @Transactional(readOnly = true)
    public AnalysisRunEntity findRun(UUID id) {
        return runRepository.findById(id).orElseThrow(() -> new RunNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<AgentResultEntity> listAgentResults(UUID runId) {
        return agentResultRepository.findByRunIdOrderByAgentName(runId);
    }

    @Transactional(readOnly = true)
    public Page<AnalysisRunEntity> listRuns(String retailerId, Pageable pageable) {
        if (retailerId != null && !retailerId.isBlank()) {
            return runRepository.findByRetailerId(retailerId, pageable);
        }
        return runRepository.findAll(pageable);
    }

    /**
     * Claim a PENDING run for execution. Returns empty when the run is not in a
     * startable state (already claimed or finished) so the caller can skip.
     */
    @Transactional
    public Optional<AnalysisRunEntity> beginExecution(UUID runId) {
        AnalysisRunEntity run = findRun(runId);
        if (!run.getStatus().canTransitionTo(RunStatus.RUNNING)) {
            return Optional.empty();
        }
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        AnalysisRunEntity saved = runRepository.save(run);
        auditService.record("RUN_STARTED", runId, run.getRequestedBy(), null);
        cacheStatus(saved);
        return Optional.of(saved);
    }

    /**
     * Record the outcome of a run. Persists the briefing, runtime metadata,
     * per-agent envelopes and approvals, then either finalises the run to
     * {@code SUCCEEDED} or, when the human-in-the-loop risk policy flags a
     * high-impact or low-confidence action, pauses it in {@code AWAITING_APPROVAL}
     * until every approval is decided.
     */
    @Transactional
    public AnalysisRunEntity completeRun(UUID runId, OrchestrationResult result) {
        AnalysisRunEntity run = findRun(runId);
        DailyBriefing briefing = result.briefing();
        RiskDecision decision = riskPolicy.evaluate(briefing, result.report());
        RunStatus target = decision.requiresApproval() ? RunStatus.AWAITING_APPROVAL : RunStatus.SUCCEEDED;
        if (!run.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Run " + runId + " cannot move to " + target + " from " + run.getStatus());
        }

        applyBriefing(run, briefing);
        applyRunMetadata(run, result.report());
        run.setStatus(target);
        run.setUpdatedAt(Instant.now());
        if (target == RunStatus.SUCCEEDED) {
            run.setCompletedAt(Instant.now());
        } else {
            run.setRiskReasons(truncateReason(String.join(",", decision.reasons())));
        }
        AnalysisRunEntity saved = runRepository.save(run);

        persistAgentResults(runId, result);
        raiseApprovals(saved, briefing);

        if (target == RunStatus.AWAITING_APPROVAL) {
            telemetry.recordApprovalWait(runId.toString());
            auditService.record("RUN_AWAITING_APPROVAL", runId, run.getRequestedBy(),
                    "reasons=" + String.join(",", decision.reasons()));
        } else {
            auditService.record("RUN_SUCCEEDED", runId, run.getRequestedBy(),
                    "actions=" + safeSize(briefing.getRecommendedActions()));
        }
        cacheStatus(saved);
        return saved;
    }

    /**
     * Resume a run paused for approval once all of its approvals have been decided.
     * Triggered after an approval decision commits; deterministic and idempotent —
     * it finalises to {@code SUCCEEDED} only when no approval remains pending and
     * the run is still {@code AWAITING_APPROVAL}. Rejected actions are audited but
     * do not block completion (they are simply not enacted).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    public void resumeIfReady(ApprovalDecidedEvent event) {
        AnalysisRunEntity run = runRepository.findById(event.runId()).orElse(null);
        if (run == null || run.getStatus() != RunStatus.AWAITING_APPROVAL) {
            return;
        }
        List<ApprovalRequestEntity> approvals = approvalService.listForRun(event.runId());
        boolean stillPending = approvals.stream()
                .anyMatch(approval -> approval.getStatus() == ApprovalStatus.PENDING);
        if (stillPending || !run.getStatus().canTransitionTo(RunStatus.SUCCEEDED)) {
            return;
        }
        run.setStatus(RunStatus.SUCCEEDED);
        run.setCompletedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        AnalysisRunEntity saved = runRepository.save(run);
        long rejected = approvals.stream()
                .filter(approval -> approval.getStatus() == ApprovalStatus.REJECTED).count();
        auditService.record("RUN_RESUMED", event.runId(), run.getRequestedBy(),
                "approvals=" + approvals.size() + " rejected=" + rejected);
        cacheStatus(saved);
    }

    private void applyBriefing(AnalysisRunEntity run, DailyBriefing briefing) {
        run.setExecutiveSummary(truncate(briefing.getExecutiveSummary()));
        run.setCrossInsights(truncate(joinLines(briefing.getCrossInsights())));
        run.setRecommendedActions(truncate(joinLines(briefing.getRecommendedActions())));
        run.setUrgentAlerts(truncate(joinLines(briefing.getUrgentAlerts())));
    }

    private void applyRunMetadata(AnalysisRunEntity run, RuntimeRunReport report) {
        if (report == null) {
            return;
        }
        run.setDegraded(report.degraded());
        run.setToolCalls(report.toolCalls());
        run.setAgentRounds(report.agentRounds());
        TokenUsage usage = report.totalUsage();
        run.setPromptTokens(usage.promptTokens());
        run.setCompletionTokens(usage.completionTokens());
        run.setCostMicros(usage.estimatedCostMicros());
        AgentResultEnvelope coordinator = report.coordinator();
        if (coordinator != null) {
            run.setPromptId(coordinator.promptId());
            run.setPromptHash(coordinator.promptHash());
            run.setProvider(coordinator.provider());
            run.setModel(coordinator.model());
        }
        List<AgentResultEnvelope> envelopes = report.allEnvelopes();
        double min = 1.0;
        for (AgentResultEnvelope envelope : envelopes) {
            min = Math.min(min, envelope.confidence().value());
        }
        run.setMinConfidence(envelopes.isEmpty() ? 0.0 : min);
    }

    /** Record a failed run with a non-sensitive reason. */
    @Transactional
    public AnalysisRunEntity failRun(UUID runId, String reason) {
        AnalysisRunEntity run = findRun(runId);
        if (run.getStatus().isTerminal()) {
            return run;
        }
        run.setStatus(RunStatus.FAILED);
        run.setFailureReason(truncateReason(reason));
        run.setCompletedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        AnalysisRunEntity saved = runRepository.save(run);
        auditService.record("RUN_FAILED", runId, run.getRequestedBy(), truncateReason(reason));
        cacheStatus(saved);
        return saved;
    }

    /** @return a cached compact status snapshot for the run, when still fresh. */
    public Optional<String> cachedStatus(UUID runId) {
        return statusCache.get(runId);
    }

    private void persistAgentResults(UUID runId, OrchestrationResult result) {
        Map<String, AgentResultEnvelope> envelopes = new HashMap<>();
        for (AgentResultEnvelope envelope : result.report().specialists()) {
            envelopes.put(envelope.agentName(), envelope);
        }
        for (AgentMessage message : result.agentMessages()) {
            AgentResultEntity entity = new AgentResultEntity();
            entity.setId(UUID.randomUUID());
            entity.setRunId(runId);
            entity.setAgentName(message.getAgentName());
            AgentResultStatus status = AgentResultClassifier.classify(message);
            entity.setStatus(status);
            entity.setPriority(message.getPriority() == null ? "NORMAL" : message.getPriority().name());
            entity.setContent(truncate(message.getContent()));
            applyEnvelopeMetadata(entity, envelopes.get(message.getAgentName()));
            entity.setCreatedAt(Instant.now());
            agentResultRepository.save(entity);
        }
    }

    private static void applyEnvelopeMetadata(AgentResultEntity entity, AgentResultEnvelope envelope) {
        if (envelope == null) {
            entity.setSchemaValid(true);
            return;
        }
        entity.setPromptId(envelope.promptId());
        entity.setPromptHash(envelope.promptHash());
        entity.setProvider(envelope.provider());
        entity.setModel(envelope.model());
        entity.setPromptTokens(envelope.usage().promptTokens());
        entity.setCompletionTokens(envelope.usage().completionTokens());
        entity.setCostMicros(envelope.usage().estimatedCostMicros());
        entity.setConfidence(envelope.confidence().value());
        entity.setSchemaValid(envelope.schemaValid());
        entity.setCitedEvidenceCount(envelope.citedEvidenceCount());
        String reasons = String.join(",", envelope.degradedReasons());
        entity.setDegradedReasons(reasons.length() > 1000 ? reasons.substring(0, 1000) : reasons);
    }

    private void raiseApprovals(AnalysisRunEntity run, DailyBriefing briefing) {
        List<String> actions = briefing.getRecommendedActions();
        if (actions == null) {
            return;
        }
        String requestedBy = run.getRequestedBy() == null ? "system" : run.getRequestedBy();
        for (String action : actions) {
            approvalService.raise(run.getId(), action, requestedBy);
        }
    }

    private void cacheStatus(AnalysisRunEntity run) {
        try {
            String snapshot = objectMapper.writeValueAsString(new RunStatusSnapshot(
                    run.getId(), run.getStatus().name(), run.getUpdatedAt()));
            statusCache.put(run.getId(), snapshot, properties.getStatusCache().getTtl());
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            LOG.debug("Skipping status cache for run {} ({})", run.getId(), ex.getClass().getSimpleName());
        }
    }

    private static String joinLines(List<String> values) {
        return values == null ? null : String.join("\n", values);
    }

    private static int safeSize(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > TEXT_LIMIT ? value.substring(0, TEXT_LIMIT) : value;
    }

    private static String truncateReason(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    /** Compact snapshot serialised into the status cache. */
    public record RunStatusSnapshot(UUID runId, String status, Instant updatedAt) {
    }
}
