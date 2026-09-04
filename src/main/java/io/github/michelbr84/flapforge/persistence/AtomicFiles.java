package io.github.michelbr84.flapforge.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Crash-safe file writes (D15, E13): the bytes go to {@code &lt;target&gt;.tmp}, the temporary file
 * is forced to the storage device, it is moved onto the target with
 * {@link StandardCopyOption#ATOMIC_MOVE} and the containing directory is forced afterwards, so a
 * power cut leaves either the old file or the new one — never a truncated one.
 *
 * <p>A failing atomic move is retried <em>immediately</em> three times (a lock held by a virus
 * scanner or an indexer is usually gone by the next call); there is no sleep and no back-off,
 * because the pure packages may not touch the thread API (E13). When every retry fails the move is
 * repeated with {@link StandardCopyOption#REPLACE_EXISTING}, which is not atomic but keeps the game
 * saving on file systems that do not support atomic replacement. Only when that fails too does the
 * call report {@link Outcome#IO_FAILED}: expected failures are results, never exceptions.
 *
 * <p>{@link FailurePoint} injects a simulated crash at a chosen step so tests can prove that the
 * previous file survives.
 */
public final class AtomicFiles {

    /** Suffix of the temporary file written next to the target. */
    public static final String TEMP_SUFFIX = ".tmp";
    /** How many times a failing {@code ATOMIC_MOVE} is retried before the fallback. */
    public static final int ATOMIC_MOVE_ATTEMPTS = 3;

    /** How a write ended. */
    public enum Outcome {
        /** The bytes are on disk under the target name. */
        OK,
        /** Nothing could be written; the previous file, if any, is untouched. */
        IO_FAILED
    }

    /**
     * Step at which a write pretends to crash. Production always passes {@link #NONE}.
     */
    public enum FailurePoint {
        /** No simulated failure. */
        NONE,
        /** Fail after the temporary file has been written, before it is forced. */
        AFTER_TMP_WRITE,
        /** Fail after the temporary file has been forced, before the move. */
        BEFORE_MOVE,
        /** Fail after the move, before the directory is forced. */
        AFTER_MOVE,
        /**
         * Make every {@code ATOMIC_MOVE} attempt fail, so the retry loop and the
         * {@link StandardCopyOption#REPLACE_EXISTING} fallback run. There is no portable way to
         * make a real file system refuse an atomic rename inside a test, and E13's retry-then-
         * replace contract is exactly what must not rot.
         */
        ATOMIC_MOVE_FAILS
    }

    /**
     * The result of one write.
     *
     * @param outcome how the write ended
     * @param target the file that was being written
     * @param atomic whether the final move was atomic ({@code false} means the replace fallback ran)
     * @param moveAttempts how many atomic-move attempts were made
     * @param detail an English explanation, empty when {@link Outcome#OK}
     */
    public record WriteResult(Outcome outcome, Path target, boolean atomic, int moveAttempts,
            String detail) {

        /**
         * Whether the bytes reached the target.
         *
         * @return {@code true} when the outcome is {@link Outcome#OK}
         */
        public boolean ok() {
            return outcome == Outcome.OK;
        }
    }

    private AtomicFiles() {
    }

    /**
     * Writes UTF-8 text.
     *
     * @param target the file to replace
     * @param text the content
     * @return the result; never {@code null}, never thrown
     */
    public static WriteResult writeString(Path target, String text) {
        return writeString(target, text, FailurePoint.NONE);
    }

    /**
     * Writes UTF-8 text with a simulated crash.
     *
     * @param target the file to replace
     * @param text the content
     * @param failurePoint where to pretend to crash
     * @return the result; never {@code null}, never thrown
     */
    public static WriteResult writeString(Path target, String text, FailurePoint failurePoint) {
        Objects.requireNonNull(text, "text");
        return write(target, text.getBytes(StandardCharsets.UTF_8), failurePoint);
    }

    /**
     * Writes bytes.
     *
     * @param target the file to replace
     * @param bytes the content
     * @return the result; never {@code null}, never thrown
     */
    public static WriteResult write(Path target, byte[] bytes) {
        return write(target, bytes, FailurePoint.NONE);
    }

    /**
     * Writes bytes with a simulated crash.
     *
     * @param target the file to replace
     * @param bytes the content
     * @param failurePoint where to pretend to crash
     * @return the result; never {@code null}, never thrown
     */
    public static WriteResult write(Path target, byte[] bytes, FailurePoint failurePoint) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bytes, "bytes");
        FailurePoint failAt = failurePoint == null ? FailurePoint.NONE : failurePoint;
        Path tmp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeTemp(tmp, bytes, failAt);
        } catch (SimulatedFailure e) {
            deleteQuietly(tmp);
            return failed(target, e.getMessage());
        } catch (IOException e) {
            deleteQuietly(tmp);
            return failed(target, "could not write " + tmp.getFileName() + ": " + e);
        }
        return move(target, tmp, failAt);
    }

    private static void writeTemp(Path tmp, byte[] bytes, FailurePoint failAt) throws IOException {
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            if (failAt == FailurePoint.AFTER_TMP_WRITE) {
                throw new SimulatedFailure("simulated failure after the temporary file was written");
            }
            channel.force(true);
        }
        if (failAt == FailurePoint.BEFORE_MOVE) {
            throw new SimulatedFailure("simulated failure before the atomic move");
        }
    }

    private static WriteResult move(Path target, Path tmp, FailurePoint failAt) {
        IOException last = null;
        int attempts = 0;
        for (int attempt = 1; attempt <= ATOMIC_MOVE_ATTEMPTS; attempt++) {
            attempts = attempt;
            try {
                if (failAt == FailurePoint.ATOMIC_MOVE_FAILS) {
                    throw new SimulatedFailure("simulated atomic move failure");
                }
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                return finish(target, true, attempt, failAt);
            } catch (AtomicMoveNotSupportedException e) {
                // The file system will never do it; retrying is pointless.
                last = e;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return finish(target, false, attempts, failAt);
        } catch (IOException e) {
            deleteQuietly(tmp);
            return new WriteResult(Outcome.IO_FAILED, target, false, attempts,
                    "could not replace " + target.getFileName() + ": " + e
                            + (last == null ? "" : " (atomic move failed with " + last + ")"));
        }
    }

    private static WriteResult finish(Path target, boolean atomic, int attempts,
            FailurePoint failAt) {
        if (failAt == FailurePoint.AFTER_MOVE) {
            return new WriteResult(Outcome.IO_FAILED, target, atomic, attempts,
                    "simulated failure after the move");
        }
        forceDirectory(target.toAbsolutePath().getParent());
        return new WriteResult(Outcome.OK, target, atomic, attempts, "");
    }

    /**
     * Forces the directory entry so the rename itself survives a power cut. Not every platform
     * lets a directory be opened as a channel (Windows does not); there the call is a no-op.
     *
     * @param dir the directory, may be {@code null}
     */
    private static void forceDirectory(Path dir) {
        if (dir == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            // The rename is already durable enough on platforms that refuse this.
        }
    }

    private static WriteResult failed(Path target, String detail) {
        return new WriteResult(Outcome.IO_FAILED, target, false, 0, detail);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Nothing better to do: the caller already learns that the write failed.
        }
    }

    /** Internal marker for {@link FailurePoint}; never escapes this class. */
    private static final class SimulatedFailure extends IOException {
        SimulatedFailure(String message) {
            super(message);
        }
    }
}
