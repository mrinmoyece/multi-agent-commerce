package com.agentforge.commerce.runtime.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict, dependency-free JSON-schema-like contract for a flat object.
 *
 * <p>Every specialist and coordinator output is validated against one of these
 * before it is accepted. Validation is fail-closed and exact: the document must
 * be a JSON object, every required field must be present with the declared type,
 * and by default no unknown fields are allowed. This is what turns raw,
 * nondeterministic model text into a trustworthy structured result.</p>
 */
public final class JsonSchema {

    private final String name;
    private final Map<String, FieldType> fields;
    private final boolean allowUnknownFields;

    private JsonSchema(String name, Map<String, FieldType> fields, boolean allowUnknownFields) {
        this.name = name;
        this.fields = Map.copyOf(fields);
        this.allowUnknownFields = allowUnknownFields;
    }

    /** @return the schema name (used in error messages and telemetry). */
    public String name() {
        return name;
    }

    /** Start building a schema with the given name. */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /** Validate {@code node} against this schema, collecting all violations. */
    public ValidationResult validate(JsonNode node) {
        List<String> errors = new ArrayList<>();
        if (node == null || !node.isObject()) {
            errors.add(name + ": payload is not a JSON object");
            return ValidationResult.failed(errors);
        }
        for (Map.Entry<String, FieldType> field : fields.entrySet()) {
            validateField(node, field.getKey(), field.getValue(), errors);
        }
        if (!allowUnknownFields) {
            node.fieldNames().forEachRemaining(fieldName -> {
                if (!fields.containsKey(fieldName)) {
                    errors.add(name + ": unexpected field '" + fieldName + "'");
                }
            });
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    private void validateField(JsonNode node, String field, FieldType type, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            errors.add(name + ": missing required field '" + field + "'");
            return;
        }
        switch (type) {
            case NONEMPTY_TEXT -> {
                if (!value.isTextual() || value.asText().isBlank()) {
                    errors.add(name + ": field '" + field + "' must be a non-empty string");
                }
            }
            case STRING_ARRAY -> validateStringArray(field, value, errors, false);
            case NONEMPTY_STRING_ARRAY -> validateStringArray(field, value, errors, true);
            default -> errors.add(name + ": field '" + field + "' has an unsupported rule");
        }
    }

    private void validateStringArray(String field, JsonNode value, List<String> errors, boolean requireNonEmpty) {
        if (!value.isArray()) {
            errors.add(name + ": field '" + field + "' must be an array");
            return;
        }
        if (requireNonEmpty && value.isEmpty()) {
            errors.add(name + ": field '" + field + "' must contain at least one element");
        }
        for (JsonNode element : value) {
            if (!element.isTextual() || element.asText().isBlank()) {
                errors.add(name + ": field '" + field + "' must contain only non-empty strings");
                return;
            }
        }
    }

    /** Fluent builder for {@link JsonSchema}. */
    public static final class Builder {
        private final String name;
        private final Map<String, FieldType> fields = new LinkedHashMap<>();
        private boolean allowUnknownFields;

        private Builder(String name) {
            this.name = name;
        }

        /** Require a field of the given type. */
        public Builder require(String field, FieldType type) {
            fields.put(field, type);
            return this;
        }

        /** Permit fields not declared in the schema (off by default). */
        public Builder allowUnknownFields() {
            this.allowUnknownFields = true;
            return this;
        }

        /** Build the immutable schema. */
        public JsonSchema build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("schema '" + name + "' must declare at least one field");
            }
            return new JsonSchema(name, fields, allowUnknownFields);
        }
    }
}
