package io.github.michelbr84.flapforge.modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D11 in every shipped world (M7 fix): a breather opens its draft within a few hundred ticks
 * whatever the world was spawning — a 112 px gear, a 200 px wind zone or a pattern step 130 px
 * on. The deferral is an absolute clearance behind the last column, so the window
 * {@code isDraftPathClear} needs always exists and {@link ModifierDirector#BREATHER_RETRY_TICKS}
 * never fires: no breather lasts longer than {@value #MAX_BREATHER_TICKS} ticks, which is well
 * under the retry.
 */
class BreatherClearanceTest {

    /** Longest breather tolerated, in ticks: about the clearance at the slowest scroll. */
    static final int MAX_BREATHER_TICKS = 300;
    private static final int SEEDS = 12;
    private static final int TICKS = 5000;

    @Test
    void everyBreatherInEveryWorldOpensItsDraftWithinTheClearance() {
        GameContent content = GameContent.load();
        RunFactory factory = new RunFactory(content);
        int breathers = 0;
        int drafts = 0;
        List<String> failures = new ArrayList<>();
        for (WorldDef world : content.worlds()) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                for (boolean take : new boolean[] {true, false}) {
                    RunConfig config = RunConfig.builder(seed).worldId(world.id())
                            .allowOffers(true).build();
                    Run run = factory.newRun(config);
                    BotPilot bot = new BotPilot(BotPilot.Preset.PERFECT, seed);
                    int breatherStart = -1;
                    int longest = 0;
                    for (int t = 0; t < TICKS && !run.isFinished(); t++) {
                        RunInput input = run.phase() == RunPhase.CHOOSING_MODIFIER
                                ? (take ? RunInput.choose(BotPilot.FIRST_CARD) : RunInput.skip())
                                : bot.decide(run);
                        run.tick(input);
                        if (run.phase() == RunPhase.BREATHER) {
                            if (breatherStart < 0) {
                                breatherStart = run.tick();
                                breathers++;
                            }
                            longest = Math.max(longest, run.tick() - breatherStart);
                        } else {
                            breatherStart = -1;
                        }
                        if (run.phase() == RunPhase.CHOOSING_MODIFIER
                                && run.simulation().modifiers().offer() != null) {
                            drafts++;
                        }
                    }
                    if (longest > MAX_BREATHER_TICKS) {
                        failures.add(world.id() + " seed " + seed + (take ? " take" : " skip")
                                + ": a breather lasted " + longest + " ticks");
                    }
                }
            }
        }
        assertTrue(breathers >= 5 * SEEDS, "breathers seen: " + breathers);
        assertTrue(drafts > 0, "drafts opened: " + drafts);
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }
}
