package io.github.michelbr84.flapforge.content;

import java.util.List;
import java.util.Objects;

/**
 * Aggregates every content error found in one pass (D10): binding rejections from
 * {@link StrictBinder} and rule violations from {@link ContentValidator}. Each entry starts with
 * a {@code file#/json/pointer} location so the author can jump straight to the offending key.
 *
 * <p>The exception is thrown once, at the end of the pass, so a broken data file reports all of
 * its problems instead of one per run.
 */
public final class ContentException extends RuntimeException {

    private final List<String> errors;

    /**
     * Creates the exception.
     *
     * @param summary what failed (used as the first line of the message)
     * @param errors the individual errors, each prefixed with its location
     */
    public ContentException(String summary, List<String> errors) {
        super(message(summary, errors));
        this.errors = List.copyOf(errors);
    }

    private static String message(String summary, List<String> errors) {
        Objects.requireNonNull(summary, "summary");
        StringBuilder sb = new StringBuilder(summary).append(" (").append(errors.size())
                .append(errors.size() == 1 ? " error)" : " errors)");
        for (String e : errors) {
            sb.append("\n  - ").append(e);
        }
        return sb.toString();
    }

    /**
     * The individual errors in discovery order.
     *
     * @return an unmodifiable list
     */
    public List<String> errors() {
        return errors;
    }
}
