package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The load and write policy of {@link SettingsStore} (§4, D15, E22). */
class SettingsStoreTest {

    @TempDir
    Path home;

    private DirectExecutor executor;
    private SettingsStore store;

    @BeforeEach
    void setUp() {
        SavePaths.override(home);
        executor = new DirectExecutor();
        store = new SettingsStore(executor);
    }

    @AfterEach
    void tearDown() {
        SavePaths.clearOverride();
    }

    private Path file() {
        return home.resolve(SavePaths.SETTINGS_FILE);
    }

    private void write(String json) throws IOException {
        Files.writeString(file(), json, StandardCharsets.UTF_8);
    }

    private String read() throws IOException {
        return Files.readString(file(), StandardCharsets.UTF_8);
    }

    private JsonObject tree() throws IOException {
        JsonObject parsed = JsonCodec.parseObject(read());
        assertNotNull(parsed, "the store must write valid JSON");
        return parsed;
    }

    @Test
    void everyPathFollowsTheOverriddenHome() {
        assertEquals(home.resolve(SavePaths.SETTINGS_FILE), store.file());
        assertEquals(home, SavePaths.profileDir());
        assertEquals(home.resolve(SavePaths.SAVE_FILE), SavePaths.saveFile());
        assertEquals(home.resolve("settings.v3.json"), SavePaths.settingsArchiveFile(3));
    }

    @Test
    void aMissingFileYieldsTheDefaults() {
        SettingsStore.LoadResult result = store.load();

        assertEquals(Settings.defaults().normalize(), result.settings());
        assertEquals(SettingsStore.Notice.NONE, result.notice());
        assertFalse(result.hasNotice());
        assertNull(result.archived());
        assertFalse(Files.exists(file()), "loading must not create the file");
    }

    @Test
    void savedSettingsComeBackUnchanged() throws IOException {
        Settings settings = Settings.defaults();
        settings.language = "pt_BR";
        settings.masterVolume = 0.25;
        settings.muted = true;
        settings.maxFps = 144;
        settings.textScale = 1.25;
        settings.colorBlindPalette = "deuteranopia";
        settings.holdToFlap = true;
        settings.withBindings(settings.bindings().withBinding(InputAction.FLAP, List.of(32, 87)));

        store.save(settings);

        assertTrue(store.lastWrite().ok(), store.lastWrite().detail());
        assertEquals(1, executor.executed(), "the write goes through the injected executor");
        Settings reloaded = new SettingsStore(new DirectExecutor()).load().settings();
        assertEquals(settings.normalize(), reloaded);
        assertEquals(List.of(32, 87), reloaded.bindings().keysFor(InputAction.FLAP));
        assertEquals(Settings.VERSION, tree().get("version").getAsInt());
    }

    @Test
    void unknownTopLevelAndNestedKeysSurviveASave() throws IOException {
        write("{\"version\": 1, \"masterVolume\": 0.5, \"experimentalFlag\": true,"
                + " \"experimental\": {\"depth\": {\"value\": 3}}}");

        Settings settings = store.load().settings();
        assertEquals(0.5, settings.masterVolume);
        settings.masterVolume = 0.1;
        store.save(settings);

        JsonObject written = tree();
        assertEquals(0.1, written.get("masterVolume").getAsDouble());
        assertTrue(written.get("experimentalFlag").getAsBoolean(), "unknown root key was dropped");
        assertEquals(3, written.getAsJsonObject("experimental").getAsJsonObject("depth")
                .get("value").getAsInt(), "unknown nested key was dropped");
    }

    @Test
    void aVersionMismatchResetsAndKeepsTheOldFile() throws IOException {
        write("{\"version\": 99, \"masterVolume\": 0.11}");

        SettingsStore.LoadResult result = store.load();

        assertEquals(SettingsStore.Notice.RESET_VERSION_MISMATCH, result.notice());
        assertEquals(Settings.defaults().normalize(), result.settings());
        Path archived = home.resolve("settings.v99.json");
        assertEquals(archived, result.archived());
        assertTrue(Files.exists(archived), "the old file must be kept for the player");
        assertTrue(Files.readString(archived).contains("0.11"));
        assertFalse(Files.exists(file()), "the mismatched file was moved, not copied");
        assertEquals(SettingsStore.Notice.RESET_VERSION_MISMATCH, store.notice());
        store.clearNotice();
        assertEquals(SettingsStore.Notice.NONE, store.notice());
    }

    @Test
    void anUnreadableFileResetsToTheDefaults() throws IOException {
        write("{ this is not json");

        SettingsStore.LoadResult result = store.load();

        assertEquals(SettingsStore.Notice.RESET_UNREADABLE, result.notice());
        assertEquals(Settings.defaults().normalize(), result.settings());
        assertEquals(home.resolve("settings.v0.json"), result.archived());
        assertTrue(Files.exists(home.resolve("settings.v0.json")));
    }

    @Test
    void aFileOfTheWrongShapeResetsToTheDefaults() throws IOException {
        write("{\"version\": 1, \"keyBindings\": 7}");

        SettingsStore.LoadResult result = store.load();

        assertEquals(SettingsStore.Notice.RESET_UNREADABLE, result.notice());
        assertEquals(Settings.defaults().normalize(), result.settings());
    }

    @Test
    void outOfRangeValuesAreClampedOnLoadAndOnSave() throws IOException {
        write("{\"version\": 1, \"masterVolume\": 4.5, \"sfxVolume\": -2.0,"
                + " \"musicVolume\": 1.5, \"textScale\": 9.0, \"maxFps\": 1000,"
                + " \"language\": \"klingon\", \"colorBlindPalette\": \"rainbow\"}");

        Settings loaded = store.load().settings();

        assertEquals(1.0, loaded.masterVolume);
        assertEquals(0.0, loaded.sfxVolume);
        assertEquals(1.0, loaded.musicVolume);
        assertEquals(Settings.MAX_TEXT_SCALE, loaded.textScale);
        assertEquals(Settings.MAX_FPS, loaded.maxFps);
        assertEquals(Settings.LANGUAGE_AUTO, loaded.language);
        assertEquals(Settings.PALETTE_NONE, loaded.colorBlindPalette);

        Settings hostile = Settings.defaults();
        hostile.textScale = 0.1;
        hostile.maxFps = 1;
        hostile.masterVolume = Double.NaN;
        store.save(hostile);

        JsonObject written = tree();
        assertEquals(Settings.MIN_TEXT_SCALE, written.get("textScale").getAsDouble());
        assertEquals(Settings.MIN_FPS, written.get("maxFps").getAsInt());
        assertEquals(0.8, written.get("masterVolume").getAsDouble());
    }

    @Test
    void aFailedWriteLeavesThePreviousFileIntact() throws IOException {
        Settings settings = Settings.defaults();
        settings.masterVolume = 0.4;
        store.save(settings);
        assertTrue(store.lastWrite().ok());

        store.failurePoint(AtomicFiles.FailurePoint.BEFORE_MOVE);
        settings.masterVolume = 0.9;
        store.save(settings);

        assertEquals(AtomicFiles.Outcome.IO_FAILED, store.lastWrite().outcome());
        assertEquals(0.4, tree().get("masterVolume").getAsDouble(),
                "the file on disk must still be the last good one");
    }

    @Test
    void aSimulatedCrashAfterTheMoveStillLeavesAReadableFile() throws IOException {
        store.save(Settings.defaults());
        store.failurePoint(AtomicFiles.FailurePoint.AFTER_MOVE);
        Settings settings = Settings.defaults();
        settings.showFps = true;
        store.save(settings);

        assertEquals(AtomicFiles.Outcome.IO_FAILED, store.lastWrite().outcome());
        assertTrue(tree().get("showFps").getAsBoolean(), "the move itself had already happened");
    }

    @Test
    void theStoreNeverThrows() throws IOException {
        SettingsStore rejecting = new SettingsStore(command -> {
            throw new RejectedExecutionException("no room");
        });
        assertEquals(Settings.VERSION, rejecting.save(Settings.defaults()).version);
        assertEquals(AtomicFiles.Outcome.IO_FAILED, rejecting.lastWrite().outcome());

        Files.createDirectories(file());
        Files.writeString(file().resolve("blocker.txt"), "keeps the directory non-empty");
        SettingsStore overADirectory = new SettingsStore(new DirectExecutor());
        assertEquals(Settings.defaults().normalize(), overADirectory.load().settings());
        overADirectory.save(Settings.defaults());
        assertEquals(AtomicFiles.Outcome.IO_FAILED, overADirectory.lastWrite().outcome());
    }

    @Test
    void aFileWithoutAVersionKeepsItsValues() throws IOException {
        // Section 4: "missing keys default, unknown keys kept; version mismatch -> reset". An
        // absent version is a missing key, not a mismatch, so a hand-edited file must survive.
        write("{\"masterVolume\": 0.33, \"showFps\": true}");

        SettingsStore.LoadResult result = store.load();

        assertEquals(SettingsStore.Notice.NONE, result.notice());
        assertNull(result.archived());
        assertEquals(0.33, result.settings().masterVolume);
        assertTrue(result.settings().showFps);
        assertTrue(Files.exists(file()), "nothing was archived");
    }

    @Test
    void aSecondResetKeepsTheFirstArchive() throws IOException {
        write("{\"version\": 99, \"masterVolume\": 0.11}");
        assertEquals(home.resolve("settings.v99.json"), store.load().archived());

        write("{\"version\": 99, \"masterVolume\": 0.22}");
        Path second = new SettingsStore(new DirectExecutor()).load().archived();

        assertEquals(home.resolve("settings.v99-2.json"), second);
        assertTrue(Files.readString(home.resolve("settings.v99.json")).contains("0.11"),
                "the first backup must survive the second reset");
        assertTrue(Files.readString(second).contains("0.22"));
    }

    @Test
    void anAsynchronousWriteReportsItsOwnOutcomeAndNotThePreviousOne() {
        Deque<Runnable> pending = new ArrayDeque<>();
        SettingsStore async = new SettingsStore(pending::add, home.resolve("async.json"));

        async.save(Settings.defaults());
        assertNull(async.lastWrite(), "nothing has been written yet");
        assertNull(async.pollCompletedWrite(), "and nothing has finished either");

        pending.removeFirst().run();
        AtomicFiles.WriteResult first = async.pollCompletedWrite();
        assertNotNull(first, "the finished write is queued for the loop thread");
        assertTrue(first.ok(), first.detail());
        assertNull(async.pollCompletedWrite(), "each result is handed out exactly once");

        async.failurePoint(AtomicFiles.FailurePoint.BEFORE_MOVE);
        async.save(Settings.defaults());
        assertNull(async.pollCompletedWrite(), "the failing write has not run yet");
        pending.removeFirst().run();
        AtomicFiles.WriteResult second = async.pollCompletedWrite();
        assertNotNull(second);
        assertEquals(AtomicFiles.Outcome.IO_FAILED, second.outcome(),
                "the failure is reported against the write that failed");
        assertNull(async.pollCompletedWrite());
    }

    @Test
    void holdAdoptsAStateWithoutWritingIt() throws IOException {
        Settings live = Settings.defaults();
        live.masterVolume = 0.42;

        assertEquals(0.42, store.hold(live).masterVolume);
        assertEquals(0.42, store.settings().masterVolume, "the store reports what is in force");
        assertFalse(Files.exists(file()), "hold() must not touch the file");
        assertEquals(0, executor.executed(), "and must not submit a write");

        store.save(store.settings());
        assertEquals(0.42, tree().get("masterVolume").getAsDouble());
    }

    @Test
    void onlyTheRebindableActionsAreWritten() throws IOException {
        store.save(Settings.defaults());

        JsonObject bindings = tree().getAsJsonObject("keyBindings");
        assertEquals(List.of("FLAP", "ABILITY", "PAUSE", "CONFIRM", "MUTE", "DEBUG", "FULLSCREEN"),
                List.copyOf(bindings.keySet()),
                "section 4 lists exactly the seven actions the settings screen can rebind");
    }

    @Test
    void anExplicitFileIsUsedInsteadOfTheProfileDirectory() throws IOException {
        Path custom = home.resolve("nested").resolve("custom.json");
        SettingsStore explicitStore = new SettingsStore(new DirectExecutor(), custom);

        explicitStore.save(Settings.defaults());

        assertTrue(Files.exists(custom), "the parent directory is created lazily");
        assertEquals(custom, explicitStore.file());
    }
}
