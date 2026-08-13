package com.agentforge.commerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Validates the Flyway migration set two ways:
 *
 * <ol>
 *   <li><b>Always (no Docker required):</b> the committed migrations are discovered,
 *       are strictly increasing in version with no duplicates or gaps, and contain no
 *       obviously destructive DDL — a deterministic guard that runs everywhere.</li>
 *   <li><b>When Docker is available:</b> a real PostgreSQL container is migrated from
 *       an empty database through the full history, then migrated again to assert the
 *       upgrade path is idempotent and lands on the expected version.</li>
 * </ol>
 *
 * <p>The Docker-gated test is skipped (not failed) when Docker is absent, so the
 * deterministic guard remains a mandatory check on machines without a daemon.</p>
 */
class FlywayMigrationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.+\\.sql$");

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    void migrationsAreStrictlyOrderedAndNonDestructive() throws IOException {
        List<Integer> versions = new ArrayList<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            List<Path> sorted = files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            assertThat(sorted).as("at least the two committed migrations exist").hasSizeGreaterThanOrEqualTo(2);
            for (Path file : sorted) {
                String name = file.getFileName().toString();
                Matcher m = VERSIONED.matcher(name);
                assertThat(m.matches()).as("migration name %s follows V<n>__desc.sql", name).isTrue();
                versions.add(Integer.parseInt(m.group(1)));

                String sql = Files.readString(file, StandardCharsets.UTF_8).toUpperCase();
                assertThat(sql)
                        .as("%s must not drop tables/columns in place (use expand/contract)", name)
                        .doesNotContain("DROP TABLE")
                        .doesNotContain("DROP COLUMN");
            }
        }
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i))
                    .as("versions are 1-based and contiguous with no gaps/duplicates")
                    .isEqualTo(i + 1);
        }
    }

    @Test
    @EnabledIf("dockerAvailable")
    void migratesFromEmptyDatabaseAndIsIdempotent() {
        try (PostgreSQLContainer<?> postgres =
                     new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            int applied = flyway.migrate().migrationsExecuted;
            assertThat(applied).as("all migrations applied from empty DB").isGreaterThanOrEqualTo(2);

            var current = flyway.info().current();
            assertThat(current).isNotNull();
            assertThat(current.getVersion().getVersion()).isEqualTo("2");

            int reapplied = flyway.migrate().migrationsExecuted;
            assertThat(reapplied).as("second migrate is a no-op").isZero();
        }
    }
}
