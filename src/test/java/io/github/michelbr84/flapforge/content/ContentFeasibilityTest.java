package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.PatternDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Risk 11, §6 M7: the shipped worlds and patterns are winnable by the expert bot. Every number
 * here is reproducible from the command line with {@code BalancingSim} — the same seeds, the
 * same preset, the same tick budgets — and the table is recorded in {@code docs/BALANCING.md}
 * §10.
 *
 * <ul>
 *   <li>world × tier: the expert reaches {@code boss.atGate} in at least {@value #MIN_RATE} of
 *       {@value #SEEDS} seeds — {@code --world all --tier all --skill expert --seeds 50};</li>
 *   <li>every pattern in isolation (boss phases and corridors included): the expert is still
 *       flying after {@value #PATTERN_TICKS} ticks in at least {@value #MIN_RATE} of the seeds —
 *       {@code --pattern all --skill expert --seeds 50 --ticks 2400}. The budget is longer than
 *       any shipped {@code surviveTicks}, so surviving it is surviving a whole boss fight on
 *       that phase.</li>
 * </ul>
 *
 * <p>Every cell is held to the same bar. Green Fields nightmare is the tightest (32 % on the
 * review pass of 2026-09-02, up from 22 %): the tier's {@code ALL_OBSTACLES_MOVE} no longer
 * bends the gate layout roll (E32.d: the rule is applied at materialisation, so a gate rolled
 * static keeps the static layout mix, half of which floats), and M9's tier balance reads the
 * table from here.
 */
@Tag("sim")
class ContentFeasibilityTest {

    /** Seeds per cell (§6). */
    static final int SEEDS = 50;
    /** Required success rate (§6). */
    static final double MIN_RATE = 0.30;
    /** Tick budget of a world run (the balancing default). */
    static final int WORLD_TICKS = 20_000;
    /** Tick budget of an isolated pattern: longer than every shipped {@code surviveTicks}. */
    static final int PATTERN_TICKS = 2400;
    /** The tiers every world is measured on. */
    static final List<String> TIERS = List.of("normal", "hard", "nightmare");

    private static final GameContent CONTENT = GameContent.load();
    private static final RunFactory FACTORY = new RunFactory(CONTENT);

    @Test
    void theExpertReachesTheBossGateOnEveryWorldAndTier() {
        List<String> report = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (WorldDef world : CONTENT.worlds()) {
            for (String tier : TIERS) {
                int reached = 0;
                for (int i = 0; i < SEEDS; i++) {
                    long seed = 1 + i;
                    Run run = FACTORY.newRun(RunConfig.builder(seed).worldId(world.id())
                            .tierId(tier).build());
                    HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.EXPERT, seed),
                            WORLD_TICKS);
                    if (run.stats().gatesPassed() >= world.boss().atGate()) {
                        reached++;
                    }
                }
                double rate = reached / (double) SEEDS;
                String line = String.format(Locale.ROOT, "%s/%s: %d/%d reach gate %d (%.0f %%,"
                        + " required %.0f %%)", world.id(), tier, reached, SEEDS,
                        world.boss().atGate(), 100 * rate, 100 * MIN_RATE);
                report.add(line);
                if (rate < MIN_RATE) {
                    failures.add(line);
                }
            }
        }
        System.out.println(String.join(System.lineSeparator(), report));
        assertTrue(failures.isEmpty(), () -> "expert clear rate below the bar:\n"
                + String.join("\n", failures));
    }

    @Test
    void theExpertSurvivesEveryPatternInIsolation() {
        List<String> report = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (PatternDef pattern : CONTENT.patterns()) {
            int survived = 0;
            for (int i = 0; i < SEEDS; i++) {
                long seed = 1 + i;
                RunConfig config = RunConfig.builder(seed).worldId(pattern.world()).build();
                Run run = new Run(config, FACTORY.setup(config)
                        .withForcedPattern(CONTENT.patternSpec(pattern.id())));
                HeadlessRunner.Outcome outcome = HeadlessRunner.run(run,
                        new BotPilot(BotPilot.Preset.EXPERT, seed), PATTERN_TICKS);
                if (!outcome.finished()) {
                    survived++;
                }
            }
            double rate = survived / (double) SEEDS;
            String line = String.format(Locale.ROOT, "%s (%s): %d/%d survive %d ticks (%.0f %%)",
                    pattern.id(), pattern.world(), survived, SEEDS, PATTERN_TICKS, 100 * rate);
            report.add(line);
            if (rate < MIN_RATE) {
                failures.add(line);
            }
        }
        System.out.println(String.join(System.lineSeparator(), report));
        assertTrue(failures.isEmpty(), () -> "expert survival below the bar:\n"
                + String.join("\n", failures));
    }
}
