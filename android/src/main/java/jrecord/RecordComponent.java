package jrecord;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Record reflection shim for the M10 build-time source transform (rule T4:
 * {@code java.lang.reflect.RecordComponent} -> {@code jrecord.RecordComponent}).
 *
 * <p>Stand-in for the four {@code RecordComponent} members {@code content.StrictBinder} calls on
 * the components {@link Records#components(Class)} hands it: {@link #getName()} (:539),
 * {@link #getType()} (:316), {@link #getGenericType()} (:319) and
 * {@link #getAnnotation(Class)} (:538). Each answer comes from the record's
 * {@link java.lang.reflect.Field} of the component's name, which is how the desktop platform
 * answers too: for a record component javac emits a private final field of the same name, the
 * same generic signature and — for an annotation whose target includes {@code FIELD}, as the
 * game's {@code JsonName} does — the same annotations. D8's record desugaring (every record,
 * {@code minSdk 33}) keeps those fields intact; only the {@code Record} attribute, and with it
 * the platform's component order, is lost, which is what {@link Records} recovers from the
 * build-time table.
 *
 * <p>Not a {@code java.*} stand-in, so the census policy of the other shims does not apply: the
 * surface is exactly what the binder needs, plus {@link #toString()} for diagnostics.
 */
public final class RecordComponent {

    private final Field field;

    /**
     * Wraps a record's component field.
     *
     * @param field the record's declared field of the component's name
     */
    RecordComponent(Field field) {
        this.field = Objects.requireNonNull(field, "field");
    }

    /**
     * The component name (census: StrictBinder.java:539).
     *
     * @return the name, as declared
     */
    public String getName() {
        return field.getName();
    }

    /**
     * The declared type (census: StrictBinder.java:316), the canonical constructor's parameter
     * type at this component's position.
     *
     * @return the raw type
     */
    public Class<?> getType() {
        return field.getType();
    }

    /**
     * The declared generic type (census: StrictBinder.java:319), for example
     * {@code List<StatModifierDef>}; the same object as {@link #getType()} for a non-generic
     * component.
     *
     * @return the generic type
     */
    public Type getGenericType() {
        return field.getGenericType();
    }

    /**
     * The component's annotation of a type, if present (census: StrictBinder.java:538, for
     * {@code JsonName}).
     *
     * @param annotationClass the annotation type
     * @param <A> the annotation type
     * @return the annotation, or {@code null} when the component carries none of that type
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    @Override
    public String toString() {
        return field.getGenericType().getTypeName() + " " + field.getName();
    }
}
