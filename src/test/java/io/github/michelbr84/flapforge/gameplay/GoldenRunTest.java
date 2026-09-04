package io.github.michelbr84.flapforge.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.RunStats;
import io.github.michelbr84.flapforge.support.TestContent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The frozen reference run (D12): seed 42, the {@code PERFECT} pilot and the frozen content
 * fixture, 3000 ticks, compared tick by tick against
 * {@code src/test/resources/fixtures/golden_seed42.txt}.
 *
 * <p>Any change to the physics, the spawner, the stat pipeline, the bot or the frozen content
 * moves this file. That is the point: the diff is the review. Re-record with
 * {@code ./gradlew test -Pflapforge.updateGolden=true} — the run then rewrites the fixture and
 * fails on purpose, so the developer inspects the diff and re-runs without the flag.
 */
class GoldenRunTest {

    /** Seed of the reference run. */
    static final long SEED = 42;
    /** Tick budget of the reference run. */
    static final int TICKS = 3000;
    /** A state hash is recorded every this many ticks. */
    static final int HASH_EVERY = 50;
    /** Where the fixture lives, relative to the project root (the Gradle working directory). */
    static final Path GOLDEN =
            Path.of("src", "test", "resources", "fixtures", "golden_seed42.txt");
    /** System property that switches the test from asserting to re-recording. */
    static final String UPDATE_PROPERTY = "flapforge.updateGolden";

    @Test
    void theReferenceRunMatchesTheGoldenFile() {
        List<String> actual = record();
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            write(actual);
            fail("Golden fixture rewritten at " + GOLDEN + ". Review the diff, then re-run "
                    + "without -P" + UPDATE_PROPERTY + " to assert against it.");
        }
        assertTrue(Files.isRegularFile(GOLDEN), () -> "Missing " + GOLDEN
                + " — record it with ./gradlew test -P" + UPDATE_PROPERTY + "=true");
        assertEquals(read(), actual, "the reference run drifted from the golden fixture");
    }

    @Test
    void theGoldenRunIsReproducibleWithinTheSameJvm() {
        assertEquals(record(), record());
    }

    @Test
    void theGoldenRunUsesTheFrozenContent() {
        Run run = TestContent.frozenFactory().newRun(RunConfig.classic(SEED));
        assertEquals("classic", run.setup().bird().id());
        assertEquals("green_fields", run.setup().world().id());
        assertEquals("classic", run.setup().world().curve().id());
        assertEquals("normal", run.setup().tier().id());
        assertEquals(0.0005, run.setup().speedRampPerTick());
    }

    /** Runs the reference run and renders it as the lines of the golden file. */
    private static List<String> record() {
        RunFactory factory = TestContent.frozenFactory();
        Run run = factory.newRun(RunConfig.classic(SEED));
        HeadlessRunner.Outcome outcome =
                HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.PERFECT, SEED), TICKS, true);

        List<String> lines = new ArrayList<>();
        lines.add("# Flapforge golden run — frozen content fixture, do not edit by hand");
        lines.add("# Re-record with: ./gradlew test -P" + UPDATE_PROPERTY + "=true");
        lines.add("seed=" + SEED);
        lines.add("pilot=" + BotPilot.Preset.PERFECT.name());
        lines.add("maxTicks=" + TICKS);
        lines.add("hashEvery=" + HASH_EVERY);
        lines.add("ticks=" + outcome.ticks());
        lines.add("finished=" + outcome.finished());
        List<Long> hashes = outcome.hashes();
        for (int i = HASH_EVERY - 1; i < hashes.size(); i += HASH_EVERY) {
            lines.add("tick=" + (i + 1) + " hash=" + hex(hashes.get(i)));
        }
        lines.add("finalHash=" + hex(hashes.isEmpty() ? 0 : hashes.get(hashes.size() - 1)));

        RunResult result = outcome.result();
        RunStats stats = result.stats();
        lines.add("stats.gatesPassed=" + stats.gatesPassed());
        lines.add("stats.points=" + stats.points());
        lines.add("stats.coinsCollected=" + stats.coinsCollected());
        lines.add("stats.streak=" + stats.streak());
        lines.add("stats.streakBest=" + stats.streakBest());
        lines.add("stats.streakSteps=" + stats.streakSteps());
        lines.add("stats.ticksAlive=" + stats.ticksAlive());
        lines.add("stats.deathCause=" + stats.deathCause());
        lines.add("stats.shieldAbsorbs=" + stats.shieldAbsorbs());
        lines.add("stats.revives=" + stats.revives());
        lines.add("stats.nearMisses=" + stats.nearMisses());
        lines.add("stats.phasesReached=" + stats.phasesReached());
        lines.add("stats.objectiveMet=" + stats.objectiveMet());
        lines.add("stats.abilitiesUsed=" + stats.abilitiesUsed());
        lines.add("stats.modifiersTaken=" + stats.modifiersTaken());
        lines.add("stats.synergiesActivated=" + stats.synergiesActivated());
        lines.add("stats.bossesCleared=" + stats.bossesCleared());
        for (Map.Entry<String, Long> e : result.counters().entrySet()) {
            lines.add("counter." + e.getKey() + "=" + e.getValue());
        }
        return lines;
    }

    private static String hex(long value) {
        return String.format(Locale.ROOT, "%016x", value);
    }

    private static List<String> read() {
        try {
            return Files.readAllLines(GOLDEN, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + GOLDEN, e);
        }
    }

    private static void write(List<String> lines) {
        try {
            Files.createDirectories(GOLDEN.getParent());
            Files.write(GOLDEN, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + GOLDEN, e);
        }
    }
}
