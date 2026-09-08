package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.ability.AbilityManager;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.spec.BirdProfile;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The three headline numbers the bird selection shows over a bird (M11): how much air one flap
 * buys, how much punishment the bird absorbs and how much margin for error it flies with, each
 * on a 1-10 scale with the balanced bird sitting at 5.
 *
 * <p>They describe <em>the bird</em>: its base stats, its own effects and the passives it grants
 * innately, resolved through the real {@link StatSheet} arithmetic the simulation uses
 * ({@code content.birdProfile} is the same object the run is built from), and nothing else. The
 * upgrades, the world and the tier are deliberately left out — the hero shows the bird the player
 * is browsing, which is often not the one selected, and a number that mixed the two would be
 * comparing different things side by side. What the current build resolves to lives in the
 * screen's stat breakdown instead.
 *
 * <p>Each axis is a ratio against Forgewing, so the balanced bird is exactly {@code 5/5/5} and
 * every deviation is legible:
 *
 * <pre>
 * mobility = 5 + 25   * ((flap² / 2·gravity) · √(1800/gravity) / 45.5625 - 1)
 * defence  = 5 + 2.5  * (1 + 2·shields + 3·revives + 4·(1 - hitbox) + (1500 - maxFall)/1500 - 1)
 * control  = 5 + 12   * ((1800/gravity) · (gap/128) / hitbox · √(dur/cooldown)
 *                        · (1 + 0.1·(slots - 2)) - 1)
 * </pre>
 *
 * @param mobility the mobility score in {@code [1, 10]}
 * @param defence the defence score in {@code [1, 10]}
 * @param control the control score in {@code [1, 10]}
 */
public record BirdAttributes(int mobility, int defence, int control) {

    /** Lowest score an axis can show. */
    public static final int MIN = 1;
    /** Highest score an axis can show. */
    public static final int MAX = 10;
    /** Apex height of Forgewing's flap, the mobility reference. */
    public static final double CLASSIC_APEX = 45.5625;
    /** Gravity of Forgewing, the reference every ratio is taken against. */
    public static final double CLASSIC_GRAVITY = 1800.0;
    /** Gap size of the classic curve, the control reference. */
    public static final double CLASSIC_GAP = 128.0;
    /** Maximum fall speed of Forgewing, the defence reference. */
    public static final double CLASSIC_MAX_FALL = 1500.0;

    /** How far one unit of the mobility ratio moves the score. */
    private static final double MOBILITY_K = 25;
    /** How far one unit of the defence ratio moves the score. */
    private static final double DEFENCE_K = 2.5;
    /** How far one unit of the control ratio moves the score. */
    private static final double CONTROL_K = 12;
    /** The score a ratio of exactly one lands on. */
    private static final int MIDDLE = 5;

    /**
     * Scores one bird.
     *
     * @param bird the bird
     * @param content the loaded content the bird's profile and innate abilities come from
     * @return the three scores
     */
    public static BirdAttributes of(BirdDef bird, GameContent content) {
        Objects.requireNonNull(bird, "bird");
        Objects.requireNonNull(content, "content");
        BirdProfile profile = content.birdProfile(bird.id());
        EffectStack stack = new EffectStack();
        stack.setLayer(Layer.BIRD, profile.effects());
        stack.setLayer(Layer.ABILITY, innateEffects(bird, content));
        StatSheet sheet = new StatSheet(profile.baseStats(), stack, RuleSet.EMPTY);

        double gravity = sheet.resolve(StatId.GRAVITY);
        double flap = sheet.resolve(StatId.FLAP_VELOCITY);
        double maxFall = sheet.resolve(StatId.MAX_FALL_SPEED);
        double hitbox = sheet.resolve(StatId.HITBOX_SCALE);
        double gap = sheet.resolve(StatId.GAP_SIZE);
        double shields = sheet.resolve(StatId.SHIELD_CHARGES);
        double revives = sheet.resolve(StatId.REVIVES);
        double duration = sheet.resolve(StatId.ABILITY_DURATION_MULT);
        double cooldown = sheet.resolve(StatId.ABILITY_COOLDOWN_MULT);
        int slots = bird.passiveSlots();

        double apex = flap * flap / (2 * gravity) * Math.sqrt(CLASSIC_GRAVITY / gravity);
        double defenceRatio = 1 + 2 * shields + 3 * revives + 4 * (1 - hitbox)
                + (CLASSIC_MAX_FALL - maxFall) / CLASSIC_MAX_FALL;
        double controlRatio = CLASSIC_GRAVITY / gravity * (gap / CLASSIC_GAP) / hitbox
                * Math.sqrt(duration / cooldown) * (1 + 0.1 * (slots - 2));
        return new BirdAttributes(score(apex / CLASSIC_APEX, MOBILITY_K),
                score(defenceRatio, DEFENCE_K), score(controlRatio, CONTROL_K));
    }

    /**
     * The run-long effects of the passives a bird grants innately.
     *
     * @param bird the bird
     * @param content the loaded content
     * @return the modifiers, empty when the bird grants nothing
     */
    private static List<StatModifier> innateEffects(BirdDef bird, GameContent content) {
        if (bird.passiveAbilities().isEmpty() || !content.has(GameContent.ABILITIES)) {
            return List.of();
        }
        List<StatModifier> out = new ArrayList<>();
        for (String abilityId : bird.passiveAbilities()) {
            if (!content.abilities().contains(abilityId)) {
                continue;
            }
            AbilityDef def = content.abilities().get(abilityId);
            for (StatModifierDef effect : def.effects()) {
                out.add(effect.toModifier(AbilityManager.SOURCE_PREFIX + abilityId));
            }
        }
        return out;
    }

    /**
     * Turns a ratio against Forgewing into a score.
     *
     * @param ratio the ratio, {@code 1} for the balanced bird
     * @param k how far one unit of it moves the score
     * @return the score in {@code [MIN, MAX]}
     */
    private static int score(double ratio, double k) {
        return MathUtil.clamp((int) Math.round(MIDDLE + k * (ratio - 1)), MIN, MAX);
    }
}
