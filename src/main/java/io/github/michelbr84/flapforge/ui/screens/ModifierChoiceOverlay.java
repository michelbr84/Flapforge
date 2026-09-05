package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.modifier.ModifierOffer;
import io.github.michelbr84.flapforge.modifier.ModifierTag;
import io.github.michelbr84.flapforge.modifier.Rarity;
import io.github.michelbr84.flapforge.modifier.SynergyResolver;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.Overscan;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Button;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * The mid-run draft (M6, D11, D17, D27): up to three cards on the frozen playfield, one of which
 * the player takes — or none, because skipping is always allowed.
 *
 * <h2>It does not freeze anything</h2>
 * The simulation is stopped by {@code ModifierDirector}, not by this overlay: while the run is in
 * {@code CHOOSING_MODIFIER} or {@code RESUME_HOLD} a {@code Run.tick} moves no bird, spawns no
 * obstacle and counts no tick alive. The overlay still has to <em>drive</em> that tick, because the
 * {@link ScreenManager} only ticks the top of the stack, and the answer to the draft and the 3-2-1
 * countdown both travel through {@code RunInput.choice}. So every tick it forwards one choice —
 * {@link RunInput#NO_CHOICE} while the player is still reading, the card index when one is taken,
 * {@link RunInput#SKIP} when the draft is waved away — and pops itself on the tick the run is
 * handed back to {@code FLYING}. Nothing else of the player's input reaches the run: a flap, an
 * ability press or a pause pressed over the cards is not forwarded, so a mashed Space cannot fly
 * the bird into a pipe it cannot see.
 *
 * <h2>What a card says</h2>
 * Name, rarity (its own colour and its own word), tags, the effect in the words of the string
 * table and the same effect in numbers off {@code effects[]}, the stack it would be
 * ({@code maxStacks} matters to a player deciding between a repeat and something new) and — the
 * point of D27 — the set bonus taking it would <em>complete</em>, computed by asking
 * {@link SynergyResolver#matches} what the build would look like with this card added. Everything
 * is resolved once, when the overlay is built, and the descriptions are wrapped once per text
 * scale.
 *
 * <h2>Focus</h2>
 * The same model as every other screen: one {@link FocusRing} over the cards and the Skip button,
 * arrows and Tab to move, Enter or Space to take, the pointer to hover and click, {@code Esc} to
 * skip. The ring is entered with the transition grace of {@link ScreenManager}, so the flap that
 * was being held when the draft opened cannot take a card by itself.
 */
public final class ModifierChoiceOverlay implements Screen {

    /** Width of one card. */
    public static final int CARD_W = 126;
    /** Height of one card. */
    public static final int CARD_H = 250;
    /** Gap between two cards. */
    public static final int CARD_GAP = 9;
    /** Top edge of the card row. */
    public static final int CARD_TOP = 156;
    /**
     * Baseline of the title. It sits below the HUD's score and streak lines, which stay visible
     * through the dim: the frozen game is the context the choice is made in.
     */
    public static final int TITLE_BASELINE = 124;
    /** Baseline of the subtitle under the title. */
    public static final int SUBTITLE_BASELINE = 144;
    /** Top edge of the Skip button. */
    public static final int SKIP_TOP = CARD_TOP + CARD_H + 18;
    /** Width of the Skip button. */
    public static final int SKIP_W = 168;
    /** Height of the Skip button. */
    public static final int SKIP_H = 38;
    /** Baseline of the key hint under the Skip button. */
    public static final int HINT_BASELINE = SKIP_TOP + SKIP_H + 26;
    /** Point size of the 3-2-1. */
    public static final int COUNTDOWN_SIZE = 96;
    /** Radius of the ring that drains around the 3-2-1. */
    public static final int COUNTDOWN_RING_R = 74;

    private static final Color DIM = new Color(0, 0, 0, 0xA6);
    private static final Color RING_TRACK = new Color(0xF4, 0xF8, 0xF8, 0x30);
    private static final Stroke RING_STROKE = new BasicStroke(4f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final Color SYNERGY = new Color(0xF5C542);
    private static final Color NUMBERS = new Color(0x8FD8C4);
    private static final Color FOCUS_RING = new Color(0xF4, 0xF8, 0xF8, 0xE0);
    private static final Stroke FOCUS_STROKE = new BasicStroke(2f);
    private static final Map<Rarity, Color> RARITY_COLORS = rarityColors();

    private final ScreenManager screens;
    private final Strings strings;
    private final Run run;
    private final IntConsumer draftTick;
    private final FocusRing ring = new FocusRing();
    private final List<Card> cards = new ArrayList<>();
    private final Button skip;
    private final String title;
    private final String subtitle;
    private final String hint;
    private final String resumeLabel;
    private int pending = RunInput.NO_CHOICE;
    private String takenName = "";
    private boolean skipped;

    /**
     * Creates the overlay for the draft that is open on a run.
     *
     * @param screens the screen stack
     * @param strings the string table every label comes from
     * @param run the run whose draft is open; its {@code ModifierDirector} owns the cards
     * @param draftTick forwards one frozen tick to the run, carrying the player's answer — the
     *     game screen's own tick path, so the facts of the tick still reach the bus
     */
    public ModifierChoiceOverlay(ScreenManager screens, Strings strings, Run run,
            IntConsumer draftTick) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.run = Objects.requireNonNull(run, "run");
        this.draftTick = Objects.requireNonNull(draftTick, "draftTick");
        this.title = strings.get(StringKey.DRAFT_TITLE);
        this.hint = strings.get(StringKey.DRAFT_HINT);
        this.resumeLabel = strings.get(StringKey.DRAFT_RESUME);
        this.skip = new Button(strings.get(StringKey.DRAFT_SKIP), this::chooseSkip);
        this.skip.setFontSize(16);
        this.skip.setBounds((Playfield.WIDTH - SKIP_W) / 2.0, SKIP_TOP, SKIP_W, SKIP_H);
        ModifierDirector director = run.simulation().modifiers();
        ModifierOffer offer = director.offer();
        ModifierCatalog catalog = run.setup().modifiers();
        this.subtitle = offer == null ? "" : strings.format(StringKey.DRAFT_SUBTITLE,
                offer.index() + 1, catalog.offerSchedule().size(), offer.gate());
        build(offer, director, catalog);
        for (Card card : cards) {
            ring.add(card);
        }
        ring.add(skip);
    }

    /**
     * Lays the drawn cards out in a centred row and turns each into a focusable node.
     *
     * @param offer the open draft, or {@code null} when there is none (a defensive case: the
     *     overlay is only pushed on {@code CHOOSING_MODIFIER})
     * @param director the run's draft director, for the build a synergy is measured against
     * @param catalog the run's roguelite content
     */
    private void build(ModifierOffer offer, ModifierDirector director, ModifierCatalog catalog) {
        if (offer == null || offer.isEmpty()) {
            return;
        }
        List<ModifierDef> entries = takenEntries(director, catalog);
        int count = offer.size();
        double rowWidth = count * CARD_W + (count - 1) * (double) CARD_GAP;
        double left = (Playfield.WIDTH - rowWidth) / 2.0;
        for (int i = 0; i < count; i++) {
            ModifierOffer.Card card = offer.cardAt(i);
            Card node = new Card(i, card, strings, synergyName(card, entries, director, catalog));
            node.setBounds(left + i * (CARD_W + (double) CARD_GAP), CARD_TOP, CARD_W, CARD_H);
            final int index = i;
            node.setOnAction(() -> choose(index));
            cards.add(node);
        }
    }

    /**
     * The modifiers the run holds, as definitions.
     *
     * @param director the draft director
     * @param catalog the run's catalogue
     * @return one entry per distinct modifier taken, in take order
     */
    private static List<ModifierDef> takenEntries(ModifierDirector director,
            ModifierCatalog catalog) {
        List<String> ids = director.taken();
        List<ModifierDef> out = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            ModifierDef def = catalog.get(ids.get(i));
            if (def != null) {
                out.add(def);
            }
        }
        return out;
    }

    /**
     * The set bonus taking one card would complete (D27, E16).
     *
     * <p>Asked of {@link SynergyResolver} rather than re-derived: the rule that a synergy needs two
     * distinct entries lives in one place, and a card that only <em>looks</em> like it completes
     * one — a single card carrying both required tags — correctly promises nothing.
     *
     * @param card the card
     * @param entries the modifiers the run already holds
     * @param director the director, for the synergies that are already active
     * @param catalog the run's catalogue
     * @return the translated synergy name, or {@code null} when the card completes none
     */
    private String synergyName(ModifierOffer.Card card, List<ModifierDef> entries,
            ModifierDirector director, ModifierCatalog catalog) {
        List<ModifierDef> withCard = new ArrayList<>(entries);
        if (!withCard.contains(card.modifier())) {
            withCard.add(card.modifier());
        }
        List<String> active = director.activeSynergies();
        for (SynergyDef def : catalog.synergies()) {
            if (!active.contains(def.id()) && SynergyResolver.matches(def, withCard)) {
                return ProgressionText.name(strings, ContentKind.SYNERGY, def.id());
            }
        }
        return null;
    }

    private void choose(int index) {
        pending = index;
        Card card = index >= 0 && index < cards.size() ? cards.get(index) : null;
        takenName = card == null ? "" : card.name();
        skipped = false;
    }

    private void chooseSkip() {
        pending = RunInput.SKIP;
        takenName = "";
        skipped = true;
    }

    @Override
    public boolean isOverlay() {
        return true;
    }

    /**
     * A draft is a live run held still, not a finished one: the 60-second autosave must not land
     * in the middle of it (D15, and the {@code CHOOSING_MODIFIER} the rule names).
     *
     * @return {@code true} while the draft is open
     */
    @Override
    public boolean blocksAutosave() {
        return true;
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        if (!cards.isEmpty()) {
            ring.focus(cards.get(0));
        }
    }

    /**
     * One frozen tick: the player's answer is read, forwarded to the run and the overlay leaves as
     * soon as the run is flying again.
     *
     * <p>Only the answer is forwarded. Nothing else of {@code input} reaches the simulation, which
     * is what "the overlay never steals a tick" means from the run's side: a draft costs the world
     * no tick, no flap and no obstacle.
     *
     * @param input the input of this tick
     */
    @Override
    public void tick(InputFrame input) {
        if (run.phase() == RunPhase.CHOOSING_MODIFIER) {
            ring.handle(input);
            if (input.isJustPressed(InputAction.BACK)
                    || input.isJustPressed(InputAction.PAUSE)) {
                UiCues.back();
                chooseSkip();
            }
        }
        int choice = pending;
        pending = RunInput.NO_CHOICE;
        draftTick.accept(choice);
        RunPhase phase = run.phase();
        if (phase != RunPhase.CHOOSING_MODIFIER && phase != RunPhase.RESUME_HOLD) {
            screens.pop();
        }
    }

    /**
     * The cards on the table, left to right.
     *
     * @return an unmodifiable view, empty once the draft has been answered
     */
    public List<Card> cards() {
        return List.copyOf(cards);
    }

    /**
     * The Skip button.
     *
     * @return the button
     */
    public Button skipButton() {
        return skip;
    }

    /**
     * The focus ring (tests inspecting focus).
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /**
     * The name of the card that was taken.
     *
     * @return the translated name, empty while the draft is still open and after a skip
     */
    public String takenName() {
        return takenName;
    }

    /**
     * Whether the draft was waved away.
     *
     * @return {@code true} once Skip was pressed
     */
    public boolean isSkipped() {
        return skipped;
    }

    /**
     * The number the countdown shows.
     *
     * @return 3, 2 or 1 during the resume hold, 0 while the cards are up
     */
    public int countdown() {
        return run.simulation().modifiers().countdown();
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        Overscan.fillVisible(g, DIM);
        if (run.phase() == RunPhase.CHOOSING_MODIFIER) {
            renderDraft(g);
        } else {
            renderCountdown(g);
        }
    }

    private void renderDraft(Graphics2D g) {
        g.setFont(Fonts.bold(24));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.drawCentered(g, title, Playfield.WIDTH / 2.0, TITLE_BASELINE);
        if (!subtitle.isEmpty()) {
            g.setFont(Fonts.regular(12));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawCentered(g, subtitle, Playfield.WIDTH / 2.0, SUBTITLE_BASELINE);
        }
        ring.render(g);
        g.setFont(Fonts.regular(12));
        g.setColor(ProceduralArt.TEXT_MUTED);
        TextPainter.drawCentered(g, hint, Playfield.WIDTH / 2.0, HINT_BASELINE);
    }

    /**
     * The resume hold: the 3-2-1 the director counts, over what the draft ended with.
     *
     * @param g the context
     */
    private void renderCountdown(Graphics2D g) {
        int count = countdown();
        renderCountdownRing(g);
        g.setFont(Fonts.bold(COUNTDOWN_SIZE));
        TextPainter.drawOutlined(g, Integer.toString(Math.max(1, count)), Playfield.WIDTH / 2.0,
                Playfield.HEIGHT / 2.0, Align.CENTER, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.TEXT_DARK, 2);
        g.setFont(Fonts.bold(16));
        g.setColor(takenName.isEmpty() ? ProceduralArt.TEXT_MUTED : SYNERGY);
        TextPainter.drawCentered(g, takenName.isEmpty() ? resumeLabel : takenName,
                Playfield.WIDTH / 2.0, Playfield.HEIGHT / 2.0 + 44);
    }

    /**
     * The hold as a shape rather than as three digits.
     *
     * <p>{@code RESUME_HOLD_TICKS / COUNTDOWN_STEPS} is 15 ticks, so each digit is on screen for a
     * quarter of a second — right at the edge of reading as a countdown instead of a flicker. The
     * ring drains continuously over the whole 45 ticks, which is what actually tells the player how
     * long is left; the digit stays because D11 and D17 both call this a 3-2-1.
     *
     * @param g the context
     */
    private void renderCountdownRing(Graphics2D g) {
        int remaining = run.simulation().modifiers().holdTicksRemaining();
        if (remaining <= 0) {
            return;
        }
        double fraction = Math.min(1.0,
                remaining / (double) ModifierDirector.RESUME_HOLD_TICKS);
        int x = (int) (Playfield.WIDTH / 2.0 - COUNTDOWN_RING_R);
        int y = (int) (Playfield.HEIGHT / 2.0 - COUNTDOWN_RING_R - 18);
        int size = COUNTDOWN_RING_R * 2;
        Stroke previous = g.getStroke();
        g.setStroke(RING_STROKE);
        g.setColor(RING_TRACK);
        g.drawOval(x, y, size, size);
        g.setColor(NUMBERS);
        g.drawArc(x, y, size, size, 90, -(int) Math.round(360 * fraction));
        g.setStroke(previous);
    }

    private static Map<Rarity, Color> rarityColors() {
        Map<Rarity, Color> map = new EnumMap<>(Rarity.class);
        map.put(Rarity.COMMON, new Color(0x8F, 0xA3, 0xA5));
        map.put(Rarity.RARE, new Color(0x4F, 0xA8, 0xD8));
        map.put(Rarity.EPIC, new Color(0xB0, 0x7C, 0xE0));
        map.put(Rarity.LEGENDARY, new Color(0xF5, 0xC5, 0x42));
        return map;
    }

    /**
     * The colour a rarity is drawn in.
     *
     * @param rarity the rarity
     * @return the colour
     */
    public static Color colorOf(Rarity rarity) {
        Color color = RARITY_COLORS.get(rarity);
        return color == null ? ProceduralArt.TEXT_MUTED : color;
    }

    /**
     * One card of the draft: a focusable node that draws itself and knows every line it shows, so
     * a test can assert on the words without a graphics context.
     */
    public static final class Card extends UiNode {

        /** Inner padding of a card. */
        public static final int PAD = 8;
        /** Height of the rarity chip. */
        public static final int CHIP_H = 16;
        /** Line height of the wrapped description. */
        public static final int LINE_H = 13;
        /** Most description lines a card shows before it is cut. */
        public static final int MAX_DESC_LINES = 6;
        /** Line height of the footer blocks. */
        public static final int FOOTER_LINE_H = 11;
        /** Most lines the effect-in-numbers block takes. */
        public static final int MAX_NUMBER_LINES = 3;
        /** Most lines the synergy promise takes. */
        public static final int MAX_SYNERGY_LINES = 2;

        private final int index;
        private final ModifierOffer.Card card;
        private final String name;
        private final String rarityLabel;
        private final String tags;
        private final String description;
        private final String numbers;
        private final String stacks;
        private final String synergy;
        private final List<String> descLines = new ArrayList<>();
        private final List<String> numberLines = new ArrayList<>();
        private final List<String> synergyLines = new ArrayList<>();
        private double laidOutScale = -1;

        /**
         * Creates a card.
         *
         * @param index the position in the offer, which is the choice it sends
         * @param card the drawn card
         * @param strings the string table
         * @param synergy the translated name of the set bonus taking it would complete, or
         *     {@code null}
         */
        Card(int index, ModifierOffer.Card card, Strings strings, String synergy) {
            this.index = index;
            this.card = card;
            ModifierDef def = card.modifier();
            this.name = ProgressionText.name(strings, ContentKind.MODIFIER, def.id());
            this.rarityLabel = strings.get(
                    StringKey.valueOf("RARITY_" + def.rarity().name()));
            this.tags = tagLine(strings, def);
            this.description = ProgressionText.description(strings, ContentKind.MODIFIER,
                    def.id());
            this.numbers = numberLine(strings, def);
            this.stacks = Math.max(1, def.maxStacks()) > 1
                    ? strings.format(StringKey.DRAFT_STACKS, card.stacksOwned() + 1,
                            Math.max(1, def.maxStacks()))
                    : "";
            this.synergy = synergy == null ? ""
                    : strings.format(StringKey.DRAFT_SYNERGY, synergy);
        }

        private static String tagLine(Strings strings, ModifierDef def) {
            StringBuilder out = new StringBuilder();
            for (ModifierTag tag : def.tags()) {
                if (out.length() > 0) {
                    out.append(" · ");
                }
                out.append(strings.get(StringKey.valueOf("MODIFIER_TAG_" + tag.name())));
            }
            return out.toString();
        }

        /**
         * The card's effect as numbers: every {@code effects[]} entry in the shape its operation
         * implies, or — for a card whose whole effect is the coin formula's streak term — that
         * term. A card with neither shows nothing rather than an empty line.
         *
         * @param strings the string table
         * @param def the modifier
         * @return the line, possibly empty
         */
        private static String numberLine(Strings strings, ModifierDef def) {
            String effects = ProgressionText.effects(strings, def.effects());
            if (!effects.isEmpty()) {
                return effects;
            }
            return def.streakBonusCoins() > 0
                    ? strings.format(StringKey.HUD_STREAK_BONUS, def.streakBonusCoins()) : "";
        }

        /**
         * The position of this card in the offer, which is the {@code RunInput.choice} taking it
         * sends.
         *
         * @return the index
         */
        public int index() {
            return index;
        }

        /**
         * The modifier id.
         *
         * @return the id
         */
        public String id() {
            return card.id();
        }

        /**
         * The rarity.
         *
         * @return the rarity
         */
        public Rarity rarity() {
            return card.rarity();
        }

        /**
         * The translated name.
         *
         * @return the name
         */
        public String name() {
            return name;
        }

        /**
         * The translated rarity word.
         *
         * @return the label
         */
        public String rarityLabel() {
            return rarityLabel;
        }

        /**
         * The translated tags, joined.
         *
         * @return the line, empty when the card carries no tag
         */
        public String tags() {
            return tags;
        }

        /**
         * The effect in words.
         *
         * @return the translated description
         */
        public String description() {
            return description;
        }

        /**
         * The effect in numbers.
         *
         * @return the line, empty when the card has no stat effect and no streak bonus
         */
        public String numbers() {
            return numbers;
        }

        /**
         * The stack this card would be.
         *
         * @return the line, empty for a card that cannot stack
         */
        public String stacks() {
            return stacks;
        }

        /**
         * The set bonus taking this card would complete.
         *
         * @return the line, empty when it completes none
         */
        public String synergy() {
            return synergy;
        }

        /**
         * Every line the card shows, top to bottom, for a screenshot assertion.
         *
         * @return the non-empty lines
         */
        public List<String> lines() {
            List<String> out = new ArrayList<>(6);
            out.add(rarityLabel);
            out.add(name);
            if (!tags.isEmpty()) {
                out.add(tags);
            }
            out.add(description);
            if (!numbers.isEmpty()) {
                out.add(numbers);
            }
            if (!stacks.isEmpty()) {
                out.add(stacks);
            }
            if (!synergy.isEmpty()) {
                out.add(synergy);
            }
            return List.copyOf(out);
        }

        @Override
        public void render(Graphics2D g) {
            int cx = (int) Math.round(x());
            int cy = (int) Math.round(y());
            int cw = (int) Math.round(width());
            int ch = (int) Math.round(height());
            ProceduralArt.panel(g, cx, cy, cw, ch);

            Color rarity = colorOf(rarity());
            g.setColor(rarity);
            g.fillRoundRect(cx + PAD, cy + PAD, cw - 2 * PAD, CHIP_H, 6, 6);
            g.setFont(Fonts.bold(10));
            g.setColor(ProceduralArt.TEXT_DARK);
            TextPainter.drawCentered(g, rarityLabel, centerX(), cy + PAD + CHIP_H - 5.0);

            double baseline = cy + PAD + CHIP_H + 20.0;
            g.setFont(Fonts.bold(13));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            TextPainter.drawCentered(g, name, centerX(), baseline);
            baseline += 15;
            if (!tags.isEmpty()) {
                g.setFont(Fonts.regular(9));
                g.setColor(rarity);
                TextPainter.drawCentered(g, tags, centerX(), baseline);
                baseline += 14;
            }

            wrapIfNeeded(g, cw - 2.0 * PAD);
            g.setFont(Fonts.regular(11));
            g.setColor(ProceduralArt.TEXT_LIGHT);
            for (int i = 0; i < descLines.size(); i++) {
                TextPainter.drawCentered(g, descLines.get(i), centerX(), baseline);
                baseline += LINE_H;
            }

            // The three footer blocks are drawn upwards from the bottom edge, so a card with a
            // long effect line and a synergy to promise grows into the space its description left
            // instead of running off the panel.
            double bottom = cy + ch - PAD;
            g.setFont(Fonts.bold(10));
            g.setColor(SYNERGY);
            for (int i = synergyLines.size() - 1; i >= 0; i--) {
                TextPainter.drawCentered(g, synergyLines.get(i), centerX(), bottom);
                bottom -= FOOTER_LINE_H;
            }
            if (!stacks.isEmpty()) {
                g.setFont(Fonts.regular(10));
                g.setColor(ProceduralArt.TEXT_MUTED);
                TextPainter.drawCentered(g, stacks, centerX(), bottom);
                bottom -= FOOTER_LINE_H;
            }
            g.setFont(Fonts.bold(9));
            g.setColor(NUMBERS);
            for (int i = numberLines.size() - 1; i >= 0; i--) {
                TextPainter.drawCentered(g, numberLines.get(i), centerX(), bottom);
                bottom -= FOOTER_LINE_H;
            }

            if (isFocused() || isHovered()) {
                Stroke old = g.getStroke();
                g.setStroke(FOCUS_STROKE);
                g.setColor(isFocused() ? FOCUS_RING : rarity);
                g.drawRoundRect(cx - 2, cy - 2, cw + 3, ch + 3, ProceduralArt.PANEL_RADIUS,
                        ProceduralArt.PANEL_RADIUS);
                g.setStroke(old);
            }
        }

        /**
         * Wraps the three variable-length blocks to the card's width, once per text scale.
         *
         * <p>Each block is measured in the font it is drawn in, which is why the wrapping happens
         * here rather than in the constructor: a card is built before there is a graphics context,
         * and the text scale can change while the game is running (D25).
         *
         * @param g the context whose metrics measure the words
         * @param limit the usable width in logical pixels
         */
        private void wrapIfNeeded(Graphics2D g, double limit) {
            if (laidOutScale == Fonts.textScale() && !descLines.isEmpty()) {
                return;
            }
            laidOutScale = Fonts.textScale();
            g.setFont(Fonts.regular(11));
            wrap(g, description, limit, MAX_DESC_LINES, descLines);
            g.setFont(Fonts.bold(9));
            wrap(g, numbers, limit, MAX_NUMBER_LINES, numberLines);
            g.setFont(Fonts.bold(10));
            wrap(g, synergy, limit, MAX_SYNERGY_LINES, synergyLines);
        }

        /**
         * Breaks one block into lines that fit, dropping what does not (the whole point of a card
         * is that it fits on the card).
         *
         * @param g the context, already carrying the block's font
         * @param text the text, may be empty
         * @param limit the usable width
         * @param maxLines the most lines this block may take
         * @param out where the lines go; cleared first
         */
        private static void wrap(Graphics2D g, String text, double limit, int maxLines,
                List<String> out) {
            out.clear();
            if (text.isEmpty()) {
                return;
            }
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (line.length() == 0) {
                    line.append(word);
                } else if (TextPainter.width(g, line + " " + word) <= limit) {
                    line.append(' ').append(word);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
            while (out.size() > maxLines) {
                out.remove(out.size() - 1);
            }
        }
    }
}
