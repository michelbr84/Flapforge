package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes {@code profile.selected} — the bird, its palette, the world and the tier the next run is
 * played with (D15's selection write trigger, D17's BirdSelection flow).
 *
 * <p>A selection is only ever set to something the player owns and the build can play: the id has
 * to be in the registry, in {@code profile.unlocked}, and — for a world — playable in this
 * milestone (E19 keeps every world but Green Fields locked to free play until M7). Changing the
 * bird also repairs the palette, because a palette belongs to one bird and the previous one
 * usually has no counterpart in the new bird's list.
 *
 * <p>Every accepted change marks the profile dirty and asks the {@link SaveTrigger} to write it
 * immediately: a player who picks a bird and quits from the menu must find that bird selected the
 * next time. A rejected change writes nothing at all.
 */
public final class SelectionManager {

    private final ProgressionManager progression;
    private final SaveTrigger save;

    /**
     * Creates a manager.
     *
     * @param progression the write path that owns the dirty flag
     * @param save the write trigger, or {@code null} for {@link SaveTrigger#NONE}
     */
    public SelectionManager(ProgressionManager progression, SaveTrigger save) {
        this.progression = Objects.requireNonNull(progression, "progression");
        this.save = save == null ? SaveTrigger.NONE : save;
    }

    /**
     * Selects a bird, repairing the palette when the current one does not belong to it.
     *
     * @param profile the profile to write
     * @param birdId the bird id
     * @param content the loaded content
     * @return {@code true} when the selection now names that bird
     */
    public boolean selectBird(PlayerProfile profile, String birdId, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        if (birdId == null || !content.birds().contains(birdId)
                || !profile.isUnlocked(ContentKind.BIRD.unlockableId(birdId))) {
            return false;
        }
        if (birdId.equals(profile.selected.birdId)) {
            return true;
        }
        profile.selected.birdId = birdId;
        BirdDef bird = content.birds().get(birdId);
        if (!ownsPalette(profile, bird, profile.selected.paletteId)) {
            profile.selected.paletteId = defaultPaletteOf(bird);
        }
        commit(profile);
        return true;
    }

    /**
     * Selects a palette of the selected bird.
     *
     * @param profile the profile to write
     * @param paletteId the palette id
     * @param content the loaded content
     * @return {@code true} when the selection now names that palette
     */
    public boolean selectPalette(PlayerProfile profile, String paletteId, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        String birdId = profile.selected.birdId;
        if (paletteId == null || !content.birds().contains(birdId)) {
            return false;
        }
        BirdDef bird = content.birds().get(birdId);
        if (!ownsPalette(profile, bird, paletteId)) {
            return false;
        }
        if (paletteId.equals(profile.selected.paletteId)) {
            return true;
        }
        profile.selected.paletteId = paletteId;
        commit(profile);
        return true;
    }

    /**
     * Selects a world.
     *
     * @param profile the profile to write
     * @param worldId the world id
     * @param content the loaded content
     * @return {@code true} when the selection now names that world; {@code false} for a world the
     *     player does not own or that this build cannot play yet (E19)
     */
    public boolean selectWorld(PlayerProfile profile, String worldId, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        if (worldId == null || !content.worlds().contains(worldId)
                || !content.playable(ContentKind.WORLD, worldId)
                || !profile.isUnlocked(ContentKind.WORLD.unlockableId(worldId))) {
            return false;
        }
        if (worldId.equals(profile.selected.worldId)) {
            return true;
        }
        profile.selected.worldId = worldId;
        commit(profile);
        return true;
    }

    /**
     * Selects a difficulty tier.
     *
     * @param profile the profile to write
     * @param tierId the tier id
     * @param content the loaded content
     * @return {@code true} when the selection now names that tier
     */
    public boolean selectTier(PlayerProfile profile, String tierId, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        if (tierId == null || !content.tiers().contains(tierId)
                || !profile.isUnlocked(ContentKind.TIER.unlockableId(tierId))) {
            return false;
        }
        if (tierId.equals(profile.selected.tierId)) {
            return true;
        }
        profile.selected.tierId = tierId;
        commit(profile);
        return true;
    }

    /**
     * Equips the active ability of the next run (D9), or clears the slot.
     *
     * <p>An ability has to be unlocked and has to be an {@code ACTIVE} one: a passive selected
     * into the active slot would simply be dropped when the run assembles its loadout
     * ({@code AbilityManager.selectLoadout}), and a slot that silently keeps an id nothing equips
     * is worse than a refusal.
     *
     * @param profile the profile to write
     * @param abilityId the ability id, or {@code null} to equip nothing
     * @param content the loaded content
     * @return {@code true} when the selection now names that ability (or nothing)
     */
    public boolean selectActiveAbility(PlayerProfile profile, String abilityId,
            GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        String id = abilityId == null || abilityId.isBlank() ? null : abilityId;
        if (id != null && !ownsAbility(profile, content, id, AbilityKind.ACTIVE)) {
            return false;
        }
        if (Objects.equals(id, profile.selected.activeAbilityId)) {
            return true;
        }
        profile.selected.activeAbilityId = id;
        commit(profile);
        return true;
    }

    /**
     * Equips the passive abilities of the next run, in slot order (D9, E3).
     *
     * <p>The list is stored dense and deduplicated: how many of it a run actually uses depends on
     * the bird ({@code BirdDef.passiveSlots + profile.passiveSlotBonus}), which the content layer
     * resolves at run start, so the profile keeps what the player chose even when they switch to
     * a bird with fewer slots and back.
     *
     * @param profile the profile to write
     * @param abilityIds the passive ability ids, in slot order; unknown, locked and non-passive
     *     ids are dropped rather than stored
     * @param content the loaded content
     * @return {@code true} when the selection now names those abilities
     */
    public boolean setPassiveAbilities(PlayerProfile profile, List<String> abilityIds,
            GameContent content) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(content, "content");
        List<String> kept = new ArrayList<>(abilityIds == null ? List.of() : abilityIds);
        List<String> clean = new ArrayList<>(kept.size());
        for (String id : kept) {
            if (id != null && !id.isBlank() && !clean.contains(id)
                    && ownsAbility(profile, content, id, AbilityKind.PASSIVE)) {
                clean.add(id);
            }
        }
        if (clean.equals(profile.selected.passiveAbilityIds)) {
            return true;
        }
        profile.selected.passiveAbilityIds = clean;
        commit(profile);
        return true;
    }

    /**
     * Whether the profile owns an ability of a kind and this build can equip it (E19).
     *
     * @param profile the profile
     * @param content the loaded content
     * @param abilityId the ability id
     * @param kind the kind the slot takes
     * @return {@code true} when it may be equipped
     */
    private static boolean ownsAbility(PlayerProfile profile, GameContent content,
            String abilityId, AbilityKind kind) {
        if (!content.has(GameContent.ABILITIES) || !content.playable(ContentKind.ABILITY)
                || !content.abilities().contains(abilityId)) {
            return false;
        }
        AbilityDef def = content.abilities().get(abilityId);
        return def.kind() == kind && profile.isUnlocked(def.unlockableId());
    }

    private void commit(PlayerProfile profile) {
        progression.markChanged();
        save.saveNow();
    }

    private static boolean ownsPalette(PlayerProfile profile, BirdDef bird, String paletteId) {
        return bird.palette(paletteId) != null
                && profile.isUnlocked(bird.cosmeticId(paletteId));
    }

    /**
     * The palette a bird falls back to: the first one it declares (§4 "the first of which is the
     * default").
     *
     * @param bird the bird
     * @return the palette id
     */
    private static String defaultPaletteOf(BirdDef bird) {
        for (PaletteDef palette : bird.palettes()) {
            return palette.id();
        }
        return PlayerProfile.DEFAULT_PALETTE;
    }
}
