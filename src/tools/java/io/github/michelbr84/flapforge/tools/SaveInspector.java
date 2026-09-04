package io.github.michelbr84.flapforge.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.persistence.JsonCodec;
import io.github.michelbr84.flapforge.persistence.SaveFile;
import io.github.michelbr84.flapforge.persistence.SaveManager;
import io.github.michelbr84.flapforge.persistence.SavePaths;
import io.github.michelbr84.flapforge.progression.PlayerLevel;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.Statistics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Prints and validates a player's {@code save.json} (D15, milestone M3).
 *
 * <p>It reads a profile directory through the very same {@link SaveManager} the game uses — same
 * migration chain, same repairs, same failure policy — so what it prints is what the game would
 * see, not a second opinion. It never writes {@code save.json}. It does run the load policy in
 * full, so a run refreshes {@code save.json.bak} exactly as starting the game would, and an
 * unusable save is quarantined rather than left to be found again.
 *
 * <pre>
 * ./gradlew saveInspector -PtoolArgs="--home ~/.flapforge"
 * ./gradlew saveInspector -PtoolArgs="--home build/test-home --json"
 * </pre>
 *
 * <p>The run fails — which fails the Gradle task — when the save was unusable, refused as too new
 * or had to be repaired, so a release script can gate on it; {@code --json} carries the same
 * verdict as {@code "valid"}. {@code System.exit} is not used: D4 reserves it for the shutdown
 * watchdog, so the status travels as a {@link ToolFailure}.
 */
public final class SaveInspector {

    /** Exit status when everything is sound. */
    public static final int EXIT_OK = 0;
    /** Exit status for a usage error. */
    public static final int EXIT_USAGE = 1;
    /** Exit status when the save was unusable, refused or had to be repaired. */
    public static final int EXIT_PROBLEM = 2;

    private SaveInspector() {
    }

    /** Command-line options. */
    private static final class Options {
        Path home;
        boolean json;
        boolean help;
    }

    /**
     * Entry point.
     *
     * @param args the command line
     */
    public static void main(String[] args) {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(usage());
            exit(EXIT_USAGE);
            return;
        }
        if (options.help) {
            System.out.println(usage());
            return;
        }
        exit(inspect(options.home, options.json));
    }

    /**
     * Inspects a profile directory and prints the report.
     *
     * @param home the profile directory
     * @param json whether to print machine-readable JSON instead of text
     * @return the exit status
     */
    public static int inspect(Path home, boolean json) {
        Path previousOverride = SavePaths.overrideDir();
        SavePaths.override(home);
        try {
            Report report = read();
            System.out.println(json ? JsonCodec.toJson(report.toJson()) : report.toText());
            return report.problem() ? EXIT_PROBLEM : EXIT_OK;
        } finally {
            SavePaths.override(previousOverride);
        }
    }

    private static Report read() {
        SaveManager manager = new SaveManager(Runnable::run, System::currentTimeMillis);
        SaveManager.LoadResult result = manager.load();
        JsonObject raw = manager.rawTree();
        if (!raw.has(SaveFile.KEY_VERSION)) {
            // A refused or unusable file is never adopted, but its envelope is what the player
            // needs to see, so it is read straight off the disk for the report.
            JsonObject onDisk = JsonCodec.parseObject(text(manager.file()));
            if (onDisk != null) {
                raw = onDisk;
            }
        }
        PlayerProfile profile = result.profile();
        JsonObject known = new SaveFile(SaveFile.VERSION, SaveFile.UNKNOWN_APP_VERSION,
                SaveFile.CONTENT_VERSION, 0, profile).toJson();
        List<String> unknown = unknownFields(raw, known);
        return new Report(SavePaths.profileDir(), manager, result, raw, profile, unknown);
    }

    /**
     * Reads a file as UTF-8 text, or {@code null} when it cannot be read.
     *
     * @param file the file
     * @return the text or {@code null}
     */
    private static String text(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Lists the keys the file carries that the current schema does not know, at the nodes that are
     * merged rather than replaced on write. They are not errors — they are exactly what the
     * forward-compatible overlay is there to preserve (E22) — but a player asking why their save
     * is big deserves to see them.
     */
    private static List<String> unknownFields(JsonObject raw, JsonObject known) {
        List<String> out = new ArrayList<>();
        collectUnknown(raw, known, "", out);
        return out;
    }

    private static void collectUnknown(JsonObject raw, JsonObject known, String path,
            List<String> out) {
        for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
            String child = path.isEmpty() ? entry.getKey() : path + '.' + entry.getKey();
            JsonElement mirror = known.get(entry.getKey());
            if (mirror == null) {
                out.add(child);
            } else if (mirror.isJsonObject() && entry.getValue().isJsonObject()
                    && MERGED_NODES.contains(child)) {
                collectUnknown(entry.getValue().getAsJsonObject(), mirror.getAsJsonObject(), child,
                        out);
            }
        }
    }

    /** The nodes the write path merges into (E22); anywhere else, an extra key is data, not schema. */
    private static final List<String> MERGED_NODES =
            List.of("profile", "profile.statistics", "profile.selected", "profile.daily");

    /** Everything the report shows. */
    private record Report(Path home, SaveManager manager, SaveManager.LoadResult result,
            JsonObject raw, PlayerProfile profile, List<String> unknownFields) {

        boolean problem() {
            return result.hasNotice() || !result.repairs().isEmpty();
        }

        String toText() {
            StringBuilder out = new StringBuilder();
            out.append("Flapforge save inspector\n");
            out.append("========================\n\n");
            out.append("Location\n");
            out.append(line("home", home.toString()));
            out.append(file("save.json", manager.file()));
            out.append(file("save.json.bak", manager.backupFile()));
            for (Path quarantine : listing("save.corrupt-")) {
                out.append(file("quarantine", quarantine));
            }
            for (Path reset : listing("save.reset-")) {
                out.append(file("reset backup", reset));
            }
            for (Path backup : backups()) {
                out.append(file("pre-migration", backup));
            }

            out.append("\nStatus\n");
            out.append(line("status", result.status().name()));
            out.append(line("detail", result.detail()));
            out.append(line("read only", String.valueOf(result.readOnly())));
            if (result.migrated()) {
                out.append(line("migrated from", "schema " + result.migratedFrom()));
            }
            if (result.quarantined() != null) {
                out.append(line("quarantined", result.quarantined().toString()));
            }
            out.append(line("schema", envelope(SaveFile.KEY_VERSION)));
            out.append(line("app version", envelope(SaveFile.KEY_APP_VERSION)));
            out.append(line("content version", envelope(SaveFile.KEY_CONTENT_VERSION)));
            out.append(line("saved at", savedAt()));

            out.append("\nProfile\n");
            out.append(line("created at", stamp(profile.createdAtEpochMs)));
            out.append(line("level", profile.level + " (xp " + profile.xp + ", "
                    + progress() + ")"));
            out.append(line("prestige", profile.prestigeCount + " / "
                    + PlayerProfile.MAX_PRESTIGE_COUNT));
            out.append(line("wallet", profile.wallet.toString()));
            out.append(line("ability cap", String.valueOf(profile.abilityLevelCap)));
            out.append(line("passive slot bonus", String.valueOf(profile.passiveSlotBonus)));
            out.append(line("unlocked", profile.unlocked.size() + " ids " + byNamespace()));
            out.append(line("upgrades", profile.upgrades.isEmpty()
                    ? "none" : new TreeMap<>(profile.upgrades).toString()));
            out.append(line("ability levels", profile.abilityLevels.isEmpty()
                    ? "none" : new TreeMap<>(profile.abilityLevels).toString()));
            out.append(line("achievements", String.valueOf(profile.achievements.size())));
            out.append(line("challenges", challenges()));
            out.append(line("selected", profile.selected.birdId + " / "
                    + profile.selected.paletteId + " in " + profile.selected.worldId + " at "
                    + profile.selected.tierId + ", ability " + profile.selected.activeAbilityId));
            out.append(line("last seed", String.valueOf(profile.lastSeed)));

            out.append("\nDaily\n");
            PlayerProfile.DailyRecord daily = profile.daily;
            out.append(line("date", daily.date.isEmpty() ? "(no pick yet)" : daily.date));
            if (!daily.date.isEmpty()) {
                out.append(line("seed", String.valueOf(daily.seed)));
                out.append(line("world / tier", daily.worldId + " / " + daily.tierId));
                out.append(line("modifiers", daily.modifierIds.toString()));
                out.append(line("attempts", daily.attempts + ", best " + daily.bestGates));
            }

            out.append("\nStatistics\n");
            Statistics stats = profile.statistics;
            out.append(line("runs", stats.totalRuns + " (gates " + stats.totalGates + ", best "
                    + stats.bestGates + ")"));
            out.append(line("points", stats.totalPoints + " (best " + stats.bestPoints + ")"));
            out.append(line("coins", "earned " + stats.coinsEarned + ", spent " + stats.coinsSpent
                    + ", collected " + stats.coinsCollected));
            out.append(line("xp earned", String.valueOf(stats.xpEarned)));
            out.append(line("best streak", String.valueOf(stats.streakBest)));
            out.append(line("abilities used", String.valueOf(stats.abilitiesUsedTotal)));
            out.append(line("shield / revive", stats.shieldAbsorbs + " / " + stats.revives));
            out.append(line("bosses cleared", stats.bossesCleared.toString()));
            out.append(line("challenges done", String.valueOf(stats.challengesCompleted)));
            out.append(line("dailies played", String.valueOf(stats.dailiesPlayed)));
            out.append(line("playtime", playtime()));
            out.append(line("deaths", new TreeMap<>(stats.deathsByCause).toString()));
            out.append(line("best by world", new TreeMap<>(stats.bestGatesByWorld).toString()));
            out.append(line("best by tier", new TreeMap<>(stats.bestGatesByTier).toString()));
            out.append(line("run history", stats.runHistory.size() + " / "
                    + Statistics.RUN_HISTORY_LIMIT));

            out.append("\nValidation\n");
            if (result.repairs().isEmpty()) {
                out.append("  the profile matches the current schema; nothing had to be repaired\n");
            } else {
                out.append("  the profile was repaired on load:\n");
                for (String repair : result.repairs()) {
                    out.append("    - ").append(repair).append('\n');
                }
            }
            if (unknownFields.isEmpty()) {
                out.append("  no unknown fields\n");
            } else {
                out.append("  fields this build does not know (kept on write, E22):\n");
                for (String field : unknownFields) {
                    out.append("    - ").append(field).append('\n');
                }
            }
            return out.toString();
        }

        JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("home", home.toString());
            root.addProperty("status", result.status().name());
            root.addProperty("detail", result.detail());
            root.addProperty("readOnly", result.readOnly());
            root.addProperty("migratedFrom", result.migratedFrom());
            root.addProperty("quarantined",
                    result.quarantined() == null ? null : result.quarantined().toString());
            JsonObject files = new JsonObject();
            files.addProperty(manager.file().getFileName().toString(), size(manager.file()));
            files.addProperty(manager.backupFile().getFileName().toString(),
                    size(manager.backupFile()));
            for (Path quarantine : listing("save.corrupt-")) {
                files.addProperty(quarantine.getFileName().toString(), size(quarantine));
            }
            for (Path backup : backups()) {
                files.addProperty(SavePaths.BACKUP_DIR + "/" + backup.getFileName(), size(backup));
            }
            root.add("files", files);
            root.add("envelope", envelopeJson());
            root.add("profile", JsonCodec.toTree(profile));
            root.add("repairs", JsonCodec.toTree(result.repairs()));
            root.add("unknownFields", JsonCodec.toTree(unknownFields));
            root.addProperty("valid", !problem());
            return root;
        }

        private JsonObject envelopeJson() {
            JsonObject out = new JsonObject();
            for (String key : List.of(SaveFile.KEY_VERSION, SaveFile.KEY_APP_VERSION,
                    SaveFile.KEY_CONTENT_VERSION, SaveFile.KEY_SAVED_AT)) {
                JsonElement value = raw.get(key);
                if (value != null) {
                    out.add(key, value.deepCopy());
                }
            }
            return out;
        }

        private String envelope(String key) {
            JsonElement value = raw.get(key);
            return value == null || value.isJsonNull() ? "(absent)" : value.getAsString();
        }

        private String savedAt() {
            JsonElement value = raw.get(SaveFile.KEY_SAVED_AT);
            if (value == null || !value.isJsonPrimitive()) {
                return "(absent)";
            }
            return stamp(value.getAsLong());
        }

        private static String stamp(long epochMillis) {
            return epochMillis <= 0 ? "(never)"
                    : epochMillis + " (" + Instant.ofEpochMilli(epochMillis) + ")";
        }

        private String progress() {
            PlayerLevel curve = PlayerLevel.defaults();
            PlayerLevel.Progress at = curve.progressWithin(profile.xp);
            if (at.maxed()) {
                return "max level on the default curve";
            }
            return at.xpIntoLevel() + " / " + at.xpForNextLevel()
                    + " xp into level " + at.level() + " on the default curve";
        }

        private String byNamespace() {
            Map<String, Integer> counts = new TreeMap<>();
            for (String id : profile.unlocked) {
                int colon = id.indexOf(':');
                counts.merge(colon <= 0 ? "(bare)" : id.substring(0, colon), 1, Integer::sum);
            }
            return counts.toString();
        }

        private String challenges() {
            if (profile.challenges.isEmpty()) {
                return "none";
            }
            Map<String, String> out = new TreeMap<>();
            for (Map.Entry<String, PlayerProfile.ChallengeRecord> entry
                    : profile.challenges.entrySet()) {
                PlayerProfile.ChallengeRecord record = entry.getValue();
                out.put(entry.getKey(), (record.completed ? "completed" : "open") + ", best "
                        + record.bestGates + ", attempts " + record.attempts);
            }
            return out.toString();
        }

        private String playtime() {
            long seconds = profile.statistics.playtimeSeconds;
            return seconds + " s (" + (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min)";
        }

        private List<Path> listing(String prefix) {
            Path dir = home;
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            try (Stream<Path> files = Files.list(dir)) {
                return files.filter(p -> p.getFileName().toString().startsWith(prefix))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                return List.of();
            }
        }

        private List<Path> backups() {
            Path dir = manager.backupsDir();
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            try (Stream<Path> files = Files.list(dir)) {
                return files.sorted().toList();
            } catch (IOException e) {
                return List.of();
            }
        }

        private static String file(String label, Path path) {
            if (!Files.exists(path)) {
                return line(label, "(absent)");
            }
            return line(label, path + "  " + humanSize(size(path)));
        }

        private static long size(Path path) {
            try {
                return Files.isRegularFile(path) ? Files.size(path) : -1;
            } catch (IOException e) {
                return -1;
            }
        }

        private static String humanSize(long bytes) {
            if (bytes < 0) {
                return "(unreadable)";
            }
            if (bytes < 1024) {
                return bytes + " B";
            }
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }

        private static String line(String label, String value) {
            return String.format(Locale.ROOT, "  %-18s %s%n", label, value == null ? "(none)" : value);
        }
    }

    private static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--home":
                    options.home = Paths.get(next(args, ++i, "--home"));
                    break;
                case "--json":
                    options.json = true;
                    break;
                case "--help":
                case "-h":
                    options.help = true;
                    break;
                default:
                    throw new IllegalArgumentException("unknown option: " + arg);
            }
        }
        if (options.home == null && !options.help) {
            options.home = SavePaths.profileDir();
        }
        return options;
    }

    private static String next(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    /**
     * The usage text.
     *
     * @return the text
     */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: saveInspector [--home <dir>] [--json]",
                "",
                "  --home <dir>  profile directory to inspect (default: the platform location)",
                "  --json        print a machine-readable report instead of the text one",
                "  --help        print this text",
                "",
                "The run fails when the save is unusable, refused as too new, or was repaired.");
    }

    /** Fails the Gradle task on a problem without killing a JVM that embeds the tool. */
    private static void exit(int status) {
        if (status != EXIT_OK) {
            throw new ToolFailure(status);
        }
    }

    /**
     * Thrown instead of calling {@code System.exit}, which D4 reserves for the shutdown watchdog:
     * it fails the Gradle task and leaves an embedding JVM alive.
     */
    static final class ToolFailure extends RuntimeException {
        private final int status;

        ToolFailure(int status) {
            super("save inspector finished with status " + status);
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}
