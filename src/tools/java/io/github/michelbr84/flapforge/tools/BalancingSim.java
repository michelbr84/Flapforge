package io.github.michelbr84.flapforge.tools;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.RewardContext;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunRewardCalculator;
import io.github.michelbr84.flapforge.gameplay.run.StreakTracker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Batch simulation over many seeds, used to balance the game from data instead of feel (D21).
 *
 * <p>It builds runs from the shipped content through {@link RunFactory}, drives them with
 * {@link BotPilot} at a chosen skill preset and reports survival percentiles, scores, death causes
 * and — since M3 — what the runs paid: coins, XP, the streak and how much of the coin trail the
 * bot actually picked up. {@code --csv} writes one row per run for spreadsheets and CI artefacts.
 * Every number in the M3 section of {@code docs/BALANCING.md} comes from this tool.
 *
 * <pre>
 * ./gradlew balancing -PtoolArgs="--seeds 200 --skill average --ticks 20000 --csv build/balancing.csv"
 * ./gradlew balancing -PtoolArgs="--seeds 50 --bird all --skill all"
 * </pre>
 */
public final class BalancingSim {

    /** Default number of seeds per cell. */
    public static final int DEFAULT_SEEDS = 100;
    /** Default tick budget of one run (20 000 ticks ≈ 5.5 minutes of play). */
    public static final int DEFAULT_TICKS = 20_000;
    /** Default first seed; run {@code i} uses {@code seed0 + i}. */
    public static final long DEFAULT_FIRST_SEED = 1;

    private BalancingSim() {
    }

    /**
     * One finished simulation, with what it paid (M3). The economy columns come from the real
     * {@link RunRewardCalculator} against the shipped {@code economy.json}, with every multiplier
     * at 1 and the run treated as a later run (no first-run bonus), so a cell of seeds is directly
     * comparable with the coins-per-run tables in {@code docs/BALANCING.md}.
     */
    private record Row(String bird, String skill, long seed, int gates, double points,
            int ticksAlive, boolean finished, String deathCause, int coinsSpawned,
            int coinsCollected, int streakBest, int streakSteps, long coins, long xp) {
    }

    /** Command-line options. */
    private static final class Options {
        int seeds = DEFAULT_SEEDS;
        long firstSeed = DEFAULT_FIRST_SEED;
        int ticks = DEFAULT_TICKS;
        String bird = RunConfig.DEFAULT_BIRD;
        String world = RunConfig.DEFAULT_WORLD;
        String tier = RunConfig.DEFAULT_TIER;
        String skill = BotPilot.Preset.AVERAGE.name();
        Path csv;
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
            return;
        }
        if (options.help) {
            System.out.println(usage());
            return;
        }
        GameContent content = GameContent.load();
        RunFactory factory = new RunFactory(content);
        List<String> birds = birdsOf(content, options.bird);
        List<BotPilot.Preset> presets = presetsOf(options.skill);

        System.out.println("Flapforge balancing — world=" + options.world + " tier=" + options.tier
                + " seeds=" + options.seeds + " (from " + options.firstSeed + ") ticks="
                + options.ticks);
        List<Row> rows = new ArrayList<>(birds.size() * presets.size() * options.seeds);
        for (String bird : birds) {
            for (BotPilot.Preset preset : presets) {
                List<Row> cell = simulate(factory, content.economy(), options, bird, preset);
                rows.addAll(cell);
                report(bird, preset, options.ticks, cell);
            }
        }
        if (options.csv != null) {
            writeCsv(options.csv, rows);
            System.out.println("CSV: " + options.csv.toAbsolutePath() + " (" + rows.size()
                    + " rows)");
        }
    }

    private static List<Row> simulate(RunFactory factory, EconomyDef economy, Options options,
            String bird, BotPilot.Preset preset) {
        List<Row> rows = new ArrayList<>(options.seeds);
        for (int i = 0; i < options.seeds; i++) {
            long seed = options.firstSeed + i;
            RunConfig config = RunConfig.builder(seed).birdId(bird).worldId(options.world)
                    .tierId(options.tier).build();
            Run run = factory.newRun(config);
            HeadlessRunner.Outcome outcome = HeadlessRunner.run(run,
                    new BotPilot(preset, seed), options.ticks);
            RunResult result = outcome.result();
            String cause = result.stats().deathCause() == null ? "ALIVE"
                    : result.stats().deathCause().name();
            RewardSummary rewards = RunRewardCalculator.compute(result, economy,
                    RewardContext.plain());
            rows.add(new Row(bird, preset.name(), seed, result.gatesPassed(),
                    result.stats().points(), result.stats().ticksAlive(), outcome.finished(),
                    cause, run.simulation().pickups().spawnedCount(),
                    result.stats().coinsCollected(), result.stats().streakBest(),
                    result.stats().streakSteps(), rewards.coins(), rewards.xp()));
        }
        return rows;
    }

    private static void report(String bird, BotPilot.Preset preset, int ticks, List<Row> rows) {
        int[] gates = new int[rows.size()];
        int[] alive = new int[rows.size()];
        int[] coins = new int[rows.size()];
        int[] xp = new int[rows.size()];
        int[] collected = new int[rows.size()];
        int[] spawned = new int[rows.size()];
        int[] streakBest = new int[rows.size()];
        int[] streakSteps = new int[rows.size()];
        double points = 0;
        Map<String, Integer> causes = new LinkedHashMap<>();
        int survived = 0;
        int zeroCoins = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            gates[i] = row.gates();
            alive[i] = row.ticksAlive();
            coins[i] = (int) row.coins();
            xp[i] = (int) row.xp();
            collected[i] = row.coinsCollected();
            spawned[i] = row.coinsSpawned();
            streakBest[i] = row.streakBest();
            streakSteps[i] = row.streakSteps();
            points += row.points();
            causes.merge(row.deathCause(), 1, Integer::sum);
            if (!row.finished()) {
                survived++;
            }
            if (row.coins() == 0) {
                zeroCoins++;
            }
        }
        Arrays.sort(gates);
        Arrays.sort(alive);
        Arrays.sort(coins);
        Arrays.sort(streakBest);
        System.out.println();
        System.out.printf(Locale.ROOT, "bird=%s skill=%s (reaction %d ticks, error %.0f px) runs=%d%n",
                bird, preset.name(), preset.reactionTicks(), preset.errorPx(), rows.size());
        System.out.printf(Locale.ROOT,
                "  gates      p10=%d p50=%d p90=%d min=%d max=%d mean=%.2f%n",
                percentile(gates, 10), percentile(gates, 50), percentile(gates, 90), gates[0],
                gates[gates.length - 1], mean(gates));
        System.out.printf(Locale.ROOT,
                "  ticksAlive p10=%d p50=%d p90=%d min=%d max=%d mean=%.1f (%.1f s at 60 Hz)%n",
                percentile(alive, 10), percentile(alive, 50), percentile(alive, 90), alive[0],
                alive[alive.length - 1], mean(alive), mean(alive) / 60.0);
        System.out.printf(Locale.ROOT, "  points     mean=%.2f%n", points / rows.size());
        System.out.printf(Locale.ROOT,
                "  coins      p10=%d p50=%d p90=%d mean=%.1f zero=%.1f %% (collected %.1f of %.1f"
                        + " spawned = %.1f %%)%n",
                percentile(coins, 10), percentile(coins, 50), percentile(coins, 90), mean(coins),
                100.0 * zeroCoins / rows.size(), mean(collected), mean(spawned),
                mean(spawned) == 0 ? 0 : 100.0 * mean(collected) / mean(spawned));
        System.out.printf(Locale.ROOT, "  xp         mean=%.1f%n", mean(xp));
        System.out.printf(Locale.ROOT,
                "  streak     best mean=%.2f max=%d  steps mean=%.2f (one every %d clean gates)%n",
                mean(streakBest), streakBest[streakBest.length - 1], mean(streakSteps),
                StreakTracker.DEFAULT_STEP);
        System.out.printf(Locale.ROOT, "  reached the %d-tick budget: %d/%d (%.1f %%)%n", ticks,
                survived, rows.size(), 100.0 * survived / rows.size());
        StringBuilder deaths = new StringBuilder("  deaths     ");
        for (Map.Entry<String, Integer> e : causes.entrySet()) {
            deaths.append(e.getKey()).append('=').append(e.getValue()).append("  ");
        }
        System.out.println(deaths.toString().stripTrailing());
    }

    /** Nearest-rank percentile of a sorted array. */
    private static int percentile(int[] sorted, int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    private static double mean(int[] values) {
        long total = 0;
        for (int v : values) {
            total += v;
        }
        return (double) total / values.length;
    }

    private static List<String> birdsOf(GameContent content, String selector) {
        if ("all".equalsIgnoreCase(selector)) {
            List<String> ids = new ArrayList<>(content.birds().size());
            for (BirdDef def : content.birds()) {
                ids.add(def.id());
            }
            return ids;
        }
        content.birds().get(selector);
        return List.of(selector);
    }

    private static List<BotPilot.Preset> presetsOf(String selector) {
        if ("all".equalsIgnoreCase(selector)) {
            return List.of(BotPilot.Preset.NOVICE, BotPilot.Preset.AVERAGE, BotPilot.Preset.EXPERT,
                    BotPilot.Preset.PERFECT);
        }
        return List.of(BotPilot.Preset.byName(selector));
    }

    private static void writeCsv(Path path, List<Row> rows) {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add("bird,skill,seed,gates,points,ticksAlive,finished,deathCause,coinsSpawned,"
                + "coinsCollected,streakBest,streakSteps,coins,xp");
        for (Row r : rows) {
            lines.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%s,%d,%s,%s,%d,%d,%d,%d,%d,%d",
                    r.bird(), r.skill(), r.seed(), r.gates(), Double.toString(r.points()),
                    r.ticksAlive(), r.finished(), r.deathCause(), r.coinsSpawned(),
                    r.coinsCollected(), r.streakBest(), r.streakSteps(), r.coins(), r.xp()));
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path, e);
        }
    }

    private static Options parse(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--seeds" -> options.seeds = positiveInt(arg, next(args, ++i));
                case "--seed0" -> options.firstSeed = Long.parseLong(next(args, ++i));
                case "--ticks" -> options.ticks = positiveInt(arg, next(args, ++i));
                case "--bird" -> options.bird = next(args, ++i);
                case "--world" -> options.world = next(args, ++i);
                case "--tier" -> options.tier = next(args, ++i);
                case "--skill" -> options.skill = next(args, ++i);
                case "--csv" -> options.csv = Path.of(next(args, ++i));
                case "--help", "-h" -> options.help = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return options;
    }

    private static String next(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[index - 1]);
        }
        return args[index];
    }

    private static int positiveInt(String name, String value) {
        int n = Integer.parseInt(value);
        if (n <= 0) {
            throw new IllegalArgumentException(name + " needs a positive number, got " + value);
        }
        return n;
    }

    /**
     * The help text.
     *
     * @return the usage lines
     */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: ./gradlew balancing -PtoolArgs=\"[options]\"",
                "  --seeds N     runs per cell (default " + DEFAULT_SEEDS + ")",
                "  --seed0 N     first seed (default " + DEFAULT_FIRST_SEED + ")",
                "  --ticks N     tick budget per run (default " + DEFAULT_TICKS + ")",
                "  --bird ID     bird id, or 'all' to iterate the registry (default "
                        + RunConfig.DEFAULT_BIRD + ")",
                "  --world ID    world id (default " + RunConfig.DEFAULT_WORLD + ")",
                "  --tier ID     tier id (default " + RunConfig.DEFAULT_TIER + ")",
                "  --skill NAME  novice | average | expert | perfect | all (default average)",
                "  --csv PATH    write one row per run",
                "  --help        this text");
    }
}
