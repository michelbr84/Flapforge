package io.github.michelbr84.flapforge.content;

/**
 * Thrown by {@link Registry#get(String)} when nothing in the registry carries the id (D10).
 *
 * <p>The message names both the registry kind and the id so a stack trace alone identifies the
 * broken reference: {@code Unknown bird id: 'sparrow'}.
 */
public final class UnknownIdException extends RuntimeException {

    private final String kind;
    private final String id;

    /**
     * Creates the exception.
     *
     * @param kind the registry kind ({@code bird}, {@code curve}, {@code tier}, …)
     * @param id the id that was not found
     */
    public UnknownIdException(String kind, String id) {
        super("Unknown " + kind + " id: '" + id + "'");
        this.kind = kind;
        this.id = id;
    }

    /**
     * The registry kind.
     *
     * @return the kind
     */
    public String kind() {
        return kind;
    }

    /**
     * The id that was not found.
     *
     * @return the id
     */
    public String id() {
        return id;
    }
}
