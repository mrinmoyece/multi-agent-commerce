package com.agentforge.commerce.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class DailyAnalysisContext {
    private LocalDate date;
    private String retailerId;
    private String region;
    private boolean isWeekend;
    private boolean isSalesPeriod;
}
