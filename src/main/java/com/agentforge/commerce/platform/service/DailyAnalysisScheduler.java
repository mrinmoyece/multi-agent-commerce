package com.agentforge.commerce.platform.service;

import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the daily analysis on a schedule. Rather than executing in-process (which
 * would run once per replica), it durably creates a run keyed by an idempotency
 * key derived from retailer + business date. The unique idempotency key means
 * every replica firing the cron converges on a single run, which is then executed
 * exactly once by {@link DurableRunExecutor}.
 */
@Component
public class DailyAnalysisScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DailyAnalysisScheduler.class);

    private final AnalysisRunService runService;

    @Value("${retailer.id:default-retailer}")
    private String retailerId;

    public DailyAnalysisScheduler(AnalysisRunService runService) {
        this.runService = runService;
    }

    /** Runs at 06:00 daily. Override via {@code briefing.cron} in application.yml. */
    @Scheduled(cron = "${briefing.cron:0 0 6 * * *}")
    public void scheduleDailyAnalysis() {
        LocalDate date = LocalDate.now();
        String idempotencyKey = "scheduled:" + retailerId + ":" + date;
        String fingerprint = RequestFingerprint.of("SCHEDULED", retailerId, date.toString());
        LOG.info("Scheduling durable daily analysis for retailer={} date={}", retailerId, date);
        runService.createRun(new CreateRunCommand(
                retailerId,
                date,
                idempotencyKey,
                fingerprint,
                "scheduler",
                "SCHEDULED",
                UUID.randomUUID().toString()));
    }
}
