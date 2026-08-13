package com.agentforge.commerce.perf;

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
 * Command-line entry point for the offline performance / concurrency gate.
 *
 * <p>Usage: {@code PerfRunner <smoke|gate>}. Writes a deterministic JSON artifact to
 * {@code build/perf/perf-report.json} and exits non-zero if any scenario misses its
 * explicit budget. Latency budgets are order-of-magnitude ceilings (not baseline
 * drift) so the gate is stable on shared runners while still catching regressions;
 * correctness invariants such as exactly-once claiming are hard failures.</p>
 */
public final class PerfRunner {

    private static final Path OUTPUT = Path.of("build", "perf", "perf-report.json");

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final PerfHarness harness = new PerfHarness();

    private PerfRunner() {
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "gate";
        int code = new PerfRunner().execute(mode);
        if (code != 0) {
            System.exit(code);
        }
    }

    int execute(String mode) throws IOException {
        List<PerfHarness.Scenario> scenarios = switch (mode) {
            case "smoke" -> harness.runSmoke();
            case "gate" -> harness.runAll();
            default -> {
                System.err.println("Unknown perf mode: " + mode + " (expected smoke|gate)");
                yield null;
            }
        };
        if (scenarios == null) {
            return 2;
        }

        Map<String, Object> report = new TreeMap<>();
        boolean allPassed = true;
        Map<String, Object> byScenario = new TreeMap<>();
        for (PerfHarness.Scenario s : scenarios) {
            allPassed &= s.passed();
            Map<String, Object> entry = new TreeMap<>();
            entry.put("passed", s.passed());
            entry.put("budget", s.budget());
            entry.put("metrics", new TreeMap<>(s.metrics()));
            byScenario.put(s.name(), entry);
            System.out.printf("[perf:%s] %-32s -> %s  %s%n", mode, s.name(),
                    s.passed() ? "PASS" : "FAIL", s.metrics());
        }
        report.put("mode", mode);
        report.put("passed", allPassed);
        report.put("scenarios", byScenario);

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, mapper.writeValueAsString(report) + "\n", StandardCharsets.UTF_8);
        System.out.printf("[perf:%s] %d/%d scenarios passed%n", mode,
                scenarios.stream().filter(PerfHarness.Scenario::passed).count(), scenarios.size());
        return allPassed ? 0 : 1;
    }
}
