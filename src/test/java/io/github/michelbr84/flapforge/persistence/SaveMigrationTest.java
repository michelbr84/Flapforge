package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SaveMigrator} and the migration half of {@link SaveManager}'s load path (D15).
 *
 * <p>Schema version 1 is the first version there has ever been, so the shipped chain is empty. The
 * contract is kept alive by a synthetic step that is exactly the shape a real one will have: v2
 * moves the flat {@code profile.coins} field into the {@code profile.wallet} map. It reads a field
 * the current {@link PlayerProfile} does not have, which is the whole reason migrations work on
 * the parsed tree rather than on the bound object.
 */
class SaveMigrationTest {

    private static final long NOW = 1_756_684_800_000L;

    /** The synthetic step: {@code profile.coins} becomes {@code profile.wallet.coins}. */
    private static Migration coinsIntoWallet() {
        return new Migration(1, 2, tree -> {
            JsonObject profile = tree.getAsJsonObject("profile");
            if (profile == null || !profile.has("coins")) {
                return tree;
            }
            long coins = profile.get("coins").getAsLong();
            profile.remove("coins");
            JsonObject wallet = profile.has("wallet")
                    ? profile.getAsJsonObject("wallet") : new JsonObject();
            wallet.addProperty("coins", coins);
            profile.add("wallet", wallet);
            return tree;
        });
    }

    /** A second synthetic step, used only to prove the chain runs in order. */
    private static Migration stampLastMigration() {
        return new Migration(2, 3, tree -> {
            tree.addProperty("migratedBy", tree.has("migratedBy")
                    ? tree.get("migratedBy").getAsString() + ",2->3" : "2->3");
            return tree;
        });
    }

    @TempDir
    Path home;

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

    private static JsonObject fixtureTree(String name) {
        JsonObject tree = JsonCodec.parseObject(SaveFixtures.read(name));
        assertNotNull(tree, "fixture " + name + " must be a JSON object");
        return tree;
    }

    @Test
    void theSyntheticStepTurnsTheInputFixtureIntoTheExpectedOne() {
        SaveMigrator migrator = new SaveMigrator(List.of(coinsIntoWallet()));
        SaveMigrator.Result result = migrator.migrate(fixtureTree("save_v1_to_v2/in.json"), 1, 2);

        assertTrue(result.complete());
        assertTrue(result.migrated());
        assertEquals(List.of("1 -> 2"), result.applied());
        assertEquals(2, result.version());
        assertEquals(fixtureTree("save_v1_to_v2/expected.json"), result.tree());
    }

    @Test
    void aMigrationNeverMutatesTheTreeItWasGiven() {
        JsonObject original = fixtureTree("save_v1_to_v2/in.json");
        JsonObject copy = original.deepCopy();
        new SaveMigrator(List.of(coinsIntoWallet())).migrate(original, 1, 2);
        assertEquals(copy, original, "the caller's tree is the one it can still fall back on");
    }

    @Test
    void stepsRunInOrderEvenWhenDeclaredOutOfOrder() {
        SaveMigrator migrator =
                new SaveMigrator(List.of(stampLastMigration(), coinsIntoWallet()));
        assertEquals(List.of(1, 2), migrator.migrations().stream().map(Migration::from).toList());

        SaveMigrator.Result result = migrator.migrate(fixtureTree("save_v1_to_v2/in.json"), 1, 3);
        assertEquals(List.of("1 -> 2", "2 -> 3"), result.applied());
        assertEquals(3, result.version());
        assertEquals(3, result.tree().get(SaveFile.KEY_VERSION).getAsInt());
        assertEquals(250, result.tree().getAsJsonObject("profile")
                .getAsJsonObject("wallet").get("coins").getAsInt());
        assertEquals("2->3", result.tree().get("migratedBy").getAsString());
    }

    @Test
    void migratingAnUpToDateTreeIsANoOp() {
        SaveMigrator migrator = new SaveMigrator(List.of(coinsIntoWallet()));
        JsonObject current = fixtureTree("save_v1_to_v2/expected.json");
        SaveMigrator.Result result = migrator.migrate(current, 2, 2);

        assertFalse(result.migrated());
        assertTrue(result.complete());
        assertEquals(current, result.tree());
    }

    @Test
    void runningTheChainTwiceChangesNothingTheSecondTime() {
        SaveMigrator migrator = new SaveMigrator(List.of(coinsIntoWallet()));
        SaveMigrator.Result once = migrator.migrate(fixtureTree("save_v1_to_v2/in.json"), 1, 2);
        SaveMigrator.Result twice = migrator.migrate(once.tree(), once.version(), 2);

        assertFalse(twice.migrated());
        assertEquals(once.tree(), twice.tree());
    }

    @Test
    void aMissingStepIsReportedInsteadOfGuessedAt() {
        SaveMigrator migrator = new SaveMigrator(List.of(stampLastMigration()));
        assertFalse(migrator.canMigrate(1, 3));
        SaveMigrator.Result result = migrator.migrate(fixtureTree("save_v1_to_v2/in.json"), 1, 3);
        assertFalse(result.complete());
        assertEquals(1, result.version());
        assertEquals(List.of(), result.applied());
    }

    @Test
    void aChainMayNotHaveTwoStepsFromTheSameVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaveMigrator(List.of(coinsIntoWallet(), coinsIntoWallet())));
    }

    @Test
    void aMigrationMustMoveForward() {
        assertThrows(IllegalArgumentException.class, () -> new Migration(2, 1, tree -> tree));
        assertThrows(IllegalArgumentException.class, () -> new Migration(1, 1, tree -> tree));
        assertThrows(NullPointerException.class, () -> new Migration(1, 2, null));
    }

    @Test
    void theShippedChainIsEmptyAndCarriesVersionOneUnchanged() {
        SaveMigrator standard = SaveMigrator.standard();
        assertEquals(List.of(), standard.migrations());
        assertTrue(standard.canMigrate(SaveFile.VERSION, SaveFile.VERSION));
    }

    @Test
    void loadMigratesAndWritesThePreMigrationBackupFirst() throws IOException {
        String original = SaveFixtures.read("save_v1_to_v2/in.json");
        Files.writeString(saveFile(), original, StandardCharsets.UTF_8);

        SaveManager manager = new SaveManager(new DirectExecutor(), time)
                .schemaVersion(2)
                .migrator(new SaveMigrator(List.of(coinsIntoWallet())));
        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.LOADED, result.status());
        assertTrue(result.migrated());
        assertEquals(1, result.migratedFrom());
        assertEquals(250L, result.profile().wallet.get(PlayerProfile.CURRENCY_COINS),
                "the migrated field reaches the bound profile");

        Path backup = home.resolve(SavePaths.BACKUP_DIR).resolve("save.v1.pre-migration.json");
        assertEquals(backup, result.preMigrationBackup());
        assertTrue(Files.isRegularFile(backup));
        assertEquals(original, Files.readString(backup, StandardCharsets.UTF_8),
                "the backup is the file exactly as it was before the step ran");
    }

    @Test
    void aSecondMigrationDoesNotOverwriteTheFirstPreMigrationBackup() throws IOException {
        Files.writeString(saveFile(), SaveFixtures.read("save_v1_to_v2/in.json"),
                StandardCharsets.UTF_8);
        Path backup = home.resolve(SavePaths.BACKUP_DIR).resolve("save.v1.pre-migration.json");
        Files.createDirectories(backup.getParent());
        Files.writeString(backup, "{\"version\": 1, \"kept\": true}", StandardCharsets.UTF_8);

        new SaveManager(new DirectExecutor(), time)
                .schemaVersion(2)
                .migrator(new SaveMigrator(List.of(coinsIntoWallet())))
                .load();

        assertEquals("{\"version\": 1, \"kept\": true}",
                Files.readString(backup, StandardCharsets.UTF_8),
                "the first pre-migration state is the one worth keeping");
    }

    @Test
    void aSaveThatCannotBeMigratedIsQuarantinedRatherThanGuessedAt() throws IOException {
        Files.writeString(saveFile(), SaveFixtures.read("save_v1_to_v2/in.json"),
                StandardCharsets.UTF_8);

        SaveManager manager = new SaveManager(new DirectExecutor(), time)
                .schemaVersion(3)
                .migrator(new SaveMigrator(List.of(stampLastMigration())));
        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.RESET_AFTER_CORRUPT, result.status());
        assertNotNull(result.quarantined());
        assertTrue(Files.isRegularFile(result.quarantined()));
    }

    @Test
    void aNewerVersionIsRefusedAndTheFileIsNotOverwritten() throws IOException {
        String original = SaveFixtures.read("save_future.json");
        Files.writeString(saveFile(), original, StandardCharsets.UTF_8);

        SaveManager manager = new SaveManager(new DirectExecutor(), time);
        SaveManager.LoadResult result = manager.load();

        assertEquals(SaveManager.Status.REFUSED_NEWER_VERSION, result.status());
        assertTrue(result.readOnly());
        assertTrue(manager.readOnly());
        assertTrue(result.detail().contains("without saving"));
        assertNull(result.quarantined(), "a newer save is not corrupt: it is left where it is");
        assertEquals(0L, result.profile().wallet.get(PlayerProfile.CURRENCY_COINS),
                "the session runs on a fresh profile it will never write");

        manager.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 4242L);
        assertFalse(manager.save(), "a refused session must not write");
        assertNull(manager.lastWrite());
        assertEquals(original, Files.readString(saveFile(), StandardCharsets.UTF_8),
                "the newer save must survive this session byte for byte");
        assertFalse(Files.exists(home.resolve(SavePaths.SAVE_BACKUP_FILE)),
                "and must not be copied over the backup either");
    }
}
