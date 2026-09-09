package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * No stat on the Forge is decorative (M13): for every node in {@code upgrades.json}, every level
 * moves every stat the node claims to touch, in the direction the authored operation says, on the
 * very {@link StatSheet} a run is started with.
 *
 * <p>This is the guard against a screen that shows a number nothing reads. It asks the question
 * the other way round — not "does something read this stat?" but "does buying this node change
 * the sheet the bird flies with?" — because a change that never reaches the sheet could not be
 * read by anything.
 */
class ForgeStatRealityTest {

    /** Below this a difference is float noise, not an effect. */
    private static final double EPSILON = 1e-9;

    private GameContent content;
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        content = GameContent.load();
        profile = PlayerProfile.fresh(1_700_000_000_000L).normalize();
    }

    @Test
    void everyNodeMovesEveryStatItClaimsInTheDirectionItSays() {
        List<String> failures = new ArrayList<>();
        for (UpgradeDef def : content.upgrades()) {
            if (def.effectsPerLevel().isEmpty()) {
                continue;
            }
            openTree(def.tree());
            for (String prereq : def.prereqs()) {
                profile.upgrades.put(prereq, 1);
            }
            for (StatModifierDef effect : def.effectsPerLevel()) {
                double previous = statOf(effect.stat());
                double direction = directionOf(effect.op(), effect.value());
                for (int level = 1; level <= def.maxLevel(); level++) {
                    profile.upgrades.put(def.id(), level);
                    double current = statOf(effect.stat());
                    double step = current - previous;
                    if (Math.abs(step) <= EPSILON) {
                        failures.add(def.id() + " level " + level + " leaves " + effect.stat()
                                + " at " + current);
                    } else if (direction != 0 && Math.signum(step) != direction
                            && !atBound(effect.stat(), current)) {
                        failures.add(def.id() + " level " + level + " moves " + effect.stat()
                                + " by " + step + ", the wrong way for " + effect.op() + " "
                                + effect.value());
                    }
                    previous = current;
                }
                profile.upgrades.remove(def.id());
            }
        }
        assertEquals(List.of(), failures, "a node that does not move its own stat is a fake stat");
    }

    @Test
    void thePanelAndTheRunReadTheSameSheet() {
        profile.upgrades.put("feather_1", 3);
        StatSheet panel = RunLoadout.previewStats(profile, content);
        StatSheet defaults = StatSheet.defaults();

        assertTrue(Math.abs(panel.resolve(StatId.GRAVITY) - defaults.resolve(StatId.GRAVITY))
                > EPSILON, "three levels of feather_1 must reach the sheet a run is started with");
        assertTrue(panel.resolve(StatId.GRAVITY) < defaults.resolve(StatId.GRAVITY),
                "feather_1 is a gravity reduction");
    }

    @Test
    void everyTreeShowsAtLeastOneStatItCanMove() {
        Set<String> trees = new LinkedHashSet<>();
        for (UpgradeDef def : content.upgrades()) {
            trees.add(def.tree());
        }
        assertEquals(Set.of("flight", "economy", "forge"), trees,
                "the three trees the Forge tabs are built from");
        for (String tree : trees) {
            boolean movable = false;
            for (UpgradeDef def : content.upgrades()) {
                if (def.tree().equals(tree) && !def.effectsPerLevel().isEmpty()) {
                    movable = true;
                    break;
                }
            }
            assertTrue(movable, tree + " must have at least one node with a stat effect");
        }
    }

    private double statOf(StatId stat) {
        return RunLoadout.previewStats(profile, content).resolve(stat);
    }

    /**
     * The sign a modifier is meant to push a stat in.
     *
     * @param op the operation
     * @param value the authored value
     * @return {@code 1} to raise it, {@code -1} to lower it, {@code 0} when the value is a no-op
     */
    private static double directionOf(StatOp op, double value) {
        if (op == StatOp.MULTIPLY) {
            return value > 1 ? 1 : (value < 1 ? -1 : 0);
        }
        return value > 0 ? 1 : (value < 0 ? -1 : 0);
    }

    private static boolean atBound(StatId stat, double value) {
        return Math.abs(value - stat.min()) <= EPSILON || Math.abs(value - stat.max()) <= EPSILON;
    }

    private void openTree(String treeId) {
        profile.unlock(UpgradeManager.treeUnlockId(treeId));
    }
}
