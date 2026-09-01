package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProfileSchema;
import io.github.michelbr84.flapforge.progression.Statistics;
import io.github.michelbr84.flapforge.support.DirectExecutor;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The load and write policy of {@link SaveManager} (D15, E21, E22). */
class SaveManagerTest {

    private static final long NOW = 1_756_684_800_000L;

    @TempDir
    Path home;

    private DirectExecutor executor;
    private FixedTimeSource time;
    private SaveManager manager;
    private boolean realHomeExistedBefore;
    private Path realHome;

    @BeforeEach
    void setUp() {
        SavePaths.clearOverride();
        realHome = SavePaths.profileDir();
        realHomeExistedBefore = Files.exists(realHome);
        SaveFixtures.useTemporaryHome(home);
        executor = new DirectExecutor();
        time = new FixedTimeSource(NOW);
        manager = new SaveManager(executor, time);
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

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private JsonObject tree(Path file) throws IOException {
        JsonObject parsed = JsonCodec.parseObject(read(file));
        assertNotNull(parsed, "the manager must write valid JSON");
        return parsed;
    }

    @Test
    void everyPathStaysInsideTheTemporaryHome() {
        assertTrue(manager.file().startsWith(home));
        assertTrue(manager.backupFile().startsWith(home));
        assertTrue(manager.backupsDir().startsWith(home));
    }

    @Test
    void aMissingFileStartsAFreshProfile() {
        SaveManager.LoadResult result = manager.load();
        assertEquals(SaveManager.Status.NEW_PROFILE, result.status());
        assertFalse(result.hasNotice());
        assertEquals(NOW, result.profile().createdAtEpochMs, "the clock is the injected one");
        assertEquals(PlayerProfile.DEFAULT_UNLOCKED.size(), result.profile().unlocked.size());
        assertFalse(Files.exists(saveFile()), "loading must not create a file");
    }

    @Test
    void roundTripKeepsEveryField() {
        manager.load();
        PlayerProfile profile = manager.profile();
        profile.wallet.put(PlayerProfile.CURRENCY_COINS, 1234L);
        profile.xp = 555;
        profile.level = 4;
        profile.abilityLevelCap = 3;
        profile.passiveSlotBonus = 1;
        profile.upgrades.put("feather_1", 2);
        profile.abilityLevels.put("shield", 1);
        profile.unlock("bird:swift");
        profile.challenge("no_shield_1").attempts = 3;
        profile.challenge("no_shield_1").completed = true;
        profile.statistics.totalRuns = 9;
        profile.statistics.bestGatesByTier.put("hard", 21L);
        profile.selected.tierId = "hard";
        profile.lastSeed = 4242;
        assertTrue(manager.save());
        assertTrue(Files.isRegularFile(saveFile()));

        SaveManager reader = new SaveManager(new DirectExecutor(), new FixedTimeSource(NOW + 1));
        PlayerProfile loaded = reader.load().profile();
        assertEquals(1234L, loaded.wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(555, loaded.xp);
        assertEquals(4, loaded.level);
        assertEquals(3, loaded.abilityLevelCap);
        assertEquals(1, loaded.passiveSlotBonus);
        assertEquals(2, loaded.upgradeLevel("feather_1"));
        assertEquals(1, loaded.abilityLevel("shield"));
        assertTrue(loaded.isUnlocked("bird:swift"));
        assertEquals(3, loaded.challenges.get("no_shield_1").attempts);
        assertEquals(9, loaded.statistics.totalRuns);
        assertEquals(21L, loaded.statistics.bestGatesByTier.get("hard"));
        assertEquals("hard", loaded.selected.tierId);
        assertEquals(4242, loaded.lastSeed);
    }

    @Test
    void theEnvelopeCarriesTheSchemaVersionAndTheInjectedTimestamp() throws IOException {
        manager.stamp("0.1.0-SNAPSHOT", 1);
        manager.load();
        time.set(NOW + 90_000);
        manager.save();
        JsonObject root = tree(saveFile());
        assertEquals(SaveFile.VERSION, root.get(SaveFile.KEY_VERSION).getAsInt());
        assertEquals("0.1.0-SNAPSHOT", root.get(SaveFile.KEY_APP_VERSION).getAsString());
        assertEquals(1, root.get(SaveFile.KEY_CONTENT_VERSION).getAsInt());
        assertEquals(NOW + 90_000, root.get(SaveFile.KEY_SAVED_AT).getAsLong());
    }

    @Test
    void theFixtureIsInternallyConsistentAndNeedsNoRepair() {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        SaveManager.LoadResult result = manager.load();
        assertEquals(SaveManager.Status.LOADED, result.status());
        assertEquals(List.of(), result.repairs(),
                "fixtures/save_v1.json is the frozen v1 sample and must satisfy E15 as it stands");
        PlayerProfile profile = result.profile();
        assertEquals(320L, profile.wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(3, profile.level);
        assertEquals(34, profile.statistics.bestGates);
        assertEquals(1, profile.statistics.runHistory.size());
        assertEquals("2026-09-01", profile.daily.date);
    }

    @Test
    void unknownFieldsRoundTrip() throws IOException {
        SaveFixtures.copyTo("save_unknown_fields.json", saveFile());
        manager.load();
        manager.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 500L);
        manager.save();

        JsonObject root = tree(saveFile());
        assertEquals("written by a newer build",
                root.get("futureEnvelopeField").getAsString());
        JsonObject profile = root.getAsJsonObject("profile");
        assertEquals(7, profile.getAsJsonObject("futureProfileField")
                .getAsJsonObject("deep").get("value").getAsInt(),
                "an unknown field nested two levels deep must survive a save");
        assertEquals(1234,
                profile.getAsJsonObject("statistics").get("futureStatistic").getAsInt());
        assertEquals("cosmetic_pet",
                profile.getAsJsonObject("selected").get("futureSelection").getAsString());
        assertEquals("kept", profile.getAsJsonObject("daily").get("futureDailyField").getAsString());
        assertEquals(3, profile.getAsJsonObject("prestigeBaseline").get("futureBaselineField")
                .getAsInt(), "prestigeBaseline is a POJO, so E22 says it is merged, not replaced");
        assertEquals(500, profile.getAsJsonObject("wallet").get("coins").getAsInt(),
                "the known fields are still updated");
    }

    @Test
    void aHandAddedFieldSurvivesEvenAfterAReload() throws IOException {
        manager.load();
        manager.save();
        JsonObject edited = tree(saveFile());
        edited.getAsJsonObject("profile").addProperty("handEdited", "keep me");
        Files.writeString(saveFile(), JsonCodec.toJson(edited), StandardCharsets.UTF_8);

        SaveManager second = new SaveManager(new DirectExecutor(), time);
        second.load();
        second.save();
        assertEquals("keep me",
                tree(saveFile()).getAsJsonObject("profile").get("handEdited").getAsString());
    }

    @Test
    void mapAndListNodesAreReplacedWholesaleSoAPrestigeResetPersists() throws IOException {
        manager.load();
        PlayerProfile profile = manager.profile();
        profile.wallet.put(PlayerProfile.CURRENCY_COINS, 900L);
        profile.upgrades.put("feather_1", 3);
        profile.upgrades.put("glide_1", 1);
        profile.abilityLevels.put("shield", 2);
        profile.unlock("bird:swift");
        profile.statistics.bestGatesByTier.put("hard", 30L);
        manager.save();

        // A prestige: the wallet, the nodes and the ability levels are cleared (E23).
        profile.wallet.put(PlayerProfile.CURRENCY_COINS, 0L);
        profile.upgrades.clear();
        profile.abilityLevels.clear();
        profile.statistics.bestGatesByTier.remove("hard");
        profile.prestigeCount = 1;
        manager.save();

        JsonObject saved = tree(saveFile()).getAsJsonObject("profile");
        assertEquals(0, saved.getAsJsonObject("upgrades").size(),
                "a merged map would resurrect the nodes the reset removed");
        assertEquals(0, saved.getAsJsonObject("abilityLevels").size());
        assertEquals(0, saved.getAsJsonObject("wallet").get("coins").getAsInt());
        assertFalse(saved.getAsJsonObject("statistics").getAsJsonObject("bestGatesByTier")
                .has("hard"));

        SaveManager reader = new SaveManager(new DirectExecutor(), time);
        PlayerProfile reloaded = reader.load().profile();
        assertTrue(reloaded.upgrades.isEmpty());
        assertEquals(0L, reloaded.wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(1, reloaded.prestigeCount);
    }

    @Test
    void anIdRemovedByTheAliasStepDoesNotReappear() throws IOException {
        manager.load();
        manager.profile().upgrades.put("old_node", 2);
        manager.profile().unlock("bird:starter");
        manager.save();

        SaveManager second = new SaveManager(new DirectExecutor(), time);
        second.aliasStep(tree -> {
            JsonObject profile = tree.getAsJsonObject("profile");
            profile.getAsJsonObject("upgrades").remove("old_node");
            profile.getAsJsonArray("unlocked").remove(
                    JsonCodec.toTree("bird:starter"));
            return tree;
        });
        PlayerProfile reconciled = second.load().profile();
        assertEquals(0, reconciled.upgradeLevel("old_node"));
        assertFalse(reconciled.isUnlocked("bird:starter"));
        second.save();

        JsonObject profile = tree(saveFile()).getAsJsonObject("profile");
        assertFalse(profile.getAsJsonObject("upgrades").has("old_node"),
                "the alias removal must not be undone by the overlay");
        assertFalse(profile.getAsJsonArray("unlocked").contains(JsonCodec.toTree("bird:starter")));
    }

    @Test
    void theRunHistoryCapSurvivesAWrite() throws IOException {
        manager.load();
        Statistics stats = manager.profile().statistics;
        for (int i = 0; i < Statistics.RUN_HISTORY_LIMIT + 5; i++) {
            Statistics.RunHistoryEntry entry = new Statistics.RunHistoryEntry();
            entry.seed = i;
            stats.addHistory(entry);
        }
        manager.save();
        assertEquals(Statistics.RUN_HISTORY_LIMIT, tree(saveFile()).getAsJsonObject("profile")
                .getAsJsonObject("statistics").getAsJsonArray("runHistory").size());
    }

    @Test
    void crashLeavesOldFileIntact() throws IOException {
        manager.load();
        manager.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 100L);
        manager.save();
        String good = read(saveFile());

        manager.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 200L);
        manager.failurePoint(AtomicFiles.FailurePoint.BEFORE_MOVE);
        manager.save();
        assertEquals(AtomicFiles.Outcome.IO_FAILED, manager.lastWrite().outcome());
        assertEquals(good, read(saveFile()), "the previous save must be untouched");
        assertFalse(Files.exists(saveFile().resolveSibling(
                saveFile().getFileName() + AtomicFiles.TEMP_SUFFIX)),
                "the temporary file must be cleaned up");

        manager.failurePoint(AtomicFiles.FailurePoint.AFTER_TMP_WRITE);
        manager.save();
        assertEquals(good, read(saveFile()));

        SaveManager reader = new SaveManager(new DirectExecutor(), time);
        assertEquals(SaveManager.Status.LOADED, reader.load().status(),
                "the file left behind by a crash must still load");
        assertEquals(100L, reader.profile().wallet.get(PlayerProfile.CURRENCY_COINS));
    }

    @Test
    void aFailedWriteIsDrainedExactlyOnce() {
        manager.load();
        manager.failurePoint(AtomicFiles.FailurePoint.BEFORE_MOVE);
        manager.save();
        AtomicFiles.WriteResult result = manager.pollCompletedWrite();
        assertNotNull(result);
        assertFalse(result.ok());
        assertNull(manager.pollCompletedWrite(), "each result is handed out once");
    }

    @Test
    void theBackupIsCopiedOncePerSessionFromThePreSessionFile() throws IOException {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        String before = read(saveFile());
        manager.load();
        assertTrue(manager.backupCopied());
        Path backup = home.resolve(SavePaths.SAVE_BACKUP_FILE);
        assertTrue(Files.isRegularFile(backup));
        assertEquals(before, read(backup), "the backup is the file as this session found it");

        manager.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 1L);
        manager.save();
        manager.load();
        assertEquals(before, read(backup),
                "one copy per session: a later save must not overwrite yesterday's backup");
    }

    @Test
    void flushReturnsWhenNothingIsPending() {
        manager.load();
        manager.save();
        assertEquals(0, manager.pendingWrites());
        assertTrue(manager.flush(1000), "a direct executor has already finished the write");
    }

    @Test
    void aRejectedWriteIsReportedAndNotLeftPending() {
        SaveManager rejecting = new SaveManager(command -> {
            throw new IllegalStateException("executor is shut down");
        }, time);
        rejecting.load();
        assertFalse(rejecting.save());
        assertEquals(AtomicFiles.Outcome.IO_FAILED, rejecting.lastWrite().outcome());
        assertEquals(0, rejecting.pendingWrites());
        assertTrue(rejecting.flush(0));
    }

    @Test
    void normalizationRepairsAnInconsistentSaveOnLoad() throws IOException {
        String broken = """
                {"version": 1, "profile": {
                  "wallet": {"coins": -50},
                  "level": 0,
                  "unlocked": ["bird:classic", "bird:classic", "upgrade:feather_1"],
                  "upgrades": {"feather_1": 2},
                  "abilityLevels": {"shield": 1},
                  "challenges": {"no_shield_1": {"completed": true, "bestGates": 4, "attempts": 1}},
                  "selected": {"birdId": "classic", "paletteId": "default",
                               "worldId": "green_fields", "tierId": "normal",
                               "activeAbilityId": "double_flap", "passiveAbilityIds": []}}}
                """;
        Files.writeString(saveFile(), broken, StandardCharsets.UTF_8);
        SaveManager withSchema = new SaveManager(executor, time)
                .schema(ProfileSchema.builder().upgradeNode("feather_1", "flight").build());
        SaveManager.LoadResult result = withSchema.load();
        PlayerProfile profile = result.profile();
        assertFalse(result.repairs().isEmpty(), "the repairs are reported, not silent");
        assertEquals(0L, profile.wallet.get(PlayerProfile.CURRENCY_COINS));
        assertEquals(1, profile.level);
        assertTrue(profile.isUnlocked("ability:shield"), "E15: abilityLevels imply the unlock");
        assertTrue(profile.isUnlocked("challenge:no_shield_1"));
        assertTrue(profile.isUnlocked("tree:flight"), "E21: an owned node implies its tree");
        assertFalse(profile.isUnlocked("upgrade:feather_1"), "E21: nodes are not unlockables");
        assertEquals(1, profile.unlocked.stream().filter("bird:classic"::equals).count());
    }

    @Test
    void aSelectionTheContentNoLongerKnowsFallsBackToTheDefault() throws IOException {
        String save = """
                {"version": 1, "profile": {
                  "unlocked": ["bird:classic"],
                  "selected": {"birdId": "deleted_bird", "paletteId": "gone",
                               "worldId": "atlantis", "tierId": "impossible",
                               "activeAbilityId": "telekinesis", "passiveAbilityIds": ["ghost"]}}}
                """;
        Files.writeString(saveFile(), save, StandardCharsets.UTF_8);
        ProfileSchema schema = ProfileSchema.builder()
                .bird("classic", List.of("default", "ember"))
                .worlds(List.of("green_fields"))
                .tiers(List.of("normal", "hard"))
                .abilities(List.of("double_flap", "shield"))
                .build();

        PlayerProfile profile = new SaveManager(executor, time).schema(schema).load().profile();

        assertEquals(PlayerProfile.DEFAULT_BIRD, profile.selected.birdId);
        assertEquals(PlayerProfile.DEFAULT_PALETTE, profile.selected.paletteId);
        assertEquals(PlayerProfile.DEFAULT_WORLD, profile.selected.worldId);
        assertEquals(PlayerProfile.DEFAULT_TIER, profile.selected.tierId);
        assertEquals(PlayerProfile.DEFAULT_ACTIVE_ABILITY, profile.selected.activeAbilityId);
        assertEquals(List.of(), profile.selected.passiveAbilityIds,
                "an unknown passive is dropped, not kept as a dangling id");
        assertTrue(profile.isUnlocked("world:green_fields"),
                "the repaired selection is then implied by the unlocks (E15)");
    }

    @Test
    void holdAdoptsAProfileWithoutWriting() {
        manager.load();
        PlayerProfile replacement = PlayerProfile.fresh(NOW);
        replacement.wallet.put(PlayerProfile.CURRENCY_COINS, 7L);
        manager.hold(replacement);
        assertEquals(7L, manager.profile().wallet.get(PlayerProfile.CURRENCY_COINS));
        assertFalse(Files.exists(saveFile()), "hold does not write");
    }

    @Test
    void resetToFreshMovesTheOldSaveAsideInsteadOfDeletingIt() throws IOException {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        manager.load();
        SaveManager.LoadResult reset = manager.resetToFresh();
        assertEquals(SaveManager.Status.NEW_PROFILE, reset.status());
        assertNotNull(reset.quarantined());
        assertTrue(Files.isRegularFile(reset.quarantined()));
        assertFalse(Files.exists(saveFile()));
        assertEquals(0L, reset.profile().wallet.get(PlayerProfile.CURRENCY_COINS));
        manager.save();
        assertEquals(0, tree(saveFile()).getAsJsonObject("profile")
                .getAsJsonObject("wallet").get("coins").getAsInt());
    }

    /**
     * The backup goes aside with the save, and is kept. Leaving it would undo the reset on the
     * next launch, which now recovers a missing {@code save.json} from {@code save.json.bak}.
     */
    @Test
    void resetToFreshMovesTheBackupAsideTooSoTheResetSticks() throws IOException {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        manager.load();
        assertTrue(Files.isRegularFile(backupFile()), "the load made a backup");

        manager.resetToFresh();

        assertFalse(Files.exists(backupFile()), "the backup was moved aside as well");
        try (Stream<Path> files = Files.list(home)) {
            assertEquals(1, files.filter(f -> f.getFileName().toString()
                    .startsWith("save.bak.reset-")).count(), "and it was kept, not deleted");
        }
        SaveManager next = new SaveManager(new DirectExecutor(), time);
        assertEquals(SaveManager.Status.NEW_PROFILE, next.load().status(),
                "the next session really starts fresh");
    }

    /**
     * The once-per-session backup goes through {@link AtomicFiles}: a plain {@code Files.copy}
     * truncates the destination first, so a crash in the middle of it destroys the previous good
     * backup. A copy that failed must also leave the flag down, so the next load tries again
     * instead of silently skipping the backup for the rest of the session.
     */
    @Test
    void aFailedBackupCopyIsNotRecordedAsDone() throws IOException {
        SaveFixtures.copyTo("save_v1.json", saveFile());
        String before = read(saveFile());
        try {
            Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("r-x------"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("the directory cannot be made read-only here: " + e);
        }
        Assumptions.assumeFalse(Files.isWritable(home), "still writable (running as root?)");
        SaveManager.LoadResult result;
        try {
            result = manager.load();
        } finally {
            Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"));
        }

        assertEquals(SaveManager.Status.LOADED, result.status(), "the save itself is fine");
        assertFalse(manager.backupCopied(), "a failed copy is not a copy");
        assertFalse(Files.exists(backupFile()), "and no half a backup was left behind");
        assertEquals(before, read(saveFile()), "the save is untouched");
    }

    /**
     * The write path coalesces: a burst while one write runs collapses into the newest state, and
     * every slot is still accounted for. The executor used to do the collapsing by discarding the
     * queued task, which left {@link SaveManager#pendingWrites()} permanently above 0 and
     * {@link SaveManager#flush(long)} unable to return true again.
     */
    @Test
    void aBurstOfSavesCoalescesAndStillSettlesTheBookkeeping() throws IOException {
        QueuedExecutor queue = new QueuedExecutor();
        SaveManager coalescing = new SaveManager(queue, time);
        coalescing.load();

        coalescing.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 1L);
        assertTrue(coalescing.save());
        coalescing.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 2L);
        assertTrue(coalescing.save());
        coalescing.profile().wallet.put(PlayerProfile.CURRENCY_COINS, 3L);
        assertTrue(coalescing.save());
        assertEquals(1, queue.size(), "one task for the whole burst");
        assertEquals(1, coalescing.pendingWrites(), "and one state owed to the disk");

        assertEquals(1, queue.runAll());

        assertEquals(0, coalescing.pendingWrites());
        assertTrue(coalescing.flush(0), "nothing is pending any more");
        assertEquals(3, tree(saveFile()).getAsJsonObject("profile").getAsJsonObject("wallet")
                .get("coins").getAsInt(), "the newest state is the one on the disk");
    }

    /** The same, against a real worker thread: {@code flush} waits for it and then returns. */
    @Test
    void flushWaitsForARunningWriteAndReturnsTrue() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "save-test");
            t.setDaemon(true);
            return t;
        });
        try {
            worker.execute(() -> {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            SaveManager blocked = new SaveManager(worker, time);
            blocked.load();
            for (int i = 1; i <= 3; i++) {
                blocked.profile().wallet.put(PlayerProfile.CURRENCY_COINS, (long) i);
                assertTrue(blocked.save());
            }
            assertFalse(blocked.flush(50), "nothing can land while the worker is blocked");
            release.countDown();
            assertTrue(blocked.flush(5_000), "and everything settles once it is not");
            assertEquals(0, blocked.pendingWrites());
            assertEquals(3, tree(saveFile()).getAsJsonObject("profile").getAsJsonObject("wallet")
                    .get("coins").getAsInt());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    /** An executor that holds every task until it is told to run them. */
    private static final class QueuedExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        int runAll() {
            int count = 0;
            while (!tasks.isEmpty()) {
                tasks.remove(0).run();
                count++;
            }
            return count;
        }
    }
}
