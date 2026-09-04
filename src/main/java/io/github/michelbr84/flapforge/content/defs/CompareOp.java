package io.github.michelbr84.flapforge.content.defs;

/**
 * How an achievement compares its counter with its threshold (§4).
 */
public enum CompareOp {

    /** counter &gt;= value. */
    GTE,
    /** counter &gt; value. */
    GT,
    /** counter &lt;= value. */
    LTE,
    /** counter &lt; value. */
    LT,
    /** counter == value. */
    EQ
}
