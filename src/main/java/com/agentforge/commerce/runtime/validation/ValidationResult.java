package com.agentforge.commerce.runtime.validation;

import java.util.List;

/**
 * Outcome of validating a JSON document against a {@link JsonSchema}. Immutable
 * and self-describing: on failure it carries the concrete, non-sensitive reasons
 * so the runtime can fail closed with an auditable explanation rather than a bare
 * boolean.
 *
 * @param valid  whether the document satisfied the schema
 * @param errors human-readable, non-sensitive validation errors (empty when valid)
 */
public record ValidationResult(boolean valid, List<String> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** A passing result. */
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    /** A failing result carrying the given errors. */
    public static ValidationResult failed(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    /** @return a compact, single-line rendering of the errors for logs/audit. */
    public String describe() {
        return String.join("; ", errors);
    }
}
