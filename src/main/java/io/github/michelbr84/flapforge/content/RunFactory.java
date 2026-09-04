package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.ability.AbilityManager;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.spec.BossSpec;
import io.github.michelbr84.flapforge.gameplay.spec.ChallengeSpec;
import io.github.michelbr84.flapforge.gameplay.spec.PatternSpec;
import io.github.michelbr84.flapforge.gameplay.spec.WorldSpec;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds runs from content (D10, D11): it resolves the ids in a {@link RunConfig} against the
 * registries and hands {@link Run} the {@link RunSetup} it needs. Everything that plays the game
 * — the game screen, the headless hash, the bot harness, the balancing tool — goes through here,
 * so they all read the same data.
 *
 * <p>From M7 the world comes from {@code worlds.json} whole: curve, effects, flags, spawn
 * weights, patterns, ambience and rule cycles ({@link GameContent#worldSpec}). A content set
 * that ships no {@code worlds.json} — the frozen golden fixture, an M1-shaped test set — still
 * builds runs: Green Fields on the classic curve with a gate-only table, any other id on the
 * standard curve, which is the M1 shape those fixtures were recorded against (E19).
 *
 * <p>From M8 a configuration that names a challenge is a challenge run, in two halves. The
 * configuration half — world, tier, flags, forced modifiers, {@code allowOffers}, the mode —
 * is stamped by {@link #challengeConfig} on top of whatever the profile selected, and the setup
 * half — the curve override, the {@code CHALLENGE} effects, the objective, the forced pattern
 * and the challenge's own boss — is resolved by {@link #setup}. The boss follows E26: a
 * challenge with a {@code boss} block fights that boss and never its world's; a challenge
 * without one has no boss at all, whatever its world says. Neither half asks whether the world
 * is unlocked (E6).
 *
 * <p>The boss of an ordinary run is its world's, unless the configuration pins it off
 * ({@link RunConfig#bossEnabled()}), which only the classic headless configuration does.
 */
public final class RunFactory {

    /** Curve of Green Fields when no {@code worlds.json} is supplied: pure upstream (D20). */
    public static final String GREEN_FIELDS_CURVE = "classic";
    /** Curve of every other world when no {@code worlds.json} is supplied (D20). */
    public static final String DEFAULT_CURVE = "standard";
    /** Gates are the only obstacle family the world-less fallback spawns (D6). */
    public static final int PIPE_GATE_WEIGHT = 100;

    private final GameContent content;

    /**
     * Creates a factory over a content set.
     *
     * @param content the content
     */
    public RunFactory(GameContent content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * The content this factory reads.
     *
     * @return the content
     */
    public GameContent content() {
        return content;
    }

    /**
     * Resolves the ids of a configuration into simulation records.
     *
     * @param config the configuration
     * @return the setup
     * @throws UnknownIdException when the bird, world, tier, curve, challenge or pattern id is
     *     not in the registries
     */
    public RunSetup setup(RunConfig config) {
        Objects.requireNonNull(config, "config");
        WorldSpec world = worldSpec(config.worldId());
        PatternSpec forcedPattern = null;
        ChallengeSpec challenge = null;
        BossSpec boss = null;
        ChallengeDef def = challengeOf(config);
        if (def != null) {
            world = world.withCurve(content.curveSpec(def.curve()));
            challenge = ContentAdapters.toSpec(def);
            if (def.forcedPattern() != null) {
                forcedPattern = content.patternSpec(def.forcedPattern());
            }
            // E26: the challenge's own block or nothing — never the world's.
            if (def.boss() != null) {
                boss = ContentAdapters.toSpec(def.boss(), def.id(), null, content);
            }
        } else {
            boss = worldBoss(config.worldId());
        }
        if (!config.bossEnabled()) {
            boss = null;
        }
        return new RunSetup(content.birdProfile(config.birdId()), world,
                content.tierSpec(config.tierId()), content.speedRampPerTick(),
                content.economy().rewards().streak().step(), loadout(config),
                content.modifierCatalog(draftableModifiers(config)), forcedPattern, boss,
                challenge);
    }

    /**
     * The challenge a configuration names, when the content ships challenges.
     *
     * @param config the configuration
     * @return the definition, or {@code null} for a run without a challenge
     * @throws UnknownIdException when the content ships challenges and none carries the id
     */
    private ChallengeDef challengeOf(RunConfig config) {
        String id = config.challengeId();
        if (id == null || id.isBlank() || !content.has(GameContent.CHALLENGES)) {
            return null;
        }
        return content.challenges().get(id);
    }

    /**
     * The boss of a world (M8), resolved from {@code worlds.json}.
     *
     * @param worldId the world id
     * @return the spec, or {@code null} when the content ships no worlds or the world has no boss
     */
    public BossSpec worldBoss(String worldId) {
        if (!content.has(GameContent.WORLDS) || !content.worlds().contains(worldId)) {
            return null;
        }
        WorldDef def = content.worlds().get(worldId);
        return def.boss() == null ? null
                : ContentAdapters.toSpec(def.boss(), worldId, worldId, content);
    }

    /**
     * Stamps a challenge on a configuration (D11, M8): the mode, the challenge id, the
     * challenge's world and tier, its flags on top of the configuration's rules, its forced
     * modifiers, and offers only when both the configuration and the challenge allow them. The
     * bird, the palette, the loadout, the upgrade layer and the owned modifiers of the base
     * configuration are kept — a challenge is played with what the player selected.
     *
     * <p>The world is not checked against anything: a challenge never requires its world to be
     * unlocked (E6), and the unlock the player needs is the challenge's own, which the screen
     * offering it checks.
     *
     * @param base the configuration to stamp, typically {@code RunLoadout.configFor}
     * @param challengeId the challenge
     * @return the challenge configuration
     * @throws UnknownIdException when no challenge carries the id
     */
    public RunConfig challengeConfig(RunConfig base, String challengeId) {
        Objects.requireNonNull(base, "base");
        ChallengeDef def = content.challenges().get(challengeId);
        return base.toBuilder()
                .mode(RunMode.CHALLENGE)
                .challengeId(def.id())
                .worldId(def.world())
                .tierId(def.tier())
                .rules(base.rules().union(RuleSet.of(def.flags())))
                .forcedModifiers(def.forcedModifiers())
                .allowOffers(base.allowOffers() && def.allowOffers())
                .build();
    }

    /**
     * The world a run plays in: {@code worlds.json} when the content ships it, else the M1
     * fallback shape (see the class comment).
     *
     * @param worldId the world id
     * @return the spec
     * @throws UnknownIdException when the content ships worlds and none carries the id
     */
    public WorldSpec worldSpec(String worldId) {
        if (content.has(GameContent.WORLDS)) {
            return content.worldSpec(worldId);
        }
        return new WorldSpec(worldId, content.curveSpec(curveIdFor(worldId)), List.of(),
                RuleSet.EMPTY, Map.of(ObstacleKind.PIPE_GATE, PIPE_GATE_WEIGHT));
    }

    /**
     * The modifier ids the run's catalogue is built from: what the profile owns, plus whatever the
     * run source forces on it (D11).
     *
     * <p>Forced modifiers are a property of the run — a challenge, the daily — and not of the
     * profile: only the <em>offer</em> pool depends on ownership. Without the union a challenge
     * that forces {@code gold_rush} on a profile that has not unlocked it would resolve to nothing
     * and the run would start without the card that defines it.
     *
     * @param config the configuration
     * @return the ids, owned first, each once
     */
    private static List<String> draftableModifiers(RunConfig config) {
        if (config.forcedModifiers().isEmpty()) {
            return config.availableModifiers();
        }
        Set<String> ids = new LinkedHashSet<>(config.availableModifiers());
        ids.addAll(config.forcedModifiers());
        return List.copyOf(ids);
    }

    /**
     * Resolves the abilities a configuration equips (D9, E3): the selected active, the selected
     * passives up to {@code BirdDef.passiveSlots + RunConfig.passiveSlotBonus}, then the bird's
     * innate passives, which cost no slot and need no unlock.
     *
     * <p>Only the ids are resolved here. What the run's rules forbid is stripped when the
     * simulation starts, where the flags of the config, the world and the tier are already
     * unioned — {@code Run.start()} strips defensively (D9) rather than trusting the screen that
     * assembled the loadout.
     *
     * @param config the configuration
     * @return the definitions, active first; empty when the build ships no abilities
     */
    public List<AbilityDef> loadout(RunConfig config) {
        if (!content.has(GameContent.ABILITIES) || content.abilities().size() == 0) {
            return List.of();
        }
        List<String> innate = List.of();
        int slots = 0;
        if (content.birds().contains(config.birdId())) {
            BirdDef bird = content.birds().get(config.birdId());
            innate = bird.passiveAbilities();
            slots = bird.passiveSlots();
        }
        return AbilityManager.selectLoadout(
                id -> content.abilities().contains(id) ? content.abilities().get(id) : null,
                config.activeAbilityId(), config.passiveAbilityIds(), innate,
                slots + Math.max(0, config.passiveSlotBonus()));
    }

    /**
     * Builds a run.
     *
     * @param config the configuration
     * @return a run in phase {@code READY}
     * @throws UnknownIdException when an id is not in the registries
     */
    public Run newRun(RunConfig config) {
        return new Run(config, setup(config));
    }

    /**
     * Builds a run with the default configuration for a seed.
     *
     * @param seed the run seed
     * @return a run in phase {@code READY}
     */
    public Run newRun(long seed) {
        return newRun(RunConfig.classic(seed));
    }

    /**
     * The curve a world uses: {@code WorldDef.curve} when the content ships worlds, else the M1
     * two-case fallback — Green Fields is the classic curve and anything else is {@code standard}
     * (E19: the frozen golden fixture carries no {@code worlds.json}).
     *
     * @param worldId the world id
     * @return the curve id
     */
    public String curveIdFor(String worldId) {
        if (content.has(GameContent.WORLDS) && content.worlds().contains(worldId)) {
            return content.worlds().get(worldId).curve();
        }
        return RunConfig.DEFAULT_WORLD.equals(worldId) ? GREEN_FIELDS_CURVE : DEFAULT_CURVE;
    }
}
