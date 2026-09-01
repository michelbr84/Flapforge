package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The crash-safe write contract of D15/E13, asserted on its own rather than through
 * {@link SettingsStore}: a normal write is one atomic move that leaves no temporary file behind, a
 * refused atomic move is retried {@value AtomicFiles#ATOMIC_MOVE_ATTEMPTS} times and then falls
 * back to a non-atomic replace, and only a write that cannot land at all reports
 * {@link AtomicFiles.Outcome#IO_FAILED} — with the previous file untouched.
 *
 * <p>Without these, replacing the move with a plain copy — which can leave a truncated file on a
 * power cut, exactly what E13 exists to prevent — passes the whole suite.
 */
class AtomicFilesTest {

    @TempDir
    Path dir;

    private Path target() {
        return dir.resolve("settings.json");
    }

    private Path temp() {
        return dir.resolve("settings.json" + AtomicFiles.TEMP_SUFFIX);
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void aNormalWriteIsOneAtomicMoveAndLeavesNoTemporaryFile() throws IOException {
        AtomicFiles.WriteResult result = AtomicFiles.writeString(target(), "{\"a\": 1}");

        assertTrue(result.ok(), result.detail());
        assertEquals(AtomicFiles.Outcome.OK, result.outcome());
        assertTrue(result.atomic(), "the rename must be atomic on a normal file system");
        assertEquals(1, result.moveAttempts(), "no retry is needed when nothing failed");
        assertEquals("", result.detail());
        assertEquals(target(), result.target());
        assertEquals("{\"a\": 1}", read(target()));
        assertFalse(Files.exists(temp()), "the temporary file must be gone");
    }

    @Test
    void theParentDirectoryIsCreatedAndAnExistingFileIsReplaced() throws IOException {
        Path nested = dir.resolve("profile").resolve("deeper").resolve("save.json");

        assertTrue(AtomicFiles.write(nested, "first".getBytes(StandardCharsets.UTF_8)).ok());
        assertEquals("first", read(nested));

        assertTrue(AtomicFiles.writeString(nested, "second").ok());
        assertEquals("second", read(nested), "the second write replaces the first");
        assertFalse(Files.exists(nested.resolveSibling("save.json" + AtomicFiles.TEMP_SUFFIX)));
    }

    @Test
    void aRefusedAtomicMoveIsRetriedAndThenReplacedNonAtomically() throws IOException {
        assertTrue(AtomicFiles.writeString(target(), "old").ok());

        AtomicFiles.WriteResult result = AtomicFiles.writeString(target(), "new",
                AtomicFiles.FailurePoint.ATOMIC_MOVE_FAILS);

        assertTrue(result.ok(), result.detail());
        assertFalse(result.atomic(), "the fallback replace is not atomic and must say so");
        assertEquals(AtomicFiles.ATOMIC_MOVE_ATTEMPTS, result.moveAttempts(),
                "every atomic attempt is made before the fallback");
        assertEquals("new", read(target()), "the bytes still reach the target");
        assertFalse(Files.exists(temp()));
    }

    @Test
    void aWriteThatCannotLandFailsAndLeavesThePreviousFileIntact() throws IOException {
        assertTrue(AtomicFiles.writeString(target(), "good").ok());
        // A non-empty directory under the target name: neither the atomic move nor the replace
        // fallback can put a file there, so both paths fail for a real reason.
        Path blocked = dir.resolve("blocked.json");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("keeps-it-non-empty.txt"), "x");

        AtomicFiles.WriteResult result = AtomicFiles.writeString(blocked, "nope");

        assertFalse(result.ok());
        assertEquals(AtomicFiles.Outcome.IO_FAILED, result.outcome());
        assertFalse(result.detail().isEmpty(), "a failure must explain itself");
        assertFalse(Files.exists(dir.resolve("blocked.json" + AtomicFiles.TEMP_SUFFIX)),
                "a failed write must not leave its temporary file behind");
        assertEquals("good", read(target()), "an unrelated file is untouched");
    }

    @Test
    void everySimulatedCrashLeavesTheDirectoryClean() throws IOException {
        for (AtomicFiles.FailurePoint at : new AtomicFiles.FailurePoint[] {
            AtomicFiles.FailurePoint.AFTER_TMP_WRITE, AtomicFiles.FailurePoint.BEFORE_MOVE}) {
            assertTrue(AtomicFiles.writeString(target(), "previous").ok());

            AtomicFiles.WriteResult result = AtomicFiles.writeString(target(), "lost", at);

            assertFalse(result.ok(), at.name());
            assertEquals("previous", read(target()), "the previous file survives " + at);
            assertFalse(Files.exists(temp()), "no temporary file survives " + at);
        }
    }

    @Test
    void aCrashAfterTheMoveReportsFailureAlthoughTheBytesLanded() throws IOException {
        assertTrue(AtomicFiles.writeString(target(), "previous").ok());

        AtomicFiles.WriteResult result = AtomicFiles.writeString(target(), "next",
                AtomicFiles.FailurePoint.AFTER_MOVE);

        assertFalse(result.ok());
        assertTrue(result.atomic(), "the move itself succeeded");
        assertEquals("next", read(target()), "which is why the new bytes are there");
    }
}
