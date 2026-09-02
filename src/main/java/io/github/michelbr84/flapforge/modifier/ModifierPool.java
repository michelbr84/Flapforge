package io.github.michelbr84.flapforge.modifier;

import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Draws the cards of one draft: {@code choicesPerOffer} modifiers, weighted by rarity, without
 * replacement, from the run's {@code offers} random stream (D12, D27).
 *
 * <p><b>The stream matters.</b> Only {@code offers} is consumed here. Obstacle generation stays on
 * {@code spawn}/{@code obstacle}, which is what makes the sequence of spawn decisions invariant
 * under what the player picks (E32.d) — the draft can draw as much as it likes without moving a
 * single gate.
 *
 * <p><b>Eligibility has two halves</b> (E12). The authored half is what the designer wrote down:
 * {@code maxStacks}, {@code excludes} and {@code requiresFlagsAbsent}. The derived half is
 * computed from the effects, because authoring every combination by hand is exactly the kind of
 * bookkeeping that rots: a card whose whole effect list is a no-op in this run is dropped even
 * though nothing says so in the data. {@code coin_drops} and {@code magnet_burst} disappear under
 * {@code NO_COINS}, {@code temp_shield} under {@code NO_DEFENSIVE_ABILITIES}, {@code second_wind}
 * under {@code NO_REVIVE} — and {@code phoenix}, which also pays coins and is therefore
 * <em>not</em> derivably inert, carries the authored exclusion instead. The run's loadout is read
 * the same way: {@code quick_hands} and {@code long_fuse} scale stats only
 * {@code AbilityInstance} reads, so with nothing equipped that declares a cooldown or a duration
 * they are blanks too ({@link DraftContext}).
 *
 * <p>The context is read on every question, never copied: a flag a taken card turned on has to be
 * visible to the next draft.
 *
 * <p><b>Running out is normal.</b> With fewer eligible cards than {@code choicesPerOffer} the
 * draft shows what is left; with none it is skipped altogether ({@link ModifierOffer#isEmpty()}),
 * and the director takes the run straight to the resume hold rather than freezing on an empty
 * table.
 */
public final class ModifierPool {

    private final ModifierCatalog catalog;
    private final DraftContext context;
    private final Random offers;

    /**
     * Creates a pool over a live run.
     *
     * @param catalog the cards this run may be offered
     * @param context the run being drafted in, which decides the derived eligibility
     * @param offers the run's {@code offers} stream
     */
    public ModifierPool(ModifierCatalog catalog, DraftContext context, Random offers) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.context = Objects.requireNonNull(context, "context");
        this.offers = Objects.requireNonNull(offers, "offers");
    }

    /**
     * Creates a pool over a fixed rule set and a loadout that can use every stat.
     *
     * @param catalog the cards this run may be offered
     * @param rules the active rules
     * @param offers the run's {@code offers} stream
     */
    public ModifierPool(ModifierCatalog catalog, RuleSet rules, Random offers) {
        this(catalog, DraftContext.of(rules), offers);
    }

    /**
     * Every card that could be drawn right now, in content order.
     *
     * @param taken how many stacks of each modifier the run already has
     * @return a new list, possibly empty
     */
    public List<ModifierDef> candidates(Map<String, Integer> taken) {
        List<ModifierDef> out = new ArrayList<>(catalog.modifiers().size());
        for (ModifierDef def : catalog.modifiers()) {
            if (isEligible(def, taken)) {
                out.add(def);
            }
        }
        return out;
    }

    /**
     * Whether one card could be drawn right now.
     *
     * @param def the card
     * @param taken how many stacks of each modifier the run already has
     * @return {@code true} when it may appear in the next draft
     */
    public boolean isEligible(ModifierDef def, Map<String, Integer> taken) {
        if (def == null) {
            return false;
        }
        if (stacksOf(taken, def.id()) >= Math.max(1, def.maxStacks())) {
            return false;
        }
        RuleSet rules = context.rules();
        for (RuleFlag flag : def.requiresFlagsAbsent()) {
            if (rules.contains(flag)) {
                return false;
            }
        }
        if (isInert(def, context)) {
            return false;
        }
        return !excluded(def, taken);
    }

    /**
     * Draws one draft.
     *
     * @param index the position of this draft in the schedule
     * @param gate the schedule entry it belongs to, carried on the offer for the overlay
     * @param taken how many stacks of each modifier the run already has
     * @return the offer, empty when nothing is eligible
     */
    public ModifierOffer draw(int index, int gate, Map<String, Integer> taken) {
        List<ModifierDef> pool = candidates(taken);
        int width = Math.min(catalog.choicesPerOffer(), pool.size());
        if (width <= 0) {
            return ModifierOffer.none(index, gate);
        }
        List<ModifierOffer.Card> cards = new ArrayList<>(width);
        while (cards.size() < width && !pool.isEmpty()) {
            int total = 0;
            for (ModifierDef def : pool) {
                total += catalog.weightOf(def.rarity());
            }
            if (total <= 0) {
                // Every remaining card belongs to a rarity the file gives no weight: drawing it
                // would be an arbitrary choice, so the draft simply shows fewer cards.
                break;
            }
            int roll = offers.nextInt(total);
            ModifierDef picked = null;
            for (int i = 0; i < pool.size(); i++) {
                roll -= catalog.weightOf(pool.get(i).rarity());
                if (roll < 0) {
                    picked = pool.remove(i);
                    break;
                }
            }
            if (picked == null) {
                break;
            }
            int stacks = stacksOf(taken, picked.id());
            cards.add(new ModifierOffer.Card(picked, stacks,
                    stacks + 1 >= Math.max(1, picked.maxStacks())));
            dropExcluded(pool, picked);
        }
        return new ModifierOffer(index, gate, cards);
    }

    /**
     * E12's derived eligibility: whether every effect this card has is a no-op in this run, so
     * showing it would be showing a blank.
     *
     * <p>A card that turns a rule flag on, or that pays a streak bonus the rules still allow, is
     * never inert whatever its effects say — it does something the stat pipeline cannot see.
     *
     * @param def the card
     * @param context the run being drafted in
     * @return {@code true} when the card would do nothing at all
     */
    public static boolean isInert(ModifierDef def, DraftContext context) {
        if (!def.flags().isEmpty()) {
            return false;
        }
        if (def.streakBonusCoins() > 0 && !context.rules().contains(RuleFlag.NO_COINS)) {
            return false;
        }
        for (StatModifierDef effect : def.effects()) {
            if (!isStatInert(effect.stat(), context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * E12's derived eligibility against a fixed rule set, for a loadout that can use every stat.
     *
     * @param def the card
     * @param rules the active rules
     * @return {@code true} when the card would do nothing at all
     */
    public static boolean isInert(ModifierDef def, RuleSet rules) {
        return isInert(def, DraftContext.of(rules));
    }

    /**
     * Whether a stat cannot matter under the active rules (E12).
     *
     * <p>Two sources: the absolute zeroing of the stat pipeline ({@code NO_REVIVE} zeroes
     * {@code REVIVES}, {@code NO_DEFENSIVE_ABILITIES} zeroes {@code SHIELD_CHARGES}, D8), and the
     * coin stats under {@code NO_COINS}, which the errata names explicitly — a run that spawns no
     * coin has no use for a spawn rate, a magnet or a coin multiplier.
     *
     * @param stat the stat
     * @param rules the active rules
     * @return {@code true} when a modifier touching only this stat would do nothing
     */
    public static boolean isStatInert(StatId stat, RuleSet rules) {
        if (rules.zeroes(stat)) {
            return true;
        }
        if (!rules.contains(RuleFlag.NO_COINS)) {
            return false;
        }
        return stat == StatId.COIN_MULT || stat == StatId.COIN_SPAWN_RATE
                || stat == StatId.MAGNET_RADIUS;
    }

    /**
     * Whether a stat cannot matter in this run: the rules zero it (E12), or the loadout has no
     * consumer for it.
     *
     * <p>The second case is the loadout half of the derived rule. {@code ABILITY_COOLDOWN_MULT}
     * and {@code ABILITY_DURATION_MULT} are read by {@code AbilityInstance} alone, so an equipped
     * set that declares neither — the E18 default is {@code double_flap}, whose cooldown and
     * duration are zero at every level — makes a card that scales them a blank. Measured over 200
     * seeds at all four skill presets: forcing {@code quick_hands} or {@code long_fuse} on the
     * default loadout moves the ticks, the gates and the payout by exactly nothing
     * ({@code docs/BALANCING.md} §8.2).
     *
     * @param stat the stat
     * @param context the run being drafted in
     * @return {@code true} when a modifier touching only this stat would do nothing
     */
    public static boolean isStatInert(StatId stat, DraftContext context) {
        if (isStatInert(stat, context.rules())) {
            return true;
        }
        if (stat == StatId.ABILITY_COOLDOWN_MULT) {
            return !context.abilityCooldownMatters();
        }
        if (stat == StatId.ABILITY_DURATION_MULT) {
            return !context.abilityDurationMatters();
        }
        return false;
    }

    /**
     * The catalogue this pool draws from.
     *
     * @return the catalogue
     */
    public ModifierCatalog catalog() {
        return catalog;
    }

    private boolean excluded(ModifierDef def, Map<String, Integer> taken) {
        for (String other : def.excludes()) {
            if (stacksOf(taken, other) > 0) {
                return true;
            }
        }
        for (Map.Entry<String, Integer> entry : taken.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            ModifierDef held = catalog.get(entry.getKey());
            if (held != null && held.excludes().contains(def.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes from the remaining pool anything the card just drawn excludes, so one draft never
     * offers two cards that could not be held together.
     *
     * @param pool the remaining candidates
     * @param picked the card just drawn
     */
    private static void dropExcluded(List<ModifierDef> pool, ModifierDef picked) {
        pool.removeIf(other -> picked.excludes().contains(other.id())
                || other.excludes().contains(picked.id()));
    }

    private static int stacksOf(Map<String, Integer> taken, String id) {
        Integer n = taken.get(id);
        return n == null ? 0 : n;
    }
}
