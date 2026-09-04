package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One entry of {@code birds.json} (D30, §4). Base stats are the only source of a stat's base
 * value (D8); every other number in the pipeline is a modifier in some layer.
 *
 * <p>Display text is deliberately absent (D25): names and descriptions live in
 * {@code data/strings/*.json} under {@code bird.&lt;id&gt;.name} / {@code .desc}.
 *
 * @param id the bird id
 * @param archetype the silhouette and roster group
 * @param passiveSlots how many passive abilities the bird can equip
 * @param baseStats base values; a stat that is absent uses {@link StatId#defaultValue()}
 * @param hitbox the hitbox geometry
 * @param effects innate effects pushed into the {@code BIRD} layer
 * @param rampEffects effects that grow with every passed gate ({@code BIRD_RAMP})
 * @param synergyEffects effects that scale with owned upgrade levels ({@code BIRD_SYNERGY}, M4)
 * @param passiveAbilities innate passive ability ids (M5)
 * @param palettes cosmetic palettes, the first of which is the default
 * @param shape the procedural silhouette key
 * @param sprite optional sprite override, or {@code null}
 * @param unlock how the bird is earned
 */
public record BirdDef(String id, BirdArchetype archetype, int passiveSlots,
        Map<StatId, Double> baseStats, HitboxDef hitbox, List<StatModifierDef> effects,
        List<RampEffectDef> rampEffects, List<SynergyEffectDef> synergyEffects,
        List<String> passiveAbilities, List<PaletteDef> palettes, String shape, SpriteDef sprite,
        UnlockConditionDef unlock) {

    /** Namespace of the unlockable id of a bird. */
    public static final String NAMESPACE = "bird:";

    /** Namespace of the unlockable id of a palette: {@code cosmetic:<bird>:<palette>}. */
    public static final String COSMETIC_NAMESPACE = "cosmetic:";

    /**
     * The silhouette keys {@code ProceduralArt.drawBirdPortrait} knows, in file order.
     *
     * <p>The renderer falls through to the balanced silhouette for anything else, so an unknown
     * key would ship the wrong art in silence. The validator rejects one instead; the list lives
     * here because {@code content} must not depend on {@code render}, and the renderer's switch
     * is the other half of the contract.
     */
    public static final Set<String> SHAPES = Set.of(
            "balanced", "swift", "heavy", "guardian", "gambler", "mystic", "forge");

    /**
     * Copies the collections into deterministic, unmodifiable ones.
     *
     * @param id the bird id
     * @param archetype the archetype
     * @param passiveSlots passive slots
     * @param baseStats base values
     * @param hitbox the hitbox
     * @param effects innate effects
     * @param rampEffects ramp effects
     * @param synergyEffects synergy effects
     * @param passiveAbilities innate passives
     * @param palettes palettes
     * @param shape the silhouette key
     * @param sprite the sprite override or {@code null}
     * @param unlock the unlock condition
     */
    public BirdDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(hitbox, "hitbox");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(unlock, "unlock");
        if (passiveSlots < 0) {
            throw new IllegalArgumentException("passiveSlots must not be negative: " + passiveSlots);
        }
        EnumMap<StatId, Double> stats = new EnumMap<>(StatId.class);
        stats.putAll(baseStats);
        baseStats = Collections.unmodifiableMap(stats);
        effects = List.copyOf(effects);
        rampEffects = List.copyOf(rampEffects);
        synergyEffects = List.copyOf(synergyEffects);
        passiveAbilities = List.copyOf(passiveAbilities);
        palettes = List.copyOf(palettes);
    }

    /**
     * Looks up a palette.
     *
     * @param paletteId the palette id
     * @return the palette, or {@code null} when the bird has no such palette
     */
    public PaletteDef palette(String paletteId) {
        for (PaletteDef p : palettes) {
            if (p.id().equals(paletteId)) {
                return p;
            }
        }
        return null;
    }

    /**
     * The namespaced unlockable id, {@code bird:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }

    /**
     * The namespaced unlockable id of one of this bird's palettes (D13).
     *
     * @param paletteId the palette id
     * @return {@code cosmetic:<bird>:<palette>}
     */
    public String cosmeticId(String paletteId) {
        return COSMETIC_NAMESPACE + id + ':' + paletteId;
    }
}
