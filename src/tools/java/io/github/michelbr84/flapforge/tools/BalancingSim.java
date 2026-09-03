package io.github.michelbr84.flapforge.tools;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityLevelDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.PatternDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.RewardContext;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunRewardCalculator;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.run.StreakTracker;
import io.github.michelbr84.flapforge.gameplay.collision.CollisionCause;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.modifier.DraftContext;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.modifier.ModifierOffer;
import io.github.michelbr84.flapforge.modifier.ModifierPool;
import io.github.michelbr84.flapforge.modifier.Rarity;
import io.github.michelbr84.flapforge.core.RandomProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
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
 * ./gradlew balancing -PtoolArgs="--bird guardian --ability shield"
 * ./gradlew balancing -PtoolArgs="--seeds 100 --world all --skill all"
 * ./gradlew balancing -PtoolArgs="--seeds 50 --world iron_forge --tier all --skill expert"
 * ./gradlew balancing -PtoolArgs="--seeds 50 --pattern forge_gear_corridor --skill expert"
 * ./gradlew balancing -PtoolArgs="--seeds 50 --challenge all --skill all"
 * ./gradlew balancing -PtoolArgs="--seeds 50 --boss all --skill expert"
 * </pre>
 *
 * <p>{@code --world all} and {@code --tier all} (M7) sweep every world of {@code worlds.json}
 * and every tier of {@code difficulty.json} through {@link RunFactory}, so the per-world table
 * of {@code docs/BALANCING.md} §10 is one command; the deaths line groups obstacle deaths by
 * kind. {@code --pattern <id|all>} plays one pattern of {@code patterns.json} in isolation —
 * the run streams that pattern and nothing else, looped, from the first spawn
 * ({@link RunSetup#withForcedPattern}) in the pattern's own world (its curve, effects and
 * ambience) unless {@code --world} was given — which is how the feasibility numbers
 * {@code ContentFeasibilityTest} asserts are reproduced from the command line.
 *
 * <p>{@code --challenge <id|all>} (M8) plays a challenge as the player would: the run is
 * stamped by {@link RunFactory#challengeConfig} — its world, tier, curve, flags, effects, forced
 * modifiers, forced pattern and boss block — and the report adds how often the objective was
 * met, how often the challenge's boss was cleared and how far its phases were reached.
 * {@code --boss <worldId|all>} (M8) measures a world's boss encounter on its own: the run starts
 * at the boss ({@link RunSetup#startingAtBoss}: the warning at the first gate, the curve shifted
 * to {@code boss.atGate}) and the report says how often the fight was survived. Both are the
 * command-line twins of the M8 rows of {@code ContentFeasibilityTest}.
 *
 * <p>{@code --ability} (M5) equips one ability in the slot its kind belongs to and reports how
 * often the bot spent it, how many hits a shield absorbed and how many revives were used;
 * {@code --ability all} sweeps the ability-free baseline plus every ability, which is how the
 * per-ability table in {@code docs/BALANCING.md} is produced.
 *
 * <p>{@code --drafts} (M6) turns the roguelite layer on: every modifier is available, offers open
 * at the scheduled gates and the bot takes the first card of every draft (D21). The extra section
 * reports how far into the schedule the runs got, which rarities the cards taken were, and how
 * often a build activated a set bonus — the criterion §6 asks for is that an average bot which
 * reaches the third offer activates at least one synergy in at least 20 % of those runs
 * (reported here, asserted by {@code MetaSimTest} in M9).
 *
 * <p>{@code --modifier <id|all|a,b>} (M6) is the other half of the roguelite measurement: instead
 * of letting the bot draft, it <em>forces</em> a card on every run of a cell and prints what the
 * card was worth against the same seeds without it. {@code --modifier all} sweeps the baseline
 * plus every shipped card, which is what produced the per-card table in
 * {@code docs/BALANCING.md} §8.3, and {@code --modifier-stacks 2} measures the second stack.
 */
public final class BalancingSim {

    /** Default number of seeds per cell. */
    public static final int DEFAULT_SEEDS = 100;
    /** Default tick budget of one run (20 000 ticks ≈ 5.5 minutes of play). */
    public static final int DEFAULT_TICKS = 20_000;
    /** Default first seed; run {@code i} uses {@code seed0 + i}. */
    public static final long DEFAULT_FIRST_SEED = 1;
    /** Fresh drafts sampled for the pool rarity mix (M6). */
    public static final int POOL_SAMPLES = 5000;
    /** {@code --ability} value that equips nothing (the default, and the M1–M4 baseline). */
    public static final String NO_ABILITY = "none";
    /** {@code --modifier} value that forces no card (the baseline of a per-card sweep). */
    public static final String NO_MODIFIER = "none";
    /** {@code --pattern} value that streams the world's own spawns (the default). */
    public static final String NO_PATTERN = "none";
    /** {@code --challenge} value that plays no challenge (the default). */
    public static final String NO_CHALLENGE = "none";
    /** {@code --boss} value that plays whole runs rather than encounters (the default). */
    public static final String NO_BOSS = "none";

    private BalancingSim() {
    }

    /**
     * One finished simulation, with what it paid (M3, M4). The economy columns come from the real
     * {@link RunRewardCalculator} against the shipped {@code economy.json} and the run treated as
     * a later run (no first-run bonus).
     *
     * <p>{@code coins} keeps every multiplier at 1, so a cell of seeds stays directly comparable
     * with the coins-per-run tables in {@code docs/BALANCING.md}. {@code payout} is the same
     * formula under the run's own resolved {@code COIN_MULT} / {@code XP_MULT} and the tier's
     * reward multiplier, which is the only column that can tell the economy birds apart: without
     * it Ironbeak's {@code COIN_MULT} of 0.8 is invisible and classic, Ironbeak and Oracle print
     * identical numbers.
     *
     * <p>The three M8 columns: {@code objectiveMet} (a challenge run's objective), {@code
     * bossCleared} (the boss the run actually spawned was survived — the world's, or the
     * challenge's own) and {@code phasesReached}.
     */
    private record Row(String world, String tier, String pattern, String challenge, String bird,
            String ability, String forced, String skill, long seed,
            int gates, double points, int ticksAlive, boolean finished, String deathCause,
            int coinsSpawned, int coinsCollected, int streakBest, int streakSteps, long coins,
            long xp, long payout, int abilityUses, int shieldAbsorbs, int revives,
            int offersOpened, List<String> modifiers, List<String> synergies,
            boolean objectiveMet, boolean bossCleared, int phasesReached) {
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
        String ability = NO_ABILITY;
        int abilityLevel = 1;
        String modifier = NO_MODIFIER;
        int modifierStacks = 1;
        String pattern = NO_PATTERN;
        String challenge = NO_CHALLENGE;
        String boss = NO_BOSS;
        boolean worldGiven;
        boolean drafts;
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
        List<String> abilities = abilitiesOf(content, options.ability);
        List<BotPilot.Preset> presets = presetsOf(options.skill);
        List<Cell> where = cellsOf(content, options);

        System.out.println("Flapforge balancing — world=" + options.world + " tier=" + options.tier
                + " pattern=" + options.pattern + " challenge=" + options.challenge + " boss="
                + options.boss + " seeds=" + options.seeds + " (from " + options.firstSeed
                + ") ticks=" + options.ticks);
        List<List<String>> forcedSets = forcedOf(content, options);
        List<Row> rows = new ArrayList<>(where.size() * birds.size() * abilities.size()
                * presets.size() * options.seeds);
        Map<String, List<Row>> cells = new LinkedHashMap<>();
        for (Cell cell : where) {
            for (String bird : birds) {
                for (String ability : abilities) {
                    for (List<String> forced : forcedSets) {
                        for (BotPilot.Preset preset : presets) {
                            List<Row> played = simulate(factory, content, options, cell, bird,
                                    ability, forced, preset);
                            rows.addAll(played);
                            report(cell, bird, ability, label(forced), preset, options.ticks,
                                    played);
                            cells.put(cell + " " + label(forced) + " @ " + preset.name(),
                                    played);
                        }
                    }
                }
            }
        }
        if (forcedSets.size() > 1) {
            reportForcedDeltas(cells);
        }
        if (options.drafts) {
            reportDrafts(content, draftContext(content, options), rows);
        }
        if (options.csv != null) {
            writeCsv(options.csv, rows);
            System.out.println("CSV: " + options.csv.toAbsolutePath() + " (" + rows.size()
                    + " rows)");
        }
    }

    /**
     * Where a cell plays (M7, M8): the world, the tier, the pattern forced on it, the challenge
     * it is, and whether it starts at the world's boss.
     *
     * @param world the world id
     * @param tier the tier id
     * @param pattern the forced pattern id, or {@link #NO_PATTERN}
     * @param challenge the challenge id, or {@link #NO_CHALLENGE}
     * @param bossOnly whether the run starts at the world's boss ({@code --boss})
     */
    private record Cell(String world, String tier, String pattern, String challenge,
            boolean bossOnly) {
        @Override
        public String toString() {
            return "world=" + world + " tier=" + tier
                    + (NO_PATTERN.equals(pattern) ? "" : " pattern=" + pattern)
                    + (NO_CHALLENGE.equals(challenge) ? "" : " challenge=" + challenge)
                    + (bossOnly ? " boss=encounter" : "");
        }

        boolean isChallenge() {
            return !NO_CHALLENGE.equals(challenge);
        }
    }

    /**
     * The cells a sweep plays (M8). A challenge sweep is one cell per challenge, in its own world
     * and tier; a boss sweep is one cell per world (times the tiers asked for), started at the
     * boss; otherwise the M7 world × tier × pattern grid.
     *
     * @param content the loaded content
     * @param options the parsed command line
     * @return the cells, in report order
     */
    private static List<Cell> cellsOf(GameContent content, Options options) {
        List<Cell> cells = new ArrayList<>();
        if (!NO_CHALLENGE.equalsIgnoreCase(options.challenge)) {
            for (String id : challengesOf(content, options.challenge)) {
                ChallengeDef def = content.challenges().get(id);
                cells.add(new Cell(def.world(), def.tier(), NO_PATTERN, id, false));
            }
            return cells;
        }
        if (!NO_BOSS.equalsIgnoreCase(options.boss)) {
            for (String world : worldsOf(content, options.boss)) {
                for (String tier : tiersOf(content, options.tier)) {
                    cells.add(new Cell(world, tier, NO_PATTERN, NO_CHALLENGE, true));
                }
            }
            return cells;
        }
        for (String world : worldsOf(content, options.world)) {
            for (String tier : tiersOf(content, options.tier)) {
                for (String pattern : patternsOf(content, options.pattern)) {
                    // A pattern plays in its own world unless the sweep pinned one (M7).
                    String playedIn = options.worldGiven || NO_PATTERN.equals(pattern) ? world
                            : content.patterns().get(pattern).world();
                    cells.add(new Cell(playedIn, tier, pattern, NO_CHALLENGE, false));
                }
            }
        }
        return cells;
    }

    private static List<Row> simulate(RunFactory factory, GameContent content, Options options,
            Cell where, String bird, String ability, List<String> forced,
            BotPilot.Preset preset) {
        EconomyDef economy = content.economy();
        List<Row> rows = new ArrayList<>(options.seeds);
        for (int i = 0; i < options.seeds; i++) {
            long seed = options.firstSeed + i;
            RunConfig.Builder builder = equip(RunConfig.builder(seed).birdId(bird)
                    .worldId(where.world()).tierId(where.tier()), content, ability,
                    options.abilityLevel);
            if (options.drafts) {
                builder.allowOffers(true).availableModifiers(content.modifiers().ids());
            }
            if (!forced.isEmpty()) {
                builder.forcedModifiers(forced);
            }
            RunConfig config = builder.build();
            if (where.isChallenge()) {
                // The challenge's own world, tier, flags, forced cards and offer switch (M8).
                config = factory.challengeConfig(config, where.challenge());
            }
            Run run;
            if (!NO_PATTERN.equals(where.pattern())) {
                run = new Run(config, factory.setup(config)
                        .withForcedPattern(content.patternSpec(where.pattern())));
            } else if (where.bossOnly()) {
                RunSetup setup = factory.setup(config);
                if (setup.boss() == null) {
                    throw new IllegalArgumentException("world '" + where.world()
                            + "' has no boss to start at");
                }
                run = new Run(config, setup.startingAtBoss());
            } else {
                run = factory.newRun(config);
            }
            HeadlessRunner.Outcome outcome = HeadlessRunner.run(run,
                    new BotPilot(preset, seed), options.ticks);
            RunResult result = outcome.result();
            // Obstacle deaths are reported by kind (M7): a Storm Sky run that dies to a bolt and
            // one that dies to a gate are different balance facts.
            String cause = result.stats().deathCause() == null ? "ALIVE"
                    : result.stats().deathCause() == CollisionCause.OBSTACLE
                            && result.stats().deathKind() != null
                            ? result.stats().deathKind().name()
                            : result.stats().deathCause().name();
            RewardSummary rewards = RunRewardCalculator.compute(result, economy,
                    RewardContext.plain());
            RewardSummary paid = RunRewardCalculator.compute(result, economy,
                    RewardContext.plain().withMultipliers(
                            run.simulation().stats().resolve(StatId.COIN_MULT),
                            run.simulation().stats().resolve(StatId.XP_MULT),
                            run.setup().tier().rewardMult(), 1));
            int uses = 0;
            for (int used : result.stats().abilitiesUsed().values()) {
                uses += used;
            }
            rows.add(new Row(where.world(), where.tier(), where.pattern(), where.challenge(),
                    bird, ability, label(forced), preset.name(), seed,
                    result.gatesPassed(),
                    result.stats().points(), result.stats().ticksAlive(), outcome.finished(),
                    cause, run.simulation().pickups().spawnedCount(),
                    result.stats().coinsCollected(), result.stats().streakBest(),
                    result.stats().streakSteps(), rewards.coins(), rewards.xp(), paid.coins(),
                    uses, result.stats().shieldAbsorbs(), result.stats().revives(),
                    run.simulation().modifiers().offersOpened(),
                    List.copyOf(result.stats().modifiersTaken()),
                    List.copyOf(result.stats().synergiesActivated()),
                    result.stats().objectiveMet(), run.simulation().boss().isCleared(),
                    result.stats().phasesReached()));
        }
        return rows;
    }

    private static void report(Cell where, String bird, String ability, String forced,
            BotPilot.Preset preset, int ticks, List<Row> rows) {
        int[] gates = new int[rows.size()];
        int[] alive = new int[rows.size()];
        int[] coins = new int[rows.size()];
        int[] payout = new int[rows.size()];
        int[] xp = new int[rows.size()];
        int[] collected = new int[rows.size()];
        int[] spawned = new int[rows.size()];
        int[] streakBest = new int[rows.size()];
        int[] streakSteps = new int[rows.size()];
        int[] phases = new int[rows.size()];
        double points = 0;
        Map<String, Integer> causes = new LinkedHashMap<>();
        int survived = 0;
        int zeroCoins = 0;
        int objectives = 0;
        int bosses = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            gates[i] = row.gates();
            alive[i] = row.ticksAlive();
            coins[i] = (int) row.coins();
            payout[i] = (int) row.payout();
            xp[i] = (int) row.xp();
            collected[i] = row.coinsCollected();
            spawned[i] = row.coinsSpawned();
            streakBest[i] = row.streakBest();
            streakSteps[i] = row.streakSteps();
            phases[i] = row.phasesReached();
            points += row.points();
            causes.merge(row.deathCause(), 1, Integer::sum);
            if (!row.finished()) {
                survived++;
            }
            if (row.coins() == 0) {
                zeroCoins++;
            }
            if (row.objectiveMet()) {
                objectives++;
            }
            if (row.bossCleared()) {
                bosses++;
            }
        }
        Arrays.sort(gates);
        Arrays.sort(alive);
        Arrays.sort(coins);
        Arrays.sort(payout);
        Arrays.sort(streakBest);
        Arrays.sort(phases);
        System.out.println();
        System.out.printf(Locale.ROOT,
                "%s bird=%s ability=%s modifier=%s skill=%s (reaction %d ticks, error %.0f px)"
                        + " runs=%d%n",
                where, bird, ability, forced, preset.name(), preset.reactionTicks(),
                preset.errorPx(), rows.size());
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
        System.out.printf(Locale.ROOT,
                "  payout     p10=%d p50=%d p90=%d mean=%.2f (COIN_MULT and tier rewardMult"
                        + " applied)%n",
                percentile(payout, 10), percentile(payout, 50), percentile(payout, 90),
                mean(payout));
        System.out.printf(Locale.ROOT, "  xp         mean=%.1f%n", mean(xp));
        System.out.printf(Locale.ROOT,
                "  streak     best mean=%.2f max=%d  steps mean=%.2f (one every %d clean gates)%n",
                mean(streakBest), streakBest[streakBest.length - 1], mean(streakSteps),
                StreakTracker.DEFAULT_STEP);
        System.out.printf(Locale.ROOT, "  reached the %d-tick budget: %d/%d (%.1f %%)%n", ticks,
                survived, rows.size(), 100.0 * survived / rows.size());
        if (where.isChallenge()) {
            System.out.printf(Locale.ROOT, "  objective  met %d/%d (%.1f %%)%n", objectives,
                    rows.size(), 100.0 * objectives / rows.size());
        }
        if (where.isChallenge() || where.bossOnly() || bosses > 0
                || phases[phases.length - 1] > 0) {
            System.out.printf(Locale.ROOT,
                    "  boss       cleared %d/%d (%.1f %%)  phases reached mean=%.2f max=%d%n",
                    bosses, rows.size(), 100.0 * bosses / rows.size(), mean(phases),
                    phases[phases.length - 1]);
        }
        if (!NO_ABILITY.equals(ability)) {
            int[] uses = new int[rows.size()];
            int[] absorbs = new int[rows.size()];
            int[] revives = new int[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                uses[i] = rows.get(i).abilityUses();
                absorbs[i] = rows.get(i).shieldAbsorbs();
                revives[i] = rows.get(i).revives();
            }
            System.out.printf(Locale.ROOT,
                    "  ability    uses mean=%.2f  shield absorbs mean=%.2f  revives mean=%.2f%n",
                    mean(uses), mean(absorbs), mean(revives));
        }
        StringBuilder deaths = new StringBuilder("  deaths     ");
        for (Map.Entry<String, Integer> e : causes.entrySet()) {
            deaths.append(e.getKey()).append('=').append(e.getValue()).append("  ");
        }
        System.out.println(deaths.toString().stripTrailing());
    }

    /**
     * The M6 draft table (§6): how far the runs got into the schedule, what the bot took and how
     * often a build came together.
     *
     * <p>Two rarity columns, and they answer different questions. "taken" is what the bot ended a
     * run holding — the first card of every draft it reached, which is exactly one weighted draw
     * each. "pool" samples whole offers from a fresh pool over {@value #POOL_SAMPLES} seeds, so it
     * shows the mix a <em>player</em> sees on the table rather than the mix a bot that never
     * chooses ends up with.
     *
     * @param content the shipped content
     * @param rows every run of the sweep
     */
    private static void reportDrafts(GameContent content, DraftContext context, List<Row> rows) {
        int runs = rows.size();
        int[] reached = new int[content.modifierBlock().offerSchedule().size() + 1];
        int withSynergy = 0;
        int reachedThird = 0;
        int reachedThirdWithSynergy = 0;
        Map<Rarity, Integer> taken = new EnumMap<>(Rarity.class);
        int cards = 0;
        for (Row row : rows) {
            reached[Math.min(row.offersOpened(), reached.length - 1)]++;
            if (!row.synergies().isEmpty()) {
                withSynergy++;
            }
            if (row.offersOpened() >= 3) {
                reachedThird++;
                if (!row.synergies().isEmpty()) {
                    reachedThirdWithSynergy++;
                }
            }
            for (String id : row.modifiers()) {
                ModifierDef def = content.modifiers().get(id);
                taken.merge(def.rarity(), 1, Integer::sum);
                cards++;
            }
        }
        System.out.println();
        System.out.println("  drafts (M6)");
        StringBuilder line = new StringBuilder("    offers     ");
        for (int i = 1; i < reached.length; i++) {
            int atLeast = 0;
            for (int j = i; j < reached.length; j++) {
                atLeast += reached[j];
            }
            line.append(String.format(Locale.ROOT, "%d+=%d (%.1f %%)  ", i, atLeast,
                    100.0 * atLeast / runs));
        }
        System.out.println(line.toString().stripTrailing());
        System.out.printf(Locale.ROOT, "    cards      %d taken over %d runs (%.2f per run)%n",
                cards, runs, cards / (double) runs);
        System.out.println("    taken      " + rarityLine(taken, cards));
        System.out.println("    pool       "
                + rarityLine(poolSample(content, context), POOL_SAMPLES * 3));
        System.out.printf(Locale.ROOT, "    synergies  %d/%d runs (%.1f %%); of the %d runs that "
                + "reached offer 3: %d (%.1f %%)%n", withSynergy, runs, 100.0 * withSynergy / runs,
                reachedThird, reachedThirdWithSynergy,
                reachedThird == 0 ? 0 : 100.0 * reachedThirdWithSynergy / reachedThird);
    }

    private static String rarityLine(Map<Rarity, Integer> counts, int total) {
        StringBuilder out = new StringBuilder();
        for (Rarity rarity : Rarity.values()) {
            int n = counts.getOrDefault(rarity, 0);
            out.append(String.format(Locale.ROOT, "%s=%d (%.1f %%)  ", rarity, n,
                    total == 0 ? 0 : 100.0 * n / total));
        }
        return out.toString().stripTrailing();
    }

    /**
     * Draws {@value #POOL_SAMPLES} fresh drafts straight from the shipped pool, so the rarity mix
     * on the table can be read without a bot in the way.
     *
     * @param content the shipped content
     * @param context the run shape the sweep plays, so the sampled table is the table those runs
     *     would really see (E12: a card the loadout cannot use is not offered)
     * @return the count per rarity over every card of every sampled draft
     */
    private static Map<Rarity, Integer> poolSample(GameContent content, DraftContext context) {
        ModifierCatalog catalog = content.modifierCatalog(content.modifiers().ids());
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (long seed = 0; seed < POOL_SAMPLES; seed++) {
            ModifierPool pool = new ModifierPool(catalog, context,
                    new RandomProvider(seed).stream(RandomProvider.OFFERS));
            ModifierOffer offer = pool.draw(0, 10, Map.of());
            for (ModifierOffer.Card card : offer.cards()) {
                counts.merge(card.rarity(), 1, Integer::sum);
            }
        }
        return counts;
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

    /**
     * The worlds a sweep iterates (M7): one id, or {@code all} for every world of
     * {@code worlds.json} in order.
     *
     * @param content the loaded content
     * @param selector the {@code --world} value
     * @return the world ids
     */
    private static List<String> worldsOf(GameContent content, String selector) {
        if ("all".equalsIgnoreCase(selector)) {
            List<String> ids = new ArrayList<>(content.worlds().size());
            for (WorldDef def : content.worlds()) {
                ids.add(def.id());
            }
            return ids;
        }
        if (content.has(GameContent.WORLDS)) {
            content.worlds().get(selector);
        }
        return List.of(selector);
    }

    /**
     * The tiers a sweep iterates (M7): one id, or {@code all} for every tier of
     * {@code difficulty.json} in order.
     *
     * @param content the loaded content
     * @param selector the {@code --tier} value
     * @return the tier ids
     */
    private static List<String> tiersOf(GameContent content, String selector) {
        if ("all".equalsIgnoreCase(selector)) {
            List<String> ids = new ArrayList<>(content.tiers().size());
            for (TierDef def : content.tiers()) {
                ids.add(def.id());
            }
            return ids;
        }
        content.tiers().get(selector);
        return List.of(selector);
    }

    /**
     * The patterns a sweep forces (M7): {@code none} for the world's own spawns, one id, or
     * {@code all} for every pattern of {@code patterns.json} — boss phases and corridors
     * included, each looped in isolation.
     *
     * @param content the loaded content
     * @param selector the {@code --pattern} value
     * @return the pattern ids, with {@link #NO_PATTERN} standing for no forced pattern
     */
    private static List<String> patternsOf(GameContent content, String selector) {
        if (NO_PATTERN.equalsIgnoreCase(selector)) {
            return List.of(NO_PATTERN);
        }
        if ("all".equalsIgnoreCase(selector)) {
            List<String> ids = new ArrayList<>(content.patterns().size());
            for (PatternDef def : content.patterns()) {
                ids.add(def.id());
            }
            return ids;
        }
        content.patterns().get(selector);
        return List.of(selector);
    }

    /**
     * The challenges a sweep plays (M8): one id, or {@code all} for every challenge of
     * {@code challenges.json} in order.
     *
     * @param content the loaded content
     * @param selector the {@code --challenge} value
     * @return the challenge ids
     */
    private static List<String> challengesOf(GameContent content, String selector) {
        if ("all".equalsIgnoreCase(selector)) {
            List<String> ids = new ArrayList<>(content.challenges().size());
            for (ChallengeDef def : content.challenges()) {
                ids.add(def.id());
            }
            return ids;
        }
        content.challenges().get(selector);
        return List.of(selector);
    }

    /**
     * The abilities a sweep iterates: {@code none}, one id, or {@code all} for every ability the
     * content ships (M5).
     *
     * @param content the loaded content
     * @param selector the {@code --ability} value
     * @return the ability ids, with {@link #NO_ABILITY} standing for an empty loadout
     */
    private static List<String> abilitiesOf(GameContent content, String selector) {
        if (NO_ABILITY.equalsIgnoreCase(selector)) {
            return List.of(NO_ABILITY);
        }
        if ("all".equalsIgnoreCase(selector)) {
            List<String> ids = new ArrayList<>(content.abilities().size() + 1);
            ids.add(NO_ABILITY);
            ids.addAll(content.abilities().ids());
            return ids;
        }
        content.abilities().get(selector);
        return List.of(selector);
    }

    /**
     * Equips one ability in the slot its kind belongs to (D9), at the requested level.
     *
     * @param builder the configuration being built
     * @param content the loaded content
     * @param ability the ability id, or {@link #NO_ABILITY}
     * @param level the level to play it at
     * @return the same builder
     */
    private static RunConfig.Builder equip(RunConfig.Builder builder, GameContent content,
            String ability, int level) {
        if (NO_ABILITY.equals(ability)) {
            return builder;
        }
        AbilityDef def = content.abilities().get(ability);
        builder.abilityLevels(Map.of(ability, level));
        return def.kind() == AbilityKind.ACTIVE
                ? builder.activeAbilityId(ability)
                : builder.passiveAbilityIds(List.of(ability));
    }

    /**
     * The forced-card cells a sweep iterates ({@code --modifier}, M6).
     *
     * <p>{@code none} is the baseline and always comes first; {@code all} sweeps the baseline plus
     * every shipped card on its own; anything else is a comma-separated build forced as written.
     * {@code --modifier-stacks N} repeats each id, which is how a second stack is measured —
     * the director caps at {@code maxStacks}, so asking for more than a card allows is harmless.
     *
     * @param content the loaded content
     * @param options the parsed command line
     * @return one list of forced ids per cell
     */
    private static List<List<String>> forcedOf(GameContent content, Options options) {
        if (NO_MODIFIER.equalsIgnoreCase(options.modifier)) {
            return List.of(List.of());
        }
        List<List<String>> out = new ArrayList<>();
        out.add(List.of());
        if ("all".equalsIgnoreCase(options.modifier)) {
            for (String id : content.modifiers().ids()) {
                out.add(stacked(List.of(id), options.modifierStacks));
            }
            return out;
        }
        List<String> ids = new ArrayList<>();
        for (String id : options.modifier.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                content.modifiers().get(trimmed);
                ids.add(trimmed);
            }
        }
        out.add(stacked(ids, options.modifierStacks));
        return out;
    }

    private static List<String> stacked(List<String> ids, int stacks) {
        List<String> out = new ArrayList<>(ids.size() * stacks);
        for (String id : ids) {
            for (int i = 0; i < Math.max(1, stacks); i++) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The eligibility context of the sweep's runs (E12): the rules are empty, and the two ability
     * timing stats matter only when the equipped ability declares a cooldown or a duration.
     *
     * @param content the loaded content
     * @param options the parsed command line
     * @return the context the drafts of this sweep draw under
     */
    private static DraftContext draftContext(GameContent content, Options options) {
        boolean cooldown = false;
        boolean duration = false;
        if (!NO_ABILITY.equals(options.ability) && content.abilities().contains(options.ability)) {
            List<AbilityLevelDef> levels = content.abilities().get(options.ability).levels();
            AbilityLevelDef level =
                    levels.get(Math.min(options.abilityLevel, levels.size()) - 1);
            cooldown = level.cooldownTicks() > 0;
            duration = level.durationTicks() > 0;
        }
        boolean hasCooldown = cooldown;
        boolean hasDuration = duration;
        return new DraftContext() {
            @Override
            public RuleSet rules() {
                return RuleSet.EMPTY;
            }

            @Override
            public boolean abilityCooldownMatters() {
                return hasCooldown;
            }

            @Override
            public boolean abilityDurationMatters() {
                return hasDuration;
            }
        };
    }

    private static String label(List<String> forced) {
        return forced.isEmpty() ? NO_MODIFIER : String.join("+", forced);
    }

    /**
     * What each forced cell was worth against the {@code none} baseline of the same skill preset
     * (M6): the table {@code docs/BALANCING.md} §8 quotes per card.
     *
     * @param cells every cell of the sweep, keyed by forced set and preset
     */
    private static void reportForcedDeltas(Map<String, List<Row>> cells) {
        System.out.println();
        System.out.println("  per-card deltas (M6), against the 'none' cell of the same skill");
        System.out.printf(Locale.ROOT, "    %-34s %9s %9s %9s %9s%n",
                "cell", "ticks", "gates", "payout", "coins");
        Map<String, double[]> baselines = new LinkedHashMap<>();
        for (Map.Entry<String, List<Row>> cell : cells.entrySet()) {
            if (isBaselineKey(cell.getKey())) {
                baselines.put(cell.getKey(), means(cell.getValue()));
            }
        }
        for (Map.Entry<String, List<Row>> cell : cells.entrySet()) {
            double[] m = means(cell.getValue());
            boolean isBaseline = isBaselineKey(cell.getKey());
            double[] base = baselines.get(baselineKeyOf(cell.getKey()));
            System.out.printf(Locale.ROOT, "    %-34s %9.1f %9.1f %9.1f %9.1f", cell.getKey(),
                    m[0], m[1], m[2], m[3]);
            if (base != null && !isBaseline) {
                System.out.printf(Locale.ROOT, "   payout %+.1f %%",
                        base[2] == 0 ? 0 : 100.0 * (m[2] - base[2]) / base[2]);
            }
            System.out.println();
        }
    }

    /** Whether a cell key names the forced-nothing baseline of its world, tier and preset. */
    private static boolean isBaselineKey(String key) {
        return key.contains(" " + NO_MODIFIER + " @ ");
    }

    /** The key of the baseline cell a forced cell is compared with. */
    private static String baselineKeyOf(String key) {
        int at = key.lastIndexOf(" @ ");
        int space = key.lastIndexOf(' ', at - 1);
        return key.substring(0, space + 1) + NO_MODIFIER + key.substring(at);
    }

    /** Mean ticksAlive, gates, payout and coins of a cell. */
    private static double[] means(List<Row> rows) {
        double ticks = 0;
        double gates = 0;
        double payout = 0;
        double coins = 0;
        for (Row row : rows) {
            ticks += row.ticksAlive();
            gates += row.gates();
            payout += row.payout();
            coins += row.coins();
        }
        int n = Math.max(1, rows.size());
        return new double[] {ticks / n, gates / n, payout / n, coins / n};
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
        lines.add("world,tier,pattern,challenge,bird,ability,forced,skill,seed,gates,points,"
                + "ticksAlive,finished,deathCause,coinsSpawned,coinsCollected,streakBest,"
                + "streakSteps,coins,xp,payout,abilityUses,shieldAbsorbs,revives,offers,"
                + "modifiers,synergies,objectiveMet,bossCleared,phasesReached");
        for (Row r : rows) {
            lines.add(String.format(Locale.ROOT,
                    "%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s,%d,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,"
                            + "%s,%s,%s,%s,%d",
                    r.world(), r.tier(), r.pattern(), r.challenge(),
                    r.bird(), r.ability(), r.forced(), r.skill(), r.seed(), r.gates(),
                    Double.toString(r.points()), r.ticksAlive(), r.finished(), r.deathCause(),
                    r.coinsSpawned(), r.coinsCollected(), r.streakBest(), r.streakSteps(),
                    r.coins(), r.xp(), r.payout(), r.abilityUses(), r.shieldAbsorbs(),
                    r.revives(), r.offersOpened(), String.join("|", r.modifiers()),
                    String.join("|", r.synergies()), r.objectiveMet(), r.bossCleared(),
                    r.phasesReached()));
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
                case "--world" -> {
                    options.world = next(args, ++i);
                    options.worldGiven = true;
                }
                case "--tier" -> options.tier = next(args, ++i);
                case "--skill" -> options.skill = next(args, ++i);
                case "--ability" -> options.ability = next(args, ++i);
                case "--ability-level" -> options.abilityLevel =
                        positiveInt(arg, next(args, ++i));
                case "--modifier" -> options.modifier = next(args, ++i);
                case "--modifier-stacks" -> options.modifierStacks =
                        positiveInt(arg, next(args, ++i));
                case "--pattern" -> options.pattern = next(args, ++i);
                case "--challenge" -> options.challenge = next(args, ++i);
                case "--boss" -> options.boss = next(args, ++i);
                case "--drafts" -> options.drafts = true;
                case "--csv" -> options.csv = Path.of(next(args, ++i));
                case "--help", "-h" -> options.help = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (!NO_CHALLENGE.equalsIgnoreCase(options.challenge)
                && !NO_BOSS.equalsIgnoreCase(options.boss)) {
            throw new IllegalArgumentException("--challenge and --boss cannot be combined");
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
                "  --world ID    world id, or 'all' to iterate worlds.json (default "
                        + RunConfig.DEFAULT_WORLD + ")",
                "  --tier ID     tier id, or 'all' to iterate difficulty.json (default "
                        + RunConfig.DEFAULT_TIER + ")",
                "  --pattern ID  stream one pattern of patterns.json in isolation, looped, in"
                        + " the pattern's own world unless --world is given; 'none' or 'all' (M7)",
                "  --challenge ID play one challenge of challenges.json as the player would"
                        + " (its world, tier, rules, boss); 'none' or 'all' (M8)",
                "  --boss ID     play a world's boss encounter on its own, started at the boss;"
                        + " a world id, 'none' or 'all' (M8)",
                "  --skill NAME  novice | average | expert | perfect | all (default average)",
                "  --ability ID  ability to equip, 'none' or 'all' (default " + NO_ABILITY + ")",
                "  --ability-level N  level to play the ability at (default 1)",
                "  --modifier ID forced card(s) to sweep: 'none', 'all', or a comma-separated"
                        + " build (M6)",
                "  --modifier-stacks N  how many copies of each forced card (default 1)",
                "  --drafts      open modifier drafts; the bot takes the first card (M6)",
                "  --csv PATH    write one row per run",
                "  --help        this text");
    }
}
