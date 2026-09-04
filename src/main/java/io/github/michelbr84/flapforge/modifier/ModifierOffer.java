package io.github.michelbr84.flapforge.modifier;

import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import java.util.ArrayList;
import java.util.List;

/**
 * One draft: the cards {@link ModifierPool} drew, plus everything the overlay has to draw them
 * (D17). It is a value, produced once when the offer opens and never changed, so the UI and the
 * director cannot disagree about what was on the table.
 *
 * <p>An offer with no card is a legal outcome, not an error: when every card is exhausted,
 * excluded or inert under the run's rules (E12) the draft is skipped and the run carries straight
 * on to the resume hold.
 *
 * @param index the position of this draft in {@code offerSchedule}, counting from 0
 * @param gate the schedule entry this draft belongs to — the gate §6 promises and the overlay
 *     prints, not the gate the corridor happened to clear at, which is a few gates later
 * @param cards the drawn cards, in draw order
 */
public record ModifierOffer(int index, int gate, List<Card> cards) {

    /**
     * One card of a draft.
     *
     * @param modifier the card
     * @param stacksOwned how many stacks the run already has of it (0 for a new card)
     * @param lastStack whether taking it reaches {@code maxStacks}
     */
    public record Card(ModifierDef modifier, int stacksOwned, boolean lastStack) {

        /**
         * The modifier id.
         *
         * @return the id
         */
        public String id() {
            return modifier.id();
        }

        /**
         * The rarity, which the card frame is coloured by.
         *
         * @return the rarity
         */
        public Rarity rarity() {
            return modifier.rarity();
        }

        /**
         * Whether the card would add a stack to something the run already has.
         *
         * @return {@code true} when it is a repeat
         */
        public boolean isStack() {
            return stacksOwned > 0;
        }
    }

    /**
     * Copies the card list.
     *
     * @param index the position in the schedule
     * @param gate the schedule entry it belongs to
     * @param cards the drawn cards
     */
    public ModifierOffer {
        cards = List.copyOf(cards);
    }

    /**
     * An offer with nothing to show, which the director skips.
     *
     * @param index the position in the schedule
     * @param gate the schedule entry it belongs to
     * @return the offer
     */
    public static ModifierOffer none(int index, int gate) {
        return new ModifierOffer(index, gate, List.of());
    }

    /**
     * Whether there is nothing to choose from.
     *
     * @return {@code true} when no card was drawn
     */
    public boolean isEmpty() {
        return cards.isEmpty();
    }

    /**
     * How many cards are on the table.
     *
     * @return the count
     */
    public int size() {
        return cards.size();
    }

    /**
     * The card at a position.
     *
     * @param position the index
     * @return the card, or {@code null} when the position is out of range
     */
    public Card cardAt(int position) {
        return position < 0 || position >= cards.size() ? null : cards.get(position);
    }

    /**
     * The ids on the table, in draw order.
     *
     * @return a new list
     */
    public List<String> ids() {
        List<String> out = new ArrayList<>(cards.size());
        for (Card card : cards) {
            out.add(card.id());
        }
        return out;
    }
}
