package com.agentforge.commerce.platform.api;

import com.agentforge.commerce.platform.api.dto.ApprovalResponse;
import com.agentforge.commerce.platform.api.dto.CreateRunRequest;
import com.agentforge.commerce.platform.api.dto.PageResponse;
import com.agentforge.commerce.platform.api.dto.RunResponse;
import com.agentforge.commerce.platform.api.dto.RunSummaryResponse;
import com.agentforge.commerce.platform.persistence.entity.AnalysisRunEntity;
import com.agentforge.commerce.platform.service.AnalysisRunService;
import com.agentforge.commerce.platform.service.ApprovalService;
import com.agentforge.commerce.platform.service.CreateRunCommand;
import com.agentforge.commerce.platform.service.RequestFingerprint;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned REST API for analysis runs. Fine-grained authorisation is enforced
 * per operation with OAuth2 scopes via method security.
 */
@RestController
@RequestMapping("/api/v1/analysis-runs")
public class AnalysisRunController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AnalysisRunService runService;
    private final ApprovalService approvalService;

    public AnalysisRunController(AnalysisRunService runService, ApprovalService approvalService) {
        this.runService = runService;
        this.approvalService = approvalService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_runs:write')")
    public ResponseEntity<RunResponse> createRun(
            @Valid @RequestBody CreateRunRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {

        LocalDate businessDate = request.businessDate() != null
                ? request.businessDate() : LocalDate.now();
        String fingerprint = RequestFingerprint.of(request.retailerId(), businessDate.toString());
        String correlationId = MDC.get("correlationId");

        AnalysisRunEntity run = runService.createRun(new CreateRunCommand(
                request.retailerId(),
                businessDate,
                normaliseKey(idempotencyKey),
                fingerprint,
                principal(authentication),
                "API",
                correlationId));

        RunResponse body = RunMapper.toRunResponse(run, runService.listAgentResults(run.getId()));
        return ResponseEntity.created(URI.create("/api/v1/analysis-runs/" + run.getId())).body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_runs:read')")
    public RunResponse getRun(@PathVariable UUID id) {
        AnalysisRunEntity run = runService.findRun(id);
        return RunMapper.toRunResponse(run, runService.listAgentResults(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_runs:read')")
    public PageResponse<RunSummaryResponse> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String retailerId) {

        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AnalysisRunEntity> runs = runService.listRuns(retailerId, pageable);
        List<RunSummaryResponse> content = runs.getContent().stream()
                .map(RunMapper::toSummary).toList();
        return PageResponse.from(runs, content);
    }

    @GetMapping("/{id}/approvals")
    @PreAuthorize("hasAuthority('SCOPE_runs:read')")
    public List<ApprovalResponse> listApprovals(@PathVariable UUID id) {
        runService.findRun(id);
        return approvalService.listForRun(id).stream()
                .map(RunMapper::toApprovalResponse).toList();
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String normaliseKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private static String principal(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
