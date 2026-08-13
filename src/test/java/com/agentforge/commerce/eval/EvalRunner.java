package com.agentforge.commerce.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point for the offline agentic evaluation gate.
 *
 * <p>Usage: {@code EvalRunner <smoke|gate> [--update]}.</p>
 *
 * <ul>
 *   <li>{@code smoke} runs a fast subset (the {@code normal} scenario) and fails
 *       the process if it does not pass — a quick local signal.</li>
 *   <li>{@code gate} runs every golden scenario, writes the deterministic report
 *       to {@code build/eval/eval-report.json}, and compares it against the
 *       committed baseline at {@code eval/baseline/eval-report.json}. The process
 *       fails if any scenario misses its thresholds <em>or</em> the report drifts
 *       from the baseline, so unreviewed behavioural changes cannot merge.</li>
 *   <li>{@code --update} rewrites the committed baseline from the current run
 *       (used deliberately when an intended behavioural change is reviewed).</li>
 * </ul>
 *
 * <p>The report contains only aggregate, non-sensitive scores — never prompts,
 * payloads or credentials.</p>
 */
public final class EvalRunner {

    private static final Path BASELINE = Path.of("eval", "baseline", "eval-report.json");
    private static final Path OUTPUT = Path.of("build", "eval", "eval-report.json");

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final EvalHarness harness = new EvalHarness();

    private EvalRunner() {
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "gate";
        boolean update = args.length > 1 && "--update".equals(args[1]);
        int code = new EvalRunner().execute(mode, update);
        if (code != 0) {
            System.exit(code);
        }
    }

    int execute(String mode, boolean update) throws IOException {
        return switch (mode) {
            case "smoke" -> smoke();
            case "gate" -> gate(update);
            default -> {
                System.err.println("Unknown eval mode: " + mode + " (expected smoke|gate)");
                yield 2;
            }
        };
    }

    private int smoke() {
        ScenarioResult normal = harness.run("normal");
        System.out.println("[eval:smoke] normal -> " + (normal.passed() ? "PASS" : "FAIL"));
        return normal.passed() ? 0 : 1;
    }

    private int gate(boolean update) throws IOException {
        EvalReport report = harness.runAll();
        String rendered = serialise(report);
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, rendered, StandardCharsets.UTF_8);

        for (ScenarioResult scenario : report.scenarios()) {
            System.out.println("[eval:gate] " + pad(scenario.id()) + " -> "
                    + (scenario.passed() ? "PASS" : "FAIL") + "  " + scenario.metrics());
        }
        System.out.println("[eval:gate] " + report.passedCount() + "/" + report.scenarios().size()
                + " scenarios passed");

        if (update) {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, rendered, StandardCharsets.UTF_8);
            System.out.println("[eval:gate] baseline updated at " + BASELINE);
            return report.allPassed() ? 0 : 1;
        }

        int exit = report.allPassed() ? 0 : 1;
        if (!report.allPassed()) {
            System.err.println("[eval:gate] FAILED: one or more scenarios missed their thresholds");
        }
        if (!Files.exists(BASELINE)) {
            System.err.println("[eval:gate] FAILED: no committed baseline at " + BASELINE
                    + " (run with --update to create it)");
            return 1;
        }
        String baseline = Files.readString(BASELINE, StandardCharsets.UTF_8).strip();
        if (!baseline.equals(rendered.strip())) {
            System.err.println("[eval:gate] DRIFT DETECTED: report differs from committed baseline " + BASELINE);
            System.err.println("[eval:gate] review the change and, if intended, re-run with --update");
            return 1;
        }
        return exit;
    }

    private String serialise(EvalReport report) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
    }

    private static String pad(String id) {
        return String.format("%-28s", id);
    }

    /** @return the golden scenario ids the gate covers (for documentation/tests). */
    public static List<String> scenarioIds() {
        return new EvalHarness().scenarioIds();
    }
}
