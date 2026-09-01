package io.github.michelbr84.flapforge.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Reads and writes {@code settings.json} (§4, D15).
 *
 * <p>Load policy: a missing file yields the defaults; missing keys keep their defaults; a file
 * whose {@code version} is not {@link Settings#VERSION} — or which cannot be parsed at all — is
 * renamed to {@code settings.v&lt;N&gt;.json}, the defaults are used instead and a
 * {@link Notice} is raised that the UI can turn into a toast. Unknown keys are kept: the tree that
 * was read is remembered and the freshly serialised settings are laid over it on every write
 * (E22), so a key written by a newer build survives a round trip through this one.
 *
 * <p>Write policy: the JSON is rendered on the calling thread (the loop thread in the game) and
 * handed to an injected {@link Executor}; {@code app.Threads.saveExecutor()} keeps one queued
 * write at most, so the latest state wins. Nothing here throws.
 *
 * <p>A write runs on another thread, so its outcome cannot be read back on the line after
 * {@link #save(Settings)} — that would report the <em>previous</em> write. Each finished write
 * therefore pushes its {@link AtomicFiles.WriteResult} onto a small concurrent queue that the loop
 * thread drains with {@link #pollCompletedWrite()} once per tick; that is the only path a failure
 * takes to the {@code SaveFailed} event and the warning toast (D15). {@link #lastWrite()} stays
 * available for tests and tools that write synchronously.
 *
 * <p>{@link #hold(Settings)} adopts a state as the one in force <em>without</em> writing it, so
 * the settings a screen is editing live and the settings this store reports are never two
 * different things while a debounced write is pending.
 */
public final class SettingsStore {

    /**
     * Map-typed and list-typed nodes are replaced wholesale rather than merged (E22): a key
     * unbound by the player must not be resurrected by the previous file.
     */
    private static final Set<String> REPLACE_WHOLESALE = Set.of("keyBindings");

    /** How many distinct archive names one schema version may occupy before one is overwritten. */
    private static final int ARCHIVE_ATTEMPTS = 9;

    /** Why the store fell back to the defaults. */
    public enum Notice {
        /** The file loaded (or did not exist yet); nothing to tell the player. */
        NONE,
        /** The file carried another schema version; it was archived and the defaults restored. */
        RESET_VERSION_MISMATCH,
        /** The file could not be parsed; it was archived and the defaults restored. */
        RESET_UNREADABLE
    }

    /**
     * The outcome of a load.
     *
     * @param settings the settings to use (never {@code null})
     * @param notice what to tell the player, if anything
     * @param archived where the previous file was moved, or {@code null}
     */
    public record LoadResult(Settings settings, Notice notice, Path archived) {

        /**
         * Whether the player should be told that the file was reset.
         *
         * @return {@code true} when the notice is not {@link Notice#NONE}
         */
        public boolean hasNotice() {
            return notice != Notice.NONE;
        }
    }

    private final Executor writer;
    private final Path explicitFile;

    private JsonObject raw = new JsonObject();
    private Settings current = Settings.defaults().normalize();
    private Notice notice = Notice.NONE;
    private Path archived;
    private AtomicFiles.FailurePoint failurePoint = AtomicFiles.FailurePoint.NONE;
    private volatile AtomicFiles.WriteResult lastWrite;
    private final ConcurrentLinkedQueue<AtomicFiles.WriteResult> completed =
            new ConcurrentLinkedQueue<>();

    /**
     * Creates a store over {@link SavePaths#settingsFile()}.
     *
     * @param writer executor the write runs on
     */
    public SettingsStore(Executor writer) {
        this(writer, null);
    }

    /**
     * Creates a store over an explicit file (tests and tools).
     *
     * @param writer executor the write runs on
     * @param file the settings file, or {@code null} to follow {@link SavePaths}
     */
    public SettingsStore(Executor writer, Path file) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.explicitFile = file;
    }

    /**
     * The file this store reads and writes.
     *
     * @return the path
     */
    public Path file() {
        return explicitFile != null ? explicitFile : SavePaths.settingsFile();
    }

    /**
     * Reads the file, applying the load policy. Never throws.
     *
     * @return what was loaded and what to tell the player
     */
    public LoadResult load() {
        notice = Notice.NONE;
        archived = null;
        raw = new JsonObject();
        current = Settings.defaults().normalize();
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return new LoadResult(current, notice, null);
        }
        String text = read(file);
        JsonObject tree = text == null ? null : JsonCodec.parseObject(text);
        if (tree == null) {
            reset(file, Notice.RESET_UNREADABLE, 0);
            return new LoadResult(current, notice, archived);
        }
        int version = versionOf(tree);
        if (version != Settings.VERSION) {
            reset(file, Notice.RESET_VERSION_MISMATCH, version);
            return new LoadResult(current, notice, archived);
        }
        Settings bound;
        try {
            bound = JsonCodec.fromTree(tree, Settings.class);
        } catch (JsonParseException e) {
            reset(file, Notice.RESET_UNREADABLE, version);
            return new LoadResult(current, notice, archived);
        }
        raw = tree;
        current = (bound == null ? Settings.defaults() : bound).normalize();
        return new LoadResult(current, notice, null);
    }

    /**
     * The settings in force.
     *
     * @return the current settings (the live instance the store keeps)
     */
    public Settings settings() {
        return current;
    }

    /**
     * Why the last load fell back to the defaults.
     *
     * @return the notice
     */
    public Notice notice() {
        return notice;
    }

    /**
     * Where the previous file was archived by the last load.
     *
     * @return the path, or {@code null}
     */
    public Path archivedFile() {
        return archived;
    }

    /**
     * Clears the notice once the UI has shown it.
     */
    public void clearNotice() {
        notice = Notice.NONE;
    }

    /**
     * Adopts a state as the one in force without writing it.
     *
     * <p>This is what a screen editing settings live calls through {@code GameContext}: the game
     * already runs with the new values, so {@link #settings()} must report them, while the file
     * write waits for the debounce. It is cheap — a copy and a normalise, no JSON.
     *
     * @param settings the state now in force
     * @return the normalised copy the store now holds
     */
    public Settings hold(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        Settings next = settings.copy().normalize();
        next.version = Settings.VERSION;
        current = next;
        return current;
    }

    /**
     * Stores a new state and writes it. The JSON is built here, the file write runs on the
     * injected executor. Never throws.
     *
     * <p>The outcome is <em>not</em> readable when this returns: with the real save executor the
     * write has not run yet. Drain {@link #pollCompletedWrite()} on the loop thread instead.
     *
     * @param settings the state to persist
     * @return the normalised copy the store now holds
     */
    public Settings save(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            hold(settings);
            JsonObject merged = JsonCodec.overlay(raw, JsonCodec.toObject(current),
                    REPLACE_WHOLESALE);
            raw = merged;
            String text = JsonCodec.toJson(merged) + "\n";
            Path file = file();
            AtomicFiles.FailurePoint at = failurePoint;
            writer.execute(() -> finished(AtomicFiles.writeString(file, text, at)));
        } catch (RuntimeException e) {
            finished(new AtomicFiles.WriteResult(AtomicFiles.Outcome.IO_FAILED, file(), false, 0,
                    "settings write was rejected: " + e));
        }
        return current;
    }

    /** Records a finished write and queues it for the loop thread. Runs on the writer thread. */
    private void finished(AtomicFiles.WriteResult result) {
        lastWrite = result;
        completed.add(result);
    }

    /**
     * Takes the oldest write that has finished since the last call.
     *
     * <p>Called once per tick from the loop thread, which is where the event bus and the toast
     * layer live; each result is handed out exactly once, so a failure is reported against the
     * write that actually failed and never twice.
     *
     * @return the result, or {@code null} when no write has finished
     */
    public AtomicFiles.WriteResult pollCompletedWrite() {
        return completed.poll();
    }

    /**
     * The result of the most recent write, or {@code null} when nothing has been written yet.
     *
     * <p>Only meaningful when the injected executor runs the write inline (tests and tools); the
     * game reads {@link #pollCompletedWrite()}.
     *
     * @return the result
     */
    public AtomicFiles.WriteResult lastWrite() {
        return lastWrite;
    }

    /**
     * Injects a simulated crash into the next writes (tests only).
     *
     * @param failurePoint where the write should pretend to fail
     */
    public void failurePoint(AtomicFiles.FailurePoint failurePoint) {
        this.failurePoint = failurePoint == null ? AtomicFiles.FailurePoint.NONE : failurePoint;
    }

    /**
     * The tree the store will overlay the next write onto (tests and tools).
     *
     * @return a copy of the remembered tree
     */
    public JsonObject rawTree() {
        return raw.deepCopy();
    }

    private void reset(Path file, Notice why, int version) {
        notice = why;
        archived = archive(file, version);
        raw = new JsonObject();
        current = Settings.defaults().normalize();
    }

    /**
     * Moves the file aside as {@code settings.v&lt;N&gt;.json}. A second reset from the same schema
     * version must not destroy the first backup, so an occupied name gets a {@code -2}, {@code -3}
     * … suffix; only when every candidate is taken is the older archive replaced.
     */
    private Path archive(Path file, int version) {
        Path first = explicitFile != null
                ? file.resolveSibling("settings.v" + version + ".json")
                : SavePaths.settingsArchiveFile(version);
        Path target = first;
        for (int n = 2; n <= ARCHIVE_ATTEMPTS && Files.exists(target); n++) {
            target = first.resolveSibling("settings.v" + version + "-" + n + ".json");
        }
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The schema version a tree declares. §4 says "missing keys default, unknown keys kept;
     * version mismatch -&gt; reset", so an absent {@code version} is a missing key like any other
     * and defaults to the current one — only a value that is present and different resets the
     * file. A present but non-numeric value is a mismatch, because it cannot have been written by
     * any build.
     */
    private static int versionOf(JsonObject tree) {
        JsonElement value = tree.get("version");
        if (value == null) {
            return Settings.VERSION;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Reads the file, or returns {@code null} when it cannot be read (an unreadable file is
     * treated exactly like an unparseable one).
     *
     * @param file the file, already known to exist
     * @return the text or {@code null}
     */
    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
