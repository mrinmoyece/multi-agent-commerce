package com.agentforge.commerce.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class DailyAnalysisContext {
    /** Durable run identifier used for provenance, telemetry and memory writes; may be null for ad-hoc runs. */
    private String runId;
    private LocalDate date;
    private String retailerId;
    private String region;
    private boolean isWeekend;
    private boolean isSalesPeriod;
}
