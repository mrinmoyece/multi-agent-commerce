package com.agentforge.commerce.runtime.validation;

/**
 * The kinds of field a {@link JsonSchema} can require. Deliberately small: agent
 * and coordinator outputs are simple, flat JSON objects, and a tight vocabulary
 * keeps validation strict and auditable.
 */
public enum FieldType {

    /** A JSON string that must be present and non-blank. */
    NONEMPTY_TEXT,

    /** A JSON array whose every element is a non-blank string. */
    STRING_ARRAY,

    /** A JSON array of non-blank strings that must contain at least one element. */
    NONEMPTY_STRING_ARRAY
}
