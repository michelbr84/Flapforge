package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.BirdDef;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Android record path of {@link StrictBinder} (M10).
 *
 * <p>D8 desugars records: on a device {@code Class.isRecord()} is {@code false} for every
 * {@code content.defs} type, so the binder reads the components off the declared instance fields
 * instead. Robolectric and the desktop tests run on a JVM with real records, so neither can
 * exercise that path — a mismatch between the two would only show up as "unsupported target
 * type" on a phone, which is exactly how it shipped. This test pins the two readings together:
 * for every def, the structural components must equal the record components, in order.
 */
class StrictBinderDesugarTest {

    @Test
    void everyDefReadsTheSameThroughFieldsAsThroughRecordComponents() throws Exception {
        List<Class<?>> defs = defClasses();
        assertFalse(defs.isEmpty(), "the defs package was found on the classpath");
        for (Class<?> def : defs) {
            if (!def.isRecord()) {
                continue;
            }
            List<StrictBinder.Component> viaRecord = StrictBinder.componentsOf(def);
            // Hand the fields over in the order a device does: the dex format stores them
            // sorted by name, so anything that trusted declaration order would swap
            // same-typed components here (UpgradesDef's trees and nodes) or find no
            // constructor at all (BirdDef).
            List<Field> dexOrder = new ArrayList<>();
            for (Field f : def.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    dexOrder.add(f);
                }
            }
            dexOrder.sort(Comparator.comparing(Field::getName));
            List<StrictBinder.Component> viaFields =
                    StrictBinder.structuralComponents(def, dexOrder);
            assertEquals(viaRecord, viaFields,
                    () -> "desugared binding differs for " + def.getName());
        }
    }

    @Test
    void aDefIsStillRecognisedWhenItsRecordNessIsInvisible() {
        // What the structural path must accept: the shape a desugared record leaves behind.
        assertEquals(StrictBinder.componentsOf(BirdDef.class),
                StrictBinder.structuralComponents(BirdDef.class));
        assertTrue(StrictBinder.structuralComponents(BirdDef.class).size() > 1);
    }

    private static List<Class<?>> defClasses() throws URISyntaxException,
            ClassNotFoundException {
        String pkg = BirdDef.class.getPackageName();
        File root = new File(BirdDef.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        File dir = new File(root, pkg.replace('.', '/'));
        List<Class<?>> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".class") && !name.contains("$")) {
                out.add(Class.forName(pkg + "." + name.substring(0, name.length() - 6)));
            }
        }
        return out;
    }
}
