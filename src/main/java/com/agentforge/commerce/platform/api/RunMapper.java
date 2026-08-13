package com.agentforge.commerce.platform.api;

import com.agentforge.commerce.platform.api.dto.AgentResultResponse;
import com.agentforge.commerce.platform.api.dto.ApprovalResponse;
import com.agentforge.commerce.platform.api.dto.RunResponse;
import com.agentforge.commerce.platform.api.dto.RunSummaryResponse;
import com.agentforge.commerce.platform.persistence.entity.AgentResultEntity;
import com.agentforge.commerce.platform.persistence.entity.AnalysisRunEntity;
import com.agentforge.commerce.platform.persistence.entity.ApprovalRequestEntity;
import java.util.Arrays;
import java.util.List;

/** Maps persistence entities to API responses. Pure and side-effect free. */
final class RunMapper {

    private RunMapper() {
    }

    static RunResponse toRunResponse(AnalysisRunEntity run, List<AgentResultEntity> agentResults) {
        return new RunResponse(
                run.getId(),
                run.getRetailerId(),
                run.getBusinessDate(),
                run.getStatus().name(),
                run.getTriggerSource(),
                run.getCorrelationId(),
                run.getExecutiveSummary(),
                splitLines(run.getCrossInsights()),
                splitLines(run.getRecommendedActions()),
                splitLines(run.getUrgentAlerts()),
                run.getFailureReason(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getCompletedAt(),
                agentResults.stream().map(RunMapper::toAgentResultResponse).toList());
    }

    static RunSummaryResponse toSummary(AnalysisRunEntity run) {
        return new RunSummaryResponse(
                run.getId(),
                run.getRetailerId(),
                run.getBusinessDate(),
                run.getStatus().name(),
                run.getCreatedAt(),
                run.getCompletedAt());
    }

    static AgentResultResponse toAgentResultResponse(AgentResultEntity entity) {
        return new AgentResultResponse(
                entity.getAgentName(),
                entity.getStatus().name(),
                entity.getPriority(),
                entity.getContent(),
                entity.getCreatedAt());
    }

    static ApprovalResponse toApprovalResponse(ApprovalRequestEntity entity) {
        return new ApprovalResponse(
                entity.getId(),
                entity.getRunId(),
                entity.getAction(),
                entity.getStatus().name(),
                entity.getRequestedBy(),
                entity.getDecidedBy(),
                entity.getDecisionReason(),
                entity.getCreatedAt(),
                entity.getDecidedAt());
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\n")).filter(s -> !s.isBlank()).toList();
    }
}
