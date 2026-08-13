package com.agentforge.commerce.runtime.budget;

/**
 * Thrown when a run would exceed one of its resource budgets. The runtime raises
 * this as a hard stop <em>before</em> a provider call is made, so budget
 * exhaustion never results in an uncounted spend. The {@link #dimension()} names
 * which budget was hit for auditing.
 */
public class BudgetExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String dimension;

    public BudgetExceededException(String dimension, String message) {
        super(message);
        this.dimension = dimension;
    }

    /** @return the budget dimension that was exhausted (e.g. {@code tokens}, {@code cost}). */
    public String dimension() {
        return dimension;
    }
}
