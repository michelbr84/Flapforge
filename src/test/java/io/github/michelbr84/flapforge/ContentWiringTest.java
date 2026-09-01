package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.ui.screens.ClassicRunFactory;
import io.github.michelbr84.flapforge.ui.screens.ContentRunFactory;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The seam {@code GameApplication} wires: the screens play runs built from the shipped content
 * (D10, D11), not from the hard-coded seam records. For M1 the two must be indistinguishable —
 * the moment they are not, either the data files or the seam records moved without the other.
 */
class ContentWiringTest {

    private static final GameContent SHIPPED = GameContent.load();
    private static final long SEED = 42;
    private static final int TICKS = 3000;

    @Test
    void theContentFactoryStampsTheModeTheLaunchChose() {
        assertEquals(RunMode.STANDARD, new ContentRunFactory(SHIPPED).newRun(SEED).config().mode());
        assertEquals(RunMode.SEEDED,
                new ContentRunFactory(SHIPPED, RunMode.SEEDED).newRun(SEED).config().mode());
        assertSame(SHIPPED, new ContentRunFactory(SHIPPED).content());
    }

    @Test
    void theContentFactoryBuildsTheDefaultConfigurationForASeed() {
        Run run = new ContentRunFactory(SHIPPED).newRun(SEED);
        RunConfig config = run.config();
        assertEquals(SEED, config.seed());
        assertEquals(RunConfig.DEFAULT_BIRD, config.birdId());
        assertEquals(RunConfig.DEFAULT_WORLD, config.worldId());
        assertEquals(RunConfig.DEFAULT_TIER, config.tierId());
        assertEquals(RunSetup.CLASSIC, run.setup(), "M1 content resolves to the classic seam");
        assertFalse(run.isFinished());
    }

    @Test
    void theShippedContentPlaysExactlyLikeTheHardCodedClassicSeam() {
        assertEquals(hashes(new ClassicRunFactory()), hashes(new ContentRunFactory(SHIPPED)),
                "swapping the content-backed factory in must not change a single tick");
    }

    private static List<Long> hashes(SeededRunSource factory) {
        return HeadlessRunner.run(factory.newRun(SEED),
                new BotPilot(BotPilot.Preset.PERFECT, SEED), TICKS, true).hashes();
    }
}
