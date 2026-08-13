package com.agentforge.commerce.platform.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Migrate-only mode for the release pipeline.
 *
 * <p>When {@code app.migrate-only=true} the application boots far enough for Flyway
 * auto-configuration to apply all pending migrations, then this runner immediately
 * shuts the context down with a clean exit code. It lets a dedicated Kubernetes Job
 * (see {@code k8s/base/migration-job.yaml}) own schema evolution so the rolling
 * Deployment never has multiple replicas racing on migration and can be started only
 * after the schema is known-good. Redis and the LLM provider are disabled for the Job,
 * so this path exercises the database exclusively.</p>
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.migrate-only", havingValue = "true")
public class MigrationRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);

    private final ApplicationContext context;

    public MigrationRunner(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(String... args) {
        LOG.info("migrate-only mode: Flyway migrations applied, shutting down");
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
