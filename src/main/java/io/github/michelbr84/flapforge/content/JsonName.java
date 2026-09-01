package io.github.michelbr84.flapforge.content;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a record component to a JSON key that cannot be a Java identifier, such as
 * {@code "default"} on {@code TierDef}. Without it {@link StrictBinder} uses the component name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD,
        ElementType.PARAMETER})
public @interface JsonName {

    /**
     * The JSON key.
     *
     * @return the key
     */
    String value();
}
