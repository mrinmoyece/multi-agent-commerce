package com.agentforge.commerce.platform.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guards the metrics pipeline against high-cardinality label explosion.
 *
 * <p>Run, retailer and tenant identifiers are unbounded and must never become
 * Prometheus label values (each distinct value creates a new time series). Runtime
 * spans carry those identifiers as high-cardinality trace attributes, which stay in
 * traces and are stripped before metrics are exported. This filter is a defence in
 * depth: even if a new meter accidentally attaches one of these keys as a tag, it is
 * removed here before the registry ever sees it.</p>
 */
@Configuration
public class MetricsConfig {

    private static final List<String> FORBIDDEN_TAG_KEYS = List.of(
            "run.id", "run_id", "runId",
            "retailer.id", "retailer_id", "retailerId",
            "tenant.id", "tenant_id", "tenantId", "tenant",
            "correlation.id", "correlation_id", "correlationId");

    @Bean
    public MeterFilter highCardinalityTagStripper() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                var kept = new java.util.ArrayList<io.micrometer.core.instrument.Tag>();
                boolean stripped = false;
                for (io.micrometer.core.instrument.Tag tag : id.getTagsAsIterable()) {
                    if (FORBIDDEN_TAG_KEYS.contains(tag.getKey())) {
                        stripped = true;
                    } else {
                        kept.add(tag);
                    }
                }
                return stripped ? id.replaceTags(kept) : id;
            }
        };
    }
}
