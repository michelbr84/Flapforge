package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The failure policy of {@link SaveManager} (D15): quarantine, then the backup, then a fresh
 * profile — and never a deleted file.
 */
class SaveCorruptRecoveryTest {

    private static final long NOW = 1_756_684_800_000L;

    @TempDir
    Path home;

    private SaveManager manager;
    private FixedTimeSource time;
    private Path realHome;
    private boolean realHomeExistedBefore;

    @BeforeEach
    void setUp() {
        SavePaths.clearOverride();
        realHome = SavePaths.profileDir();
        realHomeExistedBefore = Files.exists(realHome);
        SaveFixtures.useTemporaryHome(home);
        time = new FixedTimeSource(NOW);
        manager = new SaveManager(new DirectExecutor(), time);
    }

    @AfterEach
    void tearDown() {
        SavePaths.clearOverride();
        assertEquals(realHomeExistedBefore, Files.exists(realHome),
                "a save test must never create or delete the real profile directory " + realHome);
    }

    private Path saveFile() {
        return home.resolve(SavePaths.SAVE_FILE);
    }

    private Path backupFile() {
        return home.resolve(SavePaths.SAVE_BACKUP_FILE);
    }

    private static JsonObject tree(Path file) throws IOException {
        return JsonCodec.parseObject(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * Takes the read permission away, and skips the test where that cannot be done — a non-POSIX
     * file system, or a root user for whom the bit means nothing.
     */
    private static void assumeCanRevokeRead(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("-w-------"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("the read permission cannot be revoked here: " + e);
        }
        Assumptions.assumeFalse(Files.isReadable(file),
                "the file is still readable (running as root?)");
    }

    private List<Path> quarantineFiles() throws IOException {
        try (Stream<Path> files = Files.list(home)) {
            return files.filter(p -> p.getFileName().toString().startsWith("save.corrupt-"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void aCorruptSaveWithAGoodBackupIsRestoredAndQuarantined() throws IOException {
        SaveFixtures.copyTo("save_corrupt.json", saveFile());
        SaveFixtures.copyTo("save_v1.json", backupFile());
        String corrupt = Files.readString(saveFile(), StandardCharsets.UTF_8);

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESTORED_FROM_BACKUP, result.status());
        assertTrue(result.hasNotice(), "the player must be told the save was replaced");
        assertFalse(result.readOnly(), "a restored session still saves");
        assertEquals(320L, result.profile().wallet.get(PlayerProfile.CURRENCY_COINS),
                "the backup's profile is the one in force");
        assertEquals(34, result.profile().statistics.bestGates);

        assertNotNull(result.quarantined());
        assertEquals(home.resolve("save.corrupt-" + NOW + ".json"), result.quarantined());
        assertEquals(corrupt, Files.readString(result.quarantined(), StandardCharsets.UTF_8),
                "the unusable file is moved, never rewritten");
        assertEquals(1, quarantineFiles().size());
        assertTrue(Files.isRegularFile(saveFile()),
                "the recovered profile is written back before the session starts");
        assertEquals(320, tree(saveFile()).getAsJsonObject("profile").getAsJsonObject("wallet")
                .get("coins").getAsInt(), "and it is the backup's profile, not a fresh one");
    }

    /**
     * The recovery has to survive a player who reaches the menu and quits: nothing else in the
     * game writes a profile that has not changed, so if the load does not write it back the next
     * session finds no {@code save.json} at all.
     */
    @Test
    void aRestoredSessionWritesTheRecoveredProfileBackWithoutBeingAsked() throws IOException {
        SaveFixtures.copyTo("save_corrupt.json", saveFile());
        SaveFixtures.copyTo("save_v1.json", backupFile());
        manager.load();

        SaveManager reader = new SaveManager(new DirectExecutor(), time);
        SaveManager.LoadResult reloaded = reader.load();
        assertEquals(SaveManager.Status.LOADED, reloaded.status());
        assertEquals(320L, reloaded.profile().wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(1, quarantineFiles().size(), "recovering twice must not quarantine twice");
    }

    /**
     * {@code save.json} vanished (a half-finished sync, a stray delete) but {@code save.json.bak}
     * is right there. Starting a fresh profile on top of it would destroy the profile twice over:
     * once by ignoring it, and again when this session's first write replaced the backup.
     */
    @Test
    void aMissingSaveIsRecoveredFromTheBackup() throws IOException {
        SaveFixtures.copyTo("save_v1.json", backupFile());

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESTORED_FROM_BACKUP, result.status());
        assertTrue(result.hasNotice());
        assertNull(result.quarantined(), "there was nothing to quarantine");
        assertEquals(320L, result.profile().wallet.get(PlayerProfile.CURRENCY_COINS));
        assertTrue(Files.isRegularFile(saveFile()), "the backup is written back straight away");
        assertEquals(320, tree(saveFile()).getAsJsonObject("profile").getAsJsonObject("wallet")
                .get("coins").getAsInt());
    }

    @Test
    void aMissingSaveWithAnUnusableBackupStillStartsFresh() throws IOException {
        SaveFixtures.copyTo("save_corrupt.json", backupFile());

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.NEW_PROFILE, result.status());
        assertFalse(Files.exists(saveFile()), "a fresh profile is not written until it changes");
        assertEquals(List.of(), quarantineFiles(), "the backup is not quarantined either");
    }

    /**
     * A file that could not be <em>opened</em> says nothing about its content: an antivirus lock,
     * a cloud placeholder, a transient EIO. Quarantining it would turn a passing inconvenience
     * into a lost profile, so the file is left alone and the session refuses to write.
     */
    @Test
    void anUnreadableSaveIsLeftWhereItIsAndTheSessionNeverWrites() throws IOException {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        String before = Files.readString(saveFile(), StandardCharsets.UTF_8);
        assumeCanRevokeRead(saveFile());
        try {
            SaveManager.LoadResult result = manager.load();

            assertEquals(SaveManager.Status.UNREADABLE, result.status());
            assertTrue(result.readOnly(), "a save that could not be read is never overwritten");
            assertTrue(result.hasNotice());
            assertNull(result.quarantined());
            assertEquals(List.of(), quarantineFiles());
            assertFalse(manager.save(), "read-only mode refuses every write");
        } finally {
            Files.setPosixFilePermissions(saveFile(),
                    PosixFilePermissions.fromString("rw-------"));
        }
        assertEquals(before, Files.readString(saveFile(), StandardCharsets.UTF_8),
                "the file is byte for byte what it was");
    }

    @Test
    void anUnreadableSaveStillPlaysOnTheBackup() throws IOException {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        SaveFixtures.copyTo("save_v1.json", backupFile());
        assumeCanRevokeRead(saveFile());
        try {
            SaveManager.LoadResult result = manager.load();

            assertEquals(SaveManager.Status.UNREADABLE, result.status());
            assertEquals(320L, result.profile().wallet.get(PlayerProfile.CURRENCY_COINS),
                    "the backup carries the session");
            assertTrue(result.readOnly());
        } finally {
            Files.setPosixFilePermissions(saveFile(),
                    PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void aCorruptSaveAndACorruptBackupStartAFreshProfile() throws IOException {
        SaveFixtures.copyTo("save_corrupt.json", saveFile());
        SaveFixtures.copyTo("save_corrupt.json", backupFile());

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESET_AFTER_CORRUPT, result.status());
        assertTrue(result.hasNotice());
        assertEquals(NOW, result.profile().createdAtEpochMs);
        assertEquals(0L, result.profile().wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(1, quarantineFiles().size(), "the unusable save is still kept");
        assertTrue(Files.isRegularFile(backupFile()), "the unusable backup is left alone");
    }

    @Test
    void aCorruptSaveWithNoBackupStartsAFreshProfile() throws IOException {
        SaveFixtures.copyTo("save_corrupt.json", saveFile());

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESET_AFTER_CORRUPT, result.status());
        assertTrue(result.detail().contains("no backup"));
        assertEquals(1, quarantineFiles().size());
        assertFalse(Files.exists(backupFile()));
    }

    @Test
    void aProfileThatDoesNotFitTheSchemaCountsAsCorrupt() throws IOException {
        Files.writeString(saveFile(),
                "{\"version\": 1, \"profile\": {\"wallet\": \"not a map at all\"}}",
                StandardCharsets.UTF_8);

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESET_AFTER_CORRUPT, result.status());
        assertEquals(1, quarantineFiles().size());
    }

    @Test
    void aNonNumericVersionCountsAsCorrupt() throws IOException {
        Files.writeString(saveFile(), "{\"version\": \"one\", \"profile\": {}}",
                StandardCharsets.UTF_8);

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESET_AFTER_CORRUPT, result.status());
        assertEquals(1, quarantineFiles().size());
    }

    @Test
    void anEmptyFileCountsAsCorrupt() throws IOException {
        Files.writeString(saveFile(), "", StandardCharsets.UTF_8);

        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESET_AFTER_CORRUPT, result.status());
        assertEquals(1, quarantineFiles().size());
    }

    @Test
    void twoQuarantinesInTheSameMillisecondKeepBothFiles() throws IOException {
        SaveFixtures.copyTo("save_corrupt.json", saveFile());
        manager.load();
        SaveFixtures.copyTo("save_corrupt.json", saveFile());
        new SaveManager(new DirectExecutor(), time).load();

        List<Path> quarantined = quarantineFiles();
        assertEquals(2, quarantined.size(), "the second failure must not overwrite the first");
        assertTrue(quarantined.contains(home.resolve("save.corrupt-" + NOW + ".json")));
        assertTrue(quarantined.contains(home.resolve("save.corrupt-" + NOW + "-2.json")));
    }

    @Test
    void aFreshSessionNeverQuarantinesAnythingWhenThereIsNoFile() throws IOException {
        SaveManager.LoadResult result = manager.load();
        assertEquals(SaveManager.Status.NEW_PROFILE, result.status());
        assertNull(result.quarantined());
        assertEquals(List.of(), quarantineFiles());
    }
}
