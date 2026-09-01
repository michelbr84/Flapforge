package io.github.michelbr84.flapforge.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProfileSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reads and writes {@code save.json} (D15). One instance is one session's view of one profile.
 *
 * <p><b>Load order (E21).</b> Read the text, parse it, {@link SaveMigrator migrate} it, bind it,
 * apply the id aliases ({@link ProfileAliasStep}) and
 * {@link PlayerProfile#normalize(ProfileSchema) normalize} it. Aliases run before normalisation on
 * purpose: a renamed id must be renamed before the consistency rules decide which unlocks it
 * implies, and before an unknown selection is reset to the default. Binding sits before both
 * because the alias table is per field and normalisation is a method on the bound object; the
 * {@link AliasStep} that works on the tree stays for a change that has to happen before the tree
 * can bind at all, the tree being the only representation that still holds fields this build has
 * never heard of.
 *
 * <p><b>Forward compatibility (E22).</b> The tree that was read is kept, and a write is the freshly
 * serialised state laid <em>over</em> it, so a key written by a newer build survives a load and a
 * save by this one, at any depth. Every map-typed and list-typed node is replaced wholesale
 * instead of merged ({@link #REPLACE_WHOLESALE}); merging them would resurrect an upgrade the
 * player refunded, a modifier an alias removed, a wallet a prestige reset, or a run the history cap
 * dropped.
 *
 * <p><b>Failure policy.</b> A file whose <em>content</em> is unusable — it does not parse, does
 * not migrate, does not bind — is never deleted: it is quarantined as
 * {@code save.corrupt-<epochMs>.json}, then {@code save.json.bak} is tried, and only if that fails
 * too does the session start from a fresh profile. A restored backup is written straight back to
 * {@code save.json}, before the session can do anything else, so the recovery survives a player
 * who only browses the menu and quits. Which of the three happened is reported in the
 * {@link LoadResult}, because the player deserves to be told that yesterday's progress is not what
 * they are looking at.
 *
 * <p>A file that merely could not be <em>opened</em> is a different case and is treated as one
 * ({@link Status#UNREADABLE}): an antivirus lock, a cloud-sync placeholder or a transient
 * {@code EIO} says nothing about the bytes on the disk, so the file is left exactly where it is,
 * the backup is used if there is one, and the session runs in "play without saving" mode
 * ({@link #readOnly()}) so it cannot overwrite a save it never managed to read. A file whose
 * {@code version} is newer than this build understands is refused the same way, so the newer build
 * finds it intact.
 *
 * <p><b>Backups.</b> {@code save.json} is copied to {@code save.json.bak} once per session, right
 * after a successful load — the copy is of the file as it was <em>before</em> this session wrote
 * anything, which is what makes it useful. The copy goes through {@link AtomicFiles} like every
 * other write: a plain {@code Files.copy} truncates the destination first, and the one artefact
 * the whole recovery policy rests on must never be half a file. Before any migration runs, the
 * original file is copied to {@code backups/save.v<N>.pre-migration.json} and kept.
 *
 * <p><b>Writes.</b> The JSON is rendered on the calling thread (the loop thread in the game) and
 * the file write runs on the injected {@link Executor}. The coalescing lives here rather than in
 * the executor: at most one task is ever in flight, and a {@link #save()} that arrives while it
 * runs replaces the text the next drain will write, so the latest state wins <em>and</em> every
 * queued write is still accounted for ({@link #pendingWrites()} returns to 0, {@link #flush(long)}
 * returns true). An executor that silently discarded a queued task would leak both. Nothing here
 * throws: a failed write is a {@link AtomicFiles.WriteResult} the loop drains with
 * {@link #pollCompletedWrite()} once per tick and turns into the {@code SaveFailed} event.
 */
public final class SaveManager {

    /**
     * Paths that are replaced wholesale rather than merged on write (E22). Everything not listed
     * here and not an array is merged, which is what lets an unknown key survive; these are the
     * nodes where a stale key must not survive.
     */
    public static final Set<String> REPLACE_WHOLESALE = Set.of(
            "profile.wallet",
            "profile.upgrades",
            "profile.abilityLevels",
            "profile.achievements",
            "profile.challenges",
            "profile.unlocked",
            "profile.reconciled",
            "profile.selected.passiveAbilityIds",
            "profile.daily.modifierIds",
            "profile.statistics.bestGatesByWorld",
            "profile.statistics.bestGatesByTier",
            "profile.statistics.deathsByCause",
            "profile.statistics.abilitiesUsed",
            "profile.statistics.bossClears",
            "profile.statistics.modifiersTaken",
            "profile.statistics.synergiesActivated",
            "profile.statistics.bossesCleared",
            "profile.statistics.runHistory");

    /** How many distinct quarantine names one millisecond may occupy. */
    private static final int ARCHIVE_ATTEMPTS = 9;

    /** How a load ended. */
    public enum Status {
        /** There was no file; the session starts from a fresh profile. */
        NEW_PROFILE,
        /** The file was read (possibly after a migration). */
        LOADED,
        /** The file was unusable and {@code save.json.bak} was loaded instead. */
        RESTORED_FROM_BACKUP,
        /** The file and the backup were both unusable; the session starts from a fresh profile. */
        RESET_AFTER_CORRUPT,
        /**
         * The file exists and could not be opened. Its bytes are presumed intact, so it is left
         * exactly where it is and nothing will be written this session.
         */
        UNREADABLE,
        /** The file is newer than this build; nothing will be written this session. */
        REFUSED_NEWER_VERSION
    }

    /**
     * What a load produced and what the player should be told.
     *
     * @param profile the profile the session runs on
     * @param status how the load ended
     * @param quarantined where an unusable file was moved, or {@code null}
     * @param preMigrationBackup where the pre-migration copy was written, or {@code null}
     * @param migratedFrom the version the file was migrated from, or 0
     * @param repairs what {@link PlayerProfile#normalizeAndReport(ProfileSchema)} had to fix
     * @param detail an English summary of what happened
     */
    public record LoadResult(PlayerProfile profile, Status status, Path quarantined,
            Path preMigrationBackup, int migratedFrom, List<String> repairs, String detail) {

        /**
         * Copies the repair list.
         *
         * @param profile the profile
         * @param status the status
         * @param quarantined the quarantine path or {@code null}
         * @param preMigrationBackup the backup path or {@code null}
         * @param migratedFrom the migrated-from version or 0
         * @param repairs the repairs
         * @param detail the summary
         */
        public LoadResult {
            Objects.requireNonNull(profile, "profile");
            repairs = List.copyOf(repairs);
        }

        /**
         * Whether the session may write.
         *
         * @return {@code true} when the file was refused as too new or could not be opened
         */
        public boolean readOnly() {
            return status == Status.REFUSED_NEWER_VERSION || status == Status.UNREADABLE;
        }

        /**
         * Whether something happened the player should be told about.
         *
         * @return {@code true} unless the load was an ordinary one
         */
        public boolean hasNotice() {
            return status != Status.LOADED && status != Status.NEW_PROFILE;
        }

        /**
         * Whether a migration ran.
         *
         * @return {@code true} when {@link #migratedFrom()} is not 0
         */
        public boolean migrated() {
            return migratedFrom != 0;
        }
    }

    /**
     * The id reconciliation step over the parsed tree (E21). It is injected rather than imported
     * because {@code persistence} does not depend on {@code content}.
     *
     * <p>{@link ProfileAliasStep} is the form the game uses; this one stays for a change that has
     * to happen before the tree can bind at all (a field that moved, not an id that was renamed).
     */
    @FunctionalInterface
    public interface AliasStep {

        /** The step used while no {@code aliases.json} exists. */
        AliasStep NONE = tree -> tree;

        /**
         * Rewrites the ids of a parsed save.
         *
         * @param tree the migrated tree; may be modified in place
         * @return the tree to bind
         */
        JsonObject apply(JsonObject tree);
    }

    /**
     * The id reconciliation step over the bound profile (E21), run <em>between</em> binding and
     * {@link PlayerProfile#normalize(ProfileSchema) normalisation}.
     *
     * <p>That position is the whole point: normalisation resets a selection id no registry knows
     * and writes the unlocks an owned id implies, so an alias that has not been applied yet loses
     * the rename — the selection falls back to the default instead of being carried over, and the
     * implied unlock is written under the old id. The step is a profile-level hook rather than a
     * tree-level one because the alias table is per field ({@code unlocked}, {@code upgrades},
     * {@code abilityLevels}, {@code selected}) and the bound profile is where those fields have
     * names; {@code persistence} still knows nothing about {@code content}.
     */
    @FunctionalInterface
    public interface ProfileAliasStep {

        /** The step used while no {@code aliases.json} exists. */
        ProfileAliasStep NONE = profile -> List.of();

        /**
         * Rewrites the ids of a bound profile, in place.
         *
         * @param profile the bound profile, before normalisation
         * @return one English line per change, in the order the changes were made
         */
        List<String> apply(PlayerProfile profile);
    }

    private final Executor writer;
    private final TimeSource time;
    private final Path explicitFile;
    private final ConcurrentLinkedQueue<AtomicFiles.WriteResult> completed =
            new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition idle = lock.newCondition();

    private SaveMigrator migrator = SaveMigrator.standard();
    private int schemaVersion = SaveFile.VERSION;
    private ProfileSchema schema = ProfileSchema.permissive();
    private AliasStep aliasStep = AliasStep.NONE;
    private ProfileAliasStep profileAliasStep = ProfileAliasStep.NONE;
    private String appVersion = SaveFile.UNKNOWN_APP_VERSION;
    private int contentVersion = SaveFile.CONTENT_VERSION;
    private AtomicFiles.FailurePoint failurePoint = AtomicFiles.FailurePoint.NONE;

    private JsonObject raw = new JsonObject();
    private PlayerProfile profile = new PlayerProfile();
    private Status status = Status.NEW_PROFILE;
    private boolean readOnly;
    private boolean backupCopied;
    private int pending;
    private String queuedText;
    private Path queuedTarget;
    private AtomicFiles.FailurePoint queuedAt = AtomicFiles.FailurePoint.NONE;
    private boolean draining;
    private volatile AtomicFiles.WriteResult lastWrite;

    /**
     * Creates a manager over {@link SavePaths#saveFile()}.
     *
     * @param writer executor the file write runs on
     * @param time the injected clock (D23)
     */
    public SaveManager(Executor writer, TimeSource time) {
        this(writer, time, null);
    }

    /**
     * Creates a manager over an explicit file (tests and tools). The backup, the quarantine and
     * the pre-migration directory are resolved next to it.
     *
     * @param writer executor the file write runs on
     * @param time the injected clock (D23)
     * @param file the save file, or {@code null} to follow {@link SavePaths}
     */
    public SaveManager(Executor writer, TimeSource time, Path file) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.time = Objects.requireNonNull(time, "time");
        this.explicitFile = file;
    }

    /**
     * Sets the migration chain.
     *
     * @param migrator the chain; {@code null} restores {@link SaveMigrator#standard()}
     * @return this manager
     */
    public SaveManager migrator(SaveMigrator migrator) {
        this.migrator = migrator == null ? SaveMigrator.standard() : migrator;
        return this;
    }

    /**
     * Sets the schema version this manager reads and writes.
     *
     * <p>Production leaves it at {@link SaveFile#VERSION}. It is a seam because version 1 is the
     * first version there has ever been, so nothing in the shipped build can exercise a migration,
     * a pre-migration backup or the "file is too new" refusal; {@code SaveMigrationTest} raises it
     * to 2 and hands in a synthetic {@code 1 → 2} step, which keeps the whole path alive until the
     * first real schema change arrives.
     *
     * @param version the version; values below 1 are ignored
     * @return this manager
     */
    public SaveManager schemaVersion(int version) {
        if (version >= 1) {
            this.schemaVersion = version;
        }
        return this;
    }

    /**
     * The schema version this manager reads and writes.
     *
     * @return the version
     */
    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * Sets the id tables normalisation validates against (E21).
     *
     * @param schema the schema; {@code null} restores {@link ProfileSchema#permissive()}
     * @return this manager
     */
    public SaveManager schema(ProfileSchema schema) {
        this.schema = schema == null ? ProfileSchema.permissive() : schema;
        return this;
    }

    /**
     * Sets the id reconciliation step (E21).
     *
     * @param aliasStep the step; {@code null} restores {@link AliasStep#NONE}
     * @return this manager
     */
    public SaveManager aliasStep(AliasStep aliasStep) {
        this.aliasStep = aliasStep == null ? AliasStep.NONE : aliasStep;
        return this;
    }

    /**
     * Sets the id reconciliation step run on the bound profile, before normalisation (E21).
     *
     * @param profileAliasStep the step; {@code null} restores {@link ProfileAliasStep#NONE}
     * @return this manager
     */
    public SaveManager profileAliasStep(ProfileAliasStep profileAliasStep) {
        this.profileAliasStep =
                profileAliasStep == null ? ProfileAliasStep.NONE : profileAliasStep;
        return this;
    }

    /**
     * Sets the diagnostics written into the envelope.
     *
     * @param appVersion the game version
     * @param contentVersion the content version
     * @return this manager
     */
    public SaveManager stamp(String appVersion, int contentVersion) {
        this.appVersion = appVersion == null || appVersion.isBlank()
                ? SaveFile.UNKNOWN_APP_VERSION : appVersion;
        this.contentVersion = contentVersion;
        return this;
    }

    /**
     * Injects a simulated crash into the next writes (tests only).
     *
     * @param failurePoint where the write should pretend to fail
     * @return this manager
     */
    public SaveManager failurePoint(AtomicFiles.FailurePoint failurePoint) {
        this.failurePoint = failurePoint == null ? AtomicFiles.FailurePoint.NONE : failurePoint;
        return this;
    }

    /**
     * The save file.
     *
     * @return the path
     */
    public Path file() {
        return explicitFile != null ? explicitFile : SavePaths.saveFile();
    }

    /**
     * The once-per-session backup.
     *
     * @return the path
     */
    public Path backupFile() {
        return explicitFile != null
                ? explicitFile.resolveSibling(explicitFile.getFileName() + ".bak")
                : SavePaths.saveBackupFile();
    }

    /**
     * The directory pre-migration copies are written to.
     *
     * @return the path
     */
    public Path backupsDir() {
        return explicitFile != null
                ? parentOf(explicitFile).resolve(SavePaths.BACKUP_DIR) : SavePaths.backupsDir();
    }

    /**
     * Reads the save file and applies the whole load policy. Never throws.
     *
     * @return what was loaded and what to tell the player
     */
    public LoadResult load() {
        raw = new JsonObject();
        status = Status.NEW_PROFILE;
        readOnly = false;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return withoutSaveFile(file);
        }
        Read read = read(file);
        if (read.failed()) {
            return unreadable(read.failure());
        }
        Attempt main = attempt(read.text());
        if (main.tooNew()) {
            readOnly = true;
            status = Status.REFUSED_NEWER_VERSION;
            profile = PlayerProfile.fresh(time.epochMillis()).normalize(schema);
            return result(null, null, 0, List.of(), "the save was written by a newer build (schema "
                    + main.version() + " > " + schemaVersion
                    + "); playing without saving so the newer save is not overwritten");
        }
        if (main.ok()) {
            adopt(main);
            status = Status.LOADED;
            copyBackupOnce(file);
            String detail = main.migratedFrom() == 0
                    ? "save loaded"
                    : "save migrated from schema " + main.migratedFrom() + " and loaded";
            return result(null, main.preMigrationBackup(), main.migratedFrom(), main.repairs(),
                    detail);
        }
        Path quarantined = archive(file, "save.corrupt-");
        Attempt backup = fromBackup();
        if (backup != null && backup.ok()) {
            adopt(backup);
            status = Status.RESTORED_FROM_BACKUP;
            // Straight back to disk, before the session can do anything: a player who recovers,
            // browses the menu and quits must not find a fresh profile next time, and the .bak
            // this session did not copy over is then the only other copy left.
            save();
            return result(quarantined, backup.preMigrationBackup(), backup.migratedFrom(),
                    backup.repairs(), "the save was unusable (" + main.failure()
                            + "); it was quarantined and " + backupFile().getFileName()
                            + " was loaded instead and written back");
        }
        profile = PlayerProfile.fresh(time.epochMillis()).normalize(schema);
        raw = new JsonObject();
        status = Status.RESET_AFTER_CORRUPT;
        String why = backup == null ? "there was no backup" : backup.failure();
        return result(quarantined, null, 0, List.of(), "the save was unusable (" + main.failure()
                + ") and " + why + "; starting a new profile");
    }

    /**
     * There is no {@code save.json}. A leftover {@code save.json.bak} is still a profile, and a
     * save file that vanished (a failed sync, a half-restored home directory, a stray delete) must
     * not cost the player everything: the backup is adopted and written back. Only when there is
     * no usable backup either does the session start fresh. {@code --reset-save} moves the backup
     * aside with the save exactly so this path cannot undo a deliberate reset.
     */
    private LoadResult withoutSaveFile(Path file) {
        Attempt backup = fromBackup();
        if (backup != null && backup.ok()) {
            adopt(backup);
            status = Status.RESTORED_FROM_BACKUP;
            save();
            return result(null, backup.preMigrationBackup(), backup.migratedFrom(),
                    backup.repairs(), "there was no " + file.getFileName() + "; "
                            + backupFile().getFileName() + " was loaded and written back");
        }
        profile = PlayerProfile.fresh(time.epochMillis()).normalize(schema);
        return result(null, null, 0, List.of(), "no save file yet; starting a new profile");
    }

    /**
     * The file is there and could not be opened. Nothing is known about its content, so nothing
     * is done to it: no quarantine, no write, and the session plays on the backup (or on a fresh
     * profile) in {@link #readOnly()} mode.
     */
    private LoadResult unreadable(String failure) {
        readOnly = true;
        status = Status.UNREADABLE;
        Attempt backup = fromBackup();
        if (backup != null && backup.ok()) {
            adopt(backup);
            return result(null, backup.preMigrationBackup(), backup.migratedFrom(),
                    backup.repairs(), "the save file could not be opened (" + failure + "); "
                            + backupFile().getFileName() + " was loaded instead and nothing will "
                            + "be written, so the file is left exactly as it is");
        }
        profile = PlayerProfile.fresh(time.epochMillis()).normalize(schema);
        raw = new JsonObject();
        return result(null, null, 0, List.of(), "the save file could not be opened (" + failure
                + "); playing without saving, so the file is left exactly as it is");
    }

    private void adopt(Attempt attempt) {
        profile = attempt.profile();
        raw = attempt.tree();
    }

    private LoadResult result(Path quarantined, Path preMigrationBackup, int migratedFrom,
            List<String> repairs, String detail) {
        return new LoadResult(profile, status, quarantined, preMigrationBackup, migratedFrom,
                repairs, detail);
    }

    private Attempt fromBackup() {
        Path backup = backupFile();
        if (!Files.isRegularFile(backup)) {
            return null;
        }
        Read read = read(backup);
        return read.failed() ? Attempt.failed("the backup could not be read (" + read.failure()
                + ")") : attempt(read.text());
    }

    /** Parse, migrate, alias, bind, normalise (E21). Any failure is a value, never an exception. */
    private Attempt attempt(String text) {
        JsonObject tree = JsonCodec.parseObject(text);
        if (tree == null) {
            return Attempt.failed("the file is not a JSON object");
        }
        int version = SaveFile.versionOf(tree);
        if (version <= 0) {
            return Attempt.failed("the schema version is not a number");
        }
        if (version > schemaVersion) {
            return Attempt.tooNew(version);
        }
        Path preMigrationBackup = null;
        int migratedFrom = 0;
        if (version < schemaVersion) {
            preMigrationBackup = writePreMigrationBackup(version, text);
            SaveMigrator.Result migration = migrator.migrate(tree, version, schemaVersion);
            if (!migration.complete()) {
                return Attempt.failed("no migration path from schema version " + version);
            }
            tree = migration.tree();
            migratedFrom = version;
        }
        JsonObject aliased = aliasStep.apply(tree);
        if (aliased != null) {
            tree = aliased;
        }
        SaveFile envelope;
        try {
            envelope = SaveFile.fromJson(tree);
        } catch (JsonParseException e) {
            return Attempt.failed("the profile does not fit the current schema: " + e.getMessage());
        }
        PlayerProfile bound = envelope.profile();
        // E21's order: the aliases rename the ids, and only then do the consistency rules decide
        // what those ids imply. The other way round a renamed selection is repaired away before
        // the table can carry it over.
        List<String> repairs = new ArrayList<>(profileAliasStep.apply(bound));
        repairs.addAll(bound.normalizeAndReport(schema));
        return Attempt.loaded(bound, tree, migratedFrom, preMigrationBackup, repairs,
                schemaVersion);
    }

    /**
     * Copies the file aside before it is migrated, once. An existing copy is never overwritten:
     * the first pre-migration state is the one worth keeping.
     */
    private Path writePreMigrationBackup(int version, String text) {
        Path target = backupsDir().resolve("save.v" + version + ".pre-migration.json");
        if (Files.exists(target)) {
            return target;
        }
        AtomicFiles.WriteResult write = AtomicFiles.writeString(target, text);
        return write.ok() ? target : null;
    }

    /**
     * Copies {@code save.json} to {@code save.json.bak}, once per session, after a good load.
     *
     * <p>Through {@link AtomicFiles}, not {@code Files.copy}: a plain copy truncates the
     * destination before it writes, so a crash in the middle of it destroys the previous good
     * backup — the one file the recovery policy has left to work with. The flag is set only once
     * the write reported OK, so a transient failure is retried on the next load instead of being
     * silently skipped for the rest of the session.
     */
    private void copyBackupOnce(Path file) {
        if (backupCopied) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            // A missing backup is not worth failing a load over; the save itself is intact.
            return;
        }
        if (AtomicFiles.write(backupFile(), bytes).ok()) {
            backupCopied = true;
        }
    }

    /**
     * The profile the session runs on.
     *
     * @return the profile (the live instance; the progression pipeline writes into it)
     */
    public PlayerProfile profile() {
        return profile;
    }

    /**
     * Adopts a profile without writing it (prestige, {@code --reset-save}, tools).
     *
     * @param profile the profile
     * @return the profile, normalised
     */
    public PlayerProfile hold(PlayerProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile").normalize(schema);
        return this.profile;
    }

    /**
     * Whether this session refuses to write because the file on disk is newer than this build.
     *
     * @return {@code true} in "play without saving" mode
     */
    public boolean readOnly() {
        return readOnly;
    }

    /**
     * How the last load ended.
     *
     * @return the status
     */
    public Status status() {
        return status;
    }

    /**
     * Whether {@code save.json.bak} has been refreshed this session.
     *
     * @return {@code true} once the copy has been made (or attempted)
     */
    public boolean backupCopied() {
        return backupCopied;
    }

    /**
     * Serialises the profile and queues the write. Never throws.
     *
     * <p>The outcome is not readable when this returns — with the real save executor the write has
     * not run yet. Drain {@link #pollCompletedWrite()} on the loop thread instead.
     *
     * <p>Calls coalesce: while a write is in flight, a second call replaces the state the next
     * write will put on the disk rather than queueing a second write, so a burst collapses into
     * the newest state without losing track of what is still owed to the disk.
     *
     * @return {@code true} when a write was queued; {@code false} in {@link #readOnly()} mode or
     *     when the executor refused the task
     */
    public boolean save() {
        return save(profile);
    }

    /**
     * Adopts a profile and queues a write of it.
     *
     * @param profile the profile to persist
     * @return {@code true} when a write was queued
     */
    public boolean save(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        this.profile = profile;
        if (readOnly) {
            return false;
        }
        String text;
        Path target;
        AtomicFiles.FailurePoint at;
        try {
            profile.normalize(schema);
            SaveFile envelope = new SaveFile(schemaVersion, appVersion, contentVersion,
                    time.epochMillis(), profile);
            JsonObject merged = JsonCodec.overlay(raw, envelope.toJson(), REPLACE_WHOLESALE);
            raw = merged;
            text = JsonCodec.toJson(merged) + "\n";
            target = file();
            at = failurePoint;
        } catch (RuntimeException e) {
            // Nothing was queued, so nothing is owed: only the report is due.
            report(new AtomicFiles.WriteResult(AtomicFiles.Outcome.IO_FAILED, file(), false, 0,
                    "the save could not be serialised: " + e));
            return false;
        }
        boolean submit;
        lock.lock();
        try {
            if (queuedText == null) {
                pending++;
            }
            queuedText = text;
            queuedTarget = target;
            queuedAt = at;
            submit = !draining;
            draining = true;
        } finally {
            lock.unlock();
        }
        if (!submit) {
            return true;
        }
        try {
            writer.execute(this::drain);
            return true;
        } catch (RuntimeException e) {
            discardQueued();
            report(new AtomicFiles.WriteResult(AtomicFiles.Outcome.IO_FAILED, target, false, 0,
                    "the save write was rejected: " + e));
            return false;
        }
    }

    /**
     * Writes the newest queued state, then anything queued while that write ran. Runs on the
     * writer thread, one task at a time.
     */
    private void drain() {
        while (true) {
            String text;
            Path target;
            AtomicFiles.FailurePoint at;
            lock.lock();
            try {
                if (queuedText == null) {
                    draining = false;
                    return;
                }
                text = queuedText;
                target = queuedTarget;
                at = queuedAt;
                queuedText = null;
                queuedTarget = null;
            } finally {
                lock.unlock();
            }
            finished(AtomicFiles.writeString(target, text, at));
        }
    }

    /** Drops a slot that will never be written (the executor refused the task). */
    private void discardQueued() {
        lock.lock();
        try {
            queuedText = null;
            queuedTarget = null;
            draining = false;
            if (pending > 0) {
                pending--;
            }
            if (pending == 0) {
                idle.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Quarantines the current save and starts from a fresh profile ({@code --reset-save}). The old
     * file is never deleted, only moved aside.
     *
     * @return the fresh profile and where the old file went
     */
    public LoadResult resetToFresh() {
        Path file = file();
        Path archived = Files.isRegularFile(file) ? archive(file, "save.reset-") : null;
        // The backup goes with it. Otherwise the next launch would find no save.json, restore the
        // profile the player just asked to abandon from save.json.bak, and silently undo the flag.
        Path backup = backupFile();
        if (Files.isRegularFile(backup)) {
            archive(backup, "save.bak.reset-");
        }
        raw = new JsonObject();
        readOnly = false;
        status = Status.NEW_PROFILE;
        profile = PlayerProfile.fresh(time.epochMillis()).normalize(schema);
        return result(archived, null, 0, List.of(), archived == null
                ? "there was no save to reset; starting a new profile"
                : "the save was moved to " + archived.getFileName() + "; starting a new profile");
    }

    /**
     * Takes the oldest write that has finished since the last call (drained once per tick by the
     * loop thread, exactly like {@link SettingsStore#pollCompletedWrite()}).
     *
     * @return the result, or {@code null} when no write has finished
     */
    public AtomicFiles.WriteResult pollCompletedWrite() {
        return completed.poll();
    }

    /**
     * The result of the most recent write.
     *
     * @return the result, or {@code null} when nothing has been written yet
     */
    public AtomicFiles.WriteResult lastWrite() {
        return lastWrite;
    }

    /**
     * Waits for the queued writes to finish, for at most a bounded time (shutdown, D15).
     *
     * <p>The interrupt status is deliberately not restored here: the pure packages may not
     * reference the thread API at all (D5), and the shutdown path that owns thread state —
     * {@code app.Threads.shutdown} — is the one that handles it.
     *
     * @param timeoutMs how long to wait, in milliseconds
     * @return {@code true} when nothing is pending any more
     */
    public boolean flush(long timeoutMs) {
        lock.lock();
        try {
            long remaining = TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
            while (pending > 0 && remaining > 0) {
                remaining = idle.awaitNanos(remaining);
            }
            return pending == 0;
        } catch (InterruptedException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many states are still owed to the disk: the one being written plus the one waiting to
     * be written, so never more than 2 whatever the burst.
     *
     * @return the count
     */
    public int pendingWrites() {
        lock.lock();
        try {
            return pending;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The tree the next write will be laid over (tests and tools).
     *
     * @return a copy of the remembered tree
     */
    public JsonObject rawTree() {
        return raw.deepCopy();
    }

    /** Records a write outcome for the loop thread without touching the pending count. */
    private void report(AtomicFiles.WriteResult result) {
        lastWrite = result;
        completed.add(result);
    }

    /** Records a finished write and queues it for the loop thread. Runs on the writer thread. */
    private void finished(AtomicFiles.WriteResult result) {
        report(result);
        lock.lock();
        try {
            if (pending > 0) {
                pending--;
            }
            if (pending == 0) {
                idle.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Moves a file aside under {@code <prefix><epochMs>.json}. A name already taken (two failures
     * in the same millisecond) gets a {@code -2}, {@code -3} … suffix; only when every candidate is
     * taken is the older archive replaced.
     */
    private Path archive(Path file, String prefix) {
        Path dir = parentOf(file);
        String stamp = prefix + time.epochMillis();
        Path target = dir.resolve(stamp + ".json");
        for (int n = 2; n <= ARCHIVE_ATTEMPTS && Files.exists(target); n++) {
            target = dir.resolve(stamp + "-" + n + ".json");
        }
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path parentOf(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        return parent == null ? file.toAbsolutePath() : parent;
    }

    /**
     * Reads a file, keeping <em>why</em> it could not be read. The distinction matters: a file
     * that cannot be opened is not a corrupt file, and only a corrupt one may be quarantined.
     */
    private static Read read(Path file) {
        try {
            return new Read(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), null);
        } catch (IOException e) {
            return new Read(null, String.valueOf(e));
        }
    }

    /** The text of a file, or the I/O failure that stopped it from being read. */
    private record Read(String text, String failure) {

        boolean failed() {
            return text == null;
        }
    }

    /** One try at turning a file's text into a profile. */
    private record Attempt(PlayerProfile profile, JsonObject tree, int migratedFrom,
            Path preMigrationBackup, List<String> repairs, int version, boolean tooNew,
            String failure) {

        static Attempt loaded(PlayerProfile profile, JsonObject tree, int migratedFrom,
                Path preMigrationBackup, List<String> repairs, int version) {
            return new Attempt(profile, tree, migratedFrom, preMigrationBackup,
                    new ArrayList<>(repairs), version, false, null);
        }

        static Attempt failed(String failure) {
            return new Attempt(null, null, 0, null, List.of(), 0, false, failure);
        }

        static Attempt tooNew(int version) {
            return new Attempt(null, null, 0, null, List.of(), version, true,
                    "newer schema version");
        }

        boolean ok() {
            return profile != null;
        }
    }
}
