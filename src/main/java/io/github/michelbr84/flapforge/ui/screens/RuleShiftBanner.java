package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The telegraph of a Void rule shift (D17, M7): a non-blocking banner the game screen draws in
 * the ground strip while the simulation keeps running.
 *
 * <p>It is not a {@link io.github.michelbr84.flapforge.ui.Screen}: an overlay on the stack would
 * take the ticks and the input away from the run, and a rule shift is exactly the moment the
 * player needs both. The screen calls {@link #announce(TickFact.RuleShift)} on the fact,
 * {@link #tick(boolean)} on every live tick and {@link #render(Graphics2D, WorldPalette)} after
 * the HUD; the banner reads nothing and consumes no input.
 *
 * <p>Three phases: {@code TELEGRAPH} shows the upcoming rule with a countdown in seconds over
 * the fact's {@code telegraphTicks}; when the countdown is over but the simulation still holds
 * the shift (a draft is running, D11) the line says "now"; once the shift has landed the banner
 * flashes "in effect" for {@value #IN_EFFECT_TICKS} ticks and hides. The option is named in
 * words: each flag through {@code rule.<flag>} and each stat effect through the M6 effect text
 * ({@code -15% Gap size}), joined with commas.
 *
 * <p>The panel sits in the ground strip ({@code y ≥ 598}), where the bird never flies and no
 * hazard is ever drawn, at a translucency that leaves the ground readable: a banner over the
 * playfield hid the bird and the column 40 px ahead of it for the whole telegraph (M7 fairness).
 * A line that does not fit is shrunk, then wrapped onto two rows in place of the title.
 */
public final class RuleShiftBanner {

    /** Ticks the "in effect" flash stays up once the shift has landed. */
    public static final int IN_EFFECT_TICKS = 72;
    /** Top edge of the banner panel: inside the ground strip, under the playfield. */
    public static final int PANEL_Y = Playfield.GROUND_Y + 2;
    /** Height of the banner panel (one or two rows alike: it fills the ground strip). */
    public static final int PANEL_H = Playfield.GROUND_HEIGHT - 4;
    /** Left edge of the banner panel. */
    public static final int PANEL_X = 14;
    /** Width of the banner panel; the seed line keeps the right of the strip. */
    public static final int PANEL_W = 292;
    /** Period of the border pulse during the telegraph, in ticks. */
    public static final int PULSE_TICKS = 24;
    /** Height of the banner panel when the rule line wraps onto two rows (the same strip). */
    public static final int PANEL_H_TWO_ROWS = PANEL_H;
    /** Largest font size of the rule line. */
    public static final int LINE_FONT_MAX = 11;
    /** Smallest font size the rule line shrinks to before it wraps. */
    public static final int LINE_FONT_MIN = 8;
    /** Point size of the title row. */
    public static final int TITLE_FONT = 9;
    /** Horizontal padding between the panel edge and the text. */
    public static final int TEXT_PAD = 8;
    /** Alpha of the panel: the ground stays readable through it. */
    public static final int PANEL_ALPHA = 0x70;

    /** The banner's state. */
    public enum Phase {
        /** Nothing announced. */
        HIDDEN,
        /** A shift is announced; the countdown runs. */
        TELEGRAPH,
        /** The shift landed; the confirmation flash is up. */
        IN_EFFECT
    }

    private static final Color PANEL_BACK = new Color(0x10, 0x1C, 0x1E, PANEL_ALPHA);
    private static final Color PANEL_FLASH = new Color(0x3A, 0x2E, 0x0C, PANEL_ALPHA);
    private static final Stroke BORDER = new BasicStroke(2f);

    private final Strings strings;
    private Phase phase = Phase.HIDDEN;
    private String ruleText = "";
    private String line = "";
    private String title = "";
    private int remaining;
    private int telegraphTicks;
    private int inEffectLeft;
    private int shownSeconds = -1;
    private long ticks;
    private int announcements;
    private String laidOut = "";
    private String row1 = "";
    private String row2 = "";
    private int lineFont = LINE_FONT_MAX;
    private int panelHeight = PANEL_H;

    /**
     * Creates a banner.
     *
     * @param strings the string table the names come from
     */
    public RuleShiftBanner(Strings strings) {
        this.strings = Objects.requireNonNull(strings, "strings");
        this.title = strings.get(StringKey.RULE_SHIFT_TITLE);
    }

    /**
     * Shows the banner for a shift the simulation just announced.
     *
     * @param fact the telegraph
     */
    public void announce(TickFact.RuleShift fact) {
        Objects.requireNonNull(fact, "fact");
        announce(fact.flags(), fact.effects(), fact.telegraphTicks());
    }

    /**
     * Shows the banner for an option.
     *
     * @param flags the flags the option turns on
     * @param effects the stat effects it applies
     * @param telegraph flying ticks until it lands
     */
    public void announce(List<RuleFlag> flags, List<StatModifier> effects, int telegraph) {
        ruleText = describe(flags, effects);
        title = strings.get(StringKey.RULE_SHIFT_TITLE);
        telegraphTicks = Math.max(0, telegraph);
        remaining = telegraphTicks;
        phase = Phase.TELEGRAPH;
        inEffectLeft = 0;
        shownSeconds = -1;
        announcements++;
        refreshLine(false);
    }

    /**
     * The option in words: flags first, then effects, comma-separated.
     *
     * @param flags the flags
     * @param effects the effects
     * @return the phrase, or the title alone when the option is empty
     */
    public String describe(List<RuleFlag> flags, List<StatModifier> effects) {
        List<String> parts = new ArrayList<>(flags.size() + effects.size());
        for (RuleFlag flag : flags) {
            parts.add(ProgressionText.ruleName(strings, flag));
        }
        for (StatModifier effect : effects) {
            parts.add(ProgressionText.effect(strings, effect));
        }
        return parts.isEmpty() ? strings.get(StringKey.RULE_SHIFT_TITLE)
                : String.join(", ", parts);
    }

    /** One live tick: the countdown runs; the shift is assumed to land when it reaches zero. */
    public void tick() {
        tick(false);
    }

    /**
     * One live tick of the run.
     *
     * @param simStillPending {@code true} while the simulation has not landed the shift yet
     *     (a draft is deferring it, D11): the countdown holds at "now" instead of confirming
     */
    public void tick(boolean simStillPending) {
        ticks++;
        switch (phase) {
            case TELEGRAPH:
                if (remaining > 0) {
                    remaining--;
                }
                if (remaining == 0 && !simStillPending) {
                    land();
                } else {
                    refreshLine(remaining == 0);
                }
                break;
            case IN_EFFECT:
                if (--inEffectLeft <= 0) {
                    phase = Phase.HIDDEN;
                }
                break;
            case HIDDEN:
            default:
                break;
        }
    }

    /** Confirms the shift landed now (the simulation said so), whatever the countdown says. */
    public void land() {
        if (phase == Phase.HIDDEN) {
            return;
        }
        phase = Phase.IN_EFFECT;
        inEffectLeft = IN_EFFECT_TICKS;
        remaining = 0;
        line = strings.format(StringKey.RULE_SHIFT_IN_EFFECT, ruleText);
    }

    /** Hides the banner (a new run). */
    public void reset() {
        phase = Phase.HIDDEN;
        remaining = 0;
        inEffectLeft = 0;
        ruleText = "";
        line = "";
        laidOut = "";
        shownSeconds = -1;
    }

    /** Re-reads the title (a live language switch); the rule text is rebuilt on the next fact. */
    public void refreshTexts() {
        title = strings.get(StringKey.RULE_SHIFT_TITLE);
    }

    private void refreshLine(boolean holding) {
        if (holding) {
            if (shownSeconds != -2) {
                shownSeconds = -2;
                line = strings.format(StringKey.RULE_SHIFT_NOW, ruleText);
            }
            return;
        }
        int seconds = (remaining + Playfield.TICK_RATE - 1) / Playfield.TICK_RATE;
        if (seconds != shownSeconds) {
            shownSeconds = seconds;
            line = strings.format(StringKey.RULE_SHIFT_COUNTDOWN, ruleText, seconds);
        }
    }

    /**
     * Whether the banner is drawn.
     *
     * @return {@code true} in {@code TELEGRAPH} and {@code IN_EFFECT}
     */
    public boolean isVisible() {
        return phase != Phase.HIDDEN;
    }

    /**
     * The state.
     *
     * @return the phase
     */
    public Phase phase() {
        return phase;
    }

    /**
     * Flying ticks left on the countdown.
     *
     * @return the ticks, 0 when nothing is counting
     */
    public int remaining() {
        return remaining;
    }

    /**
     * The countdown the last announcement started with.
     *
     * @return the ticks
     */
    public int telegraphTicks() {
        return telegraphTicks;
    }

    /**
     * The option in words, as announced.
     *
     * @return the phrase, empty when nothing was announced
     */
    public String ruleText() {
        return ruleText;
    }

    /**
     * The line the banner draws right now.
     *
     * @return the text, empty when hidden
     */
    public String line() {
        return phase == Phase.HIDDEN ? "" : line;
    }

    /**
     * How many shifts were announced since construction.
     *
     * @return the count
     */
    public int announcements() {
        return announcements;
    }

    /**
     * The rows the rule line is drawn on: one, or two when it wraps (tests).
     *
     * @return {@code 1} or {@code 2}, {@code 0} while hidden
     */
    public int rows() {
        return phase == Phase.HIDDEN ? 0 : (row2.isEmpty() ? 1 : 2);
    }

    /**
     * The font size the rule line is drawn at after the last layout (tests).
     *
     * @return the size in points
     */
    public int lineFont() {
        return lineFont;
    }

    /**
     * The height of the panel after the last layout (tests).
     *
     * @return the height
     */
    public int panelHeight() {
        return panelHeight;
    }

    /**
     * Draws the banner when visible.
     *
     * @param g the context in logical coordinates
     * @param palette the world palette (the accent colours the border)
     */
    public void render(Graphics2D g, WorldPalette palette) {
        if (phase == Phase.HIDDEN) {
            return;
        }
        if (!line.equals(laidOut)) {
            layout(g);
        }
        int x = PANEL_X;
        int w = PANEL_W;
        int h = panelHeight;
        double cx = x + w / 2.0;
        boolean flash = phase == Phase.IN_EFFECT;
        g.setColor(flash ? Accessibility.tone(PANEL_FLASH, Accessibility.Role.DANGER)
                : PANEL_BACK);
        g.fillRoundRect(x, PANEL_Y, w, h, 10, 10);
        Stroke old = g.getStroke();
        g.setStroke(BORDER);
        // The border pulses through the telegraph on a triangle wave and holds bright once the
        // rule is in force; no trigonometry and no per-frame colour objects.
        double p = Math.floorMod(ticks, (long) PULSE_TICKS) / (double) PULSE_TICKS;
        double pulse = p < 0.5 ? p * 2 : 2 - p * 2;
        g.setColor(flash || pulse > 0.5 ? ProceduralArt.accentColor(palette)
                : ProceduralArt.TEXT_MUTED);
        g.drawRoundRect(x, PANEL_Y, w - 1, h - 1, 10, 10);
        g.setStroke(old);
        Color outline = ProceduralArt.color(palette, ProceduralArt.Tone.LETTERBOX);
        if (row2.isEmpty()) {
            g.setFont(Fonts.bold(TITLE_FONT));
            g.setColor(ProceduralArt.accentColor(palette));
            TextPainter.draw(g, title, cx, PANEL_Y + 13.0, Align.CENTER);
            g.setFont(Fonts.bold(lineFont));
            TextPainter.drawOutlined(g, row1, cx, PANEL_Y + h - 8.0, Align.CENTER,
                    ProceduralArt.TEXT_LIGHT, outline, 2);
        } else {
            // Two rows take the title's place: the accent border already says what this is.
            g.setFont(Fonts.bold(lineFont));
            TextPainter.drawOutlined(g, row1, cx, PANEL_Y + 15.0, Align.CENTER,
                    ProceduralArt.TEXT_LIGHT, outline, 2);
            TextPainter.drawOutlined(g, row2, cx, PANEL_Y + h - 7.0, Align.CENTER,
                    ProceduralArt.TEXT_LIGHT, outline, 2);
        }
    }

    /**
     * Fits the rule line into the panel (D25: a translation can be half as long again as the
     * English): the largest size from {@value #LINE_FONT_MAX} down to {@value #LINE_FONT_MIN}
     * that fits on one row under the title, else two rows in the title's place, split at the
     * separator nearest the middle (a comma between two rule parts first, a space otherwise) at
     * the largest size that fits both. Measured once per line, not per frame.
     */
    private void layout(Graphics2D g) {
        laidOut = line;
        int maxW = PANEL_W - 2 * TEXT_PAD;
        for (int size = LINE_FONT_MAX; size >= LINE_FONT_MIN; size--) {
            if (TextPainter.width(g, Fonts.bold(size), line) <= maxW) {
                row1 = line;
                row2 = "";
                lineFont = size;
                panelHeight = PANEL_H;
                return;
            }
        }
        int cut = splitPoint(line);
        if (cut <= 0) {
            row1 = line;
            row2 = "";
            lineFont = LINE_FONT_MIN;
            panelHeight = PANEL_H;
            return;
        }
        row1 = line.substring(0, cut).trim();
        row2 = line.substring(cut).trim();
        panelHeight = PANEL_H_TWO_ROWS;
        lineFont = LINE_FONT_MIN;
        for (int size = LINE_FONT_MAX - 1; size >= LINE_FONT_MIN; size--) {
            if (TextPainter.width(g, Fonts.bold(size), row1) <= maxW
                    && TextPainter.width(g, Fonts.bold(size), row2) <= maxW) {
                lineFont = size;
                return;
            }
        }
    }

    /** The index to break a line at: the comma, else the space, nearest the middle. */
    private static int splitPoint(String text) {
        int middle = text.length() / 2;
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ',') {
                int distance = Math.abs(i + 1 - middle);
                if (distance < bestDistance) {
                    best = i + 1;
                    bestDistance = distance;
                }
            }
        }
        if (best > 0) {
            return best;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                int distance = Math.abs(i - middle);
                if (distance < bestDistance) {
                    best = i;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
