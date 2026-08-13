package com.agentforge.commerce.platform.api;

import com.agentforge.commerce.platform.api.dto.ApprovalDecisionRequest;
import com.agentforge.commerce.platform.api.dto.ApprovalResponse;
import com.agentforge.commerce.platform.persistence.domain.ApprovalStatus;
import com.agentforge.commerce.platform.persistence.entity.ApprovalRequestEntity;
import com.agentforge.commerce.platform.service.ApprovalService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned REST API for human approval decisions on a run's recommended actions.
 * Decisions require the {@code approvals:write} scope; reading requires
 * {@code runs:read}.
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_runs:read')")
    public ApprovalResponse getApproval(@PathVariable UUID id) {
        return RunMapper.toApprovalResponse(approvalService.find(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SCOPE_approvals:write')")
    public ApprovalResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication) {
        return decide(id, ApprovalStatus.APPROVED, request, authentication);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SCOPE_approvals:write')")
    public ApprovalResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApprovalDecisionRequest request,
            Authentication authentication) {
        return decide(id, ApprovalStatus.REJECTED, request, authentication);
    }

    private ApprovalResponse decide(UUID id, ApprovalStatus decision,
                                    ApprovalDecisionRequest request, Authentication authentication) {
        String reason = request == null ? null : request.reason();
        String decidedBy = authentication == null ? null : authentication.getName();
        ApprovalRequestEntity decided = approvalService.decide(id, decision, decidedBy, reason);
        return RunMapper.toApprovalResponse(decided);
    }
}
