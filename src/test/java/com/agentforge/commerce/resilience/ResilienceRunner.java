package com.agentforge.commerce.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Command-line entry point for the offline resilience / chaos gate.
 *
 * <p>Usage: {@code ResilienceRunner <smoke|gate>}. Writes a deterministic JSON
 * artifact to {@code build/resilience/resilience-report.json} and exits non-zero if
 * any scenario does not exhibit its expected fault-handling behaviour. All scenarios
 * are offline and reproducible.</p>
 */
public final class ResilienceRunner {

    private static final Path OUTPUT = Path.of("build", "resilience", "resilience-report.json");

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final ResilienceHarness harness = new ResilienceHarness();

    private ResilienceRunner() {
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "gate";
        int code = new ResilienceRunner().execute(mode);
        if (code != 0) {
            System.exit(code);
        }
    }

    int execute(String mode) throws IOException {
        List<ResilienceHarness.Scenario> scenarios = switch (mode) {
            case "smoke" -> harness.runSmoke();
            case "gate" -> harness.runAll();
            default -> {
                System.err.println("Unknown resilience mode: " + mode + " (expected smoke|gate)");
                yield null;
            }
        };
        if (scenarios == null) {
            return 2;
        }

        Map<String, Object> report = new TreeMap<>();
        boolean allPassed = true;
        Map<String, Object> byScenario = new TreeMap<>();
        for (ResilienceHarness.Scenario s : scenarios) {
            allPassed &= s.passed();
            Map<String, Object> entry = new TreeMap<>();
            entry.put("passed", s.passed());
            entry.put("expectation", s.expectation());
            entry.put("observed", new TreeMap<>(s.observed()));
            byScenario.put(s.name(), entry);
            System.out.printf("[resilience:%s] %-38s -> %s  %s%n", mode, s.name(),
                    s.passed() ? "PASS" : "FAIL", s.observed());
        }
        report.put("mode", mode);
        report.put("passed", allPassed);
        report.put("scenarios", byScenario);

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, mapper.writeValueAsString(report) + "\n", StandardCharsets.UTF_8);
        System.out.printf("[resilience:%s] %d/%d scenarios passed%n", mode,
                scenarios.stream().filter(ResilienceHarness.Scenario::passed).count(), scenarios.size());
        return allPassed ? 0 : 1;
    }
}
