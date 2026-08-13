package com.agentforge.commerce.runtime.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the strict, schema-like validation of agent outputs.
 */
@DisplayName("JSON schema validation")
class RuntimeValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ValidationResult validate(JsonSchema schema, String json) throws Exception {
        return schema.validate(mapper.readTree(json));
    }

    @Test
    @DisplayName("Valid inventory payload passes")
    void validInventory() throws Exception {
        String json = "{\"risks\":[\"r\"],\"reorders\":[],\"clearance\":[],"
                + "\"citedSkus\":[\"SKU-1\"],\"summary\":\"ok\"}";
        assertThat(validate(AgentSchemas.INVENTORY, json).valid()).isTrue();
    }

    @Test
    @DisplayName("Missing required field fails with a descriptive error")
    void missingField() throws Exception {
        String json = "{\"risks\":[],\"reorders\":[],\"clearance\":[],\"citedSkus\":[]}";
        ValidationResult result = validate(AgentSchemas.INVENTORY, json);
        assertThat(result.valid()).isFalse();
        assertThat(result.describe()).contains("summary");
    }

    @Test
    @DisplayName("Non-object payload is rejected")
    void nonObject() throws Exception {
        assertThat(validate(AgentSchemas.PRICING, "[]").valid()).isFalse();
        assertThat(AgentSchemas.PRICING.validate(null).valid()).isFalse();
    }

    @Test
    @DisplayName("Unexpected field is rejected by default")
    void unexpectedField() throws Exception {
        String json = "{\"positiveThemes\":[],\"negativeThemes\":[],\"citedTopics\":[],"
                + "\"summary\":\"ok\",\"extra\":\"x\"}";
        ValidationResult result = validate(AgentSchemas.SENTIMENT, json);
        assertThat(result.valid()).isFalse();
        assertThat(result.describe()).contains("unexpected field");
    }

    @Test
    @DisplayName("Blank text and non-string array elements are rejected")
    void typeRules() {
        JsonSchema schema = JsonSchema.named("t")
                .require("name", FieldType.NONEMPTY_TEXT)
                .require("tags", FieldType.NONEMPTY_STRING_ARRAY)
                .build();
        assertThat(schema.validate(node("{\"name\":\" \",\"tags\":[\"a\"]}")).valid()).isFalse();
        assertThat(schema.validate(node("{\"name\":\"x\",\"tags\":[]}")).valid()).isFalse();
        assertThat(schema.validate(node("{\"name\":\"x\",\"tags\":[1]}")).valid()).isFalse();
        assertThat(schema.validate(node("{\"name\":\"x\",\"tags\":\"nope\"}")).valid()).isFalse();
        assertThat(schema.validate(node("{\"name\":\"x\",\"tags\":[\"a\"]}")).valid()).isTrue();
    }

    @Test
    @DisplayName("allowUnknownFields relaxes the closed-world check")
    void allowUnknown() {
        JsonSchema schema = JsonSchema.named("t").require("name", FieldType.NONEMPTY_TEXT)
                .allowUnknownFields().build();
        assertThat(schema.validate(node("{\"name\":\"x\",\"extra\":true}")).valid()).isTrue();
        assertThat(schema.name()).isEqualTo("t");
    }

    @Test
    @DisplayName("Builder requires at least one field")
    void emptyBuilder() {
        assertThatThrownBy(() -> JsonSchema.named("t").build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ValidationResult helpers")
    void validationResult() {
        assertThat(ValidationResult.ok().valid()).isTrue();
        assertThat(ValidationResult.failed(null).errors()).isEmpty();
    }

    private com.fasterxml.jackson.databind.JsonNode node(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
