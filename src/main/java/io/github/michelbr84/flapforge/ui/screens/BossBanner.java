package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Objects;

/**
 * The telegraph of a boss encounter (D17, M8): a non-blocking banner the game screen draws in the
 * ground strip while the simulation keeps running — the boss counterpart of the
 * {@link RuleShiftBanner}.
 *
 * <p>It is not a {@link io.github.michelbr84.flapforge.ui.Screen}, for the same reason the rule
 * banner is not one: the warning and the fight are exactly the moments the player needs the ticks
 * and the input. The screen calls {@link #announce(TickFact.BossWarning)},
 * {@link #announce(TickFact.BossStarted)} and {@link #announce(TickFact.BossCleared)} on the
 * facts, {@link #tick()} on every live tick and {@link #render(Graphics2D, WorldPalette)} after
 * the HUD; the banner reads nothing and consumes no input.
 *
 * <p>Three phases: {@code WARNING} counts the telegraph down in seconds over the fact's
 * {@code warningTicks}; {@code ACTIVE} shows the survival countdown over the fact's
 * {@code surviveTicks}; {@code CLEARED} flashes "{0} cleared!" for {@value #CLEARED_TICKS} ticks
 * and hides. The boss is named with the owner of the encounter: a world boss carries its world's
 * name, a challenge boss the challenge's (E26) — both resolved through {@link ProgressionText}.
 *
 * <p>The panel sits in the ground strip ({@code y ≥ 598}) on the right, opposite the
 * {@link RuleShiftBanner}, because a rule shift telegraphing when the boss warns is a normal
 * coincidence and the two must never overlap. A line that does not fit is shrunk, exactly like
 * the rule banner's.
 */
public final class BossBanner {

    /** Ticks the "cleared" flash stays up. */
    public static final int CLEARED_TICKS = 108;
    /** Top edge of the banner panel: inside the ground strip, under the playfield. */
    public static final int PANEL_Y = Playfield.GROUND_Y + 2;
    /** Height of the banner panel (it fills the ground strip). */
    public static final int PANEL_H = Playfield.GROUND_HEIGHT - 4;
    /** Right edge of the banner panel; the seed line keeps the left of the strip. */
    public static final int PANEL_RIGHT = Playfield.WIDTH - 14;
    /** Width of the banner panel. */
    public static final int PANEL_W = 232;
    /** Largest font size of the banner line. */
    public static final int LINE_FONT_MAX = 11;
    /** Smallest font size the banner line shrinks to. */
    public static final int LINE_FONT_MIN = 8;
    /** Point size of the title row. */
    public static final int TITLE_FONT = 9;
    /** Horizontal padding between the panel edge and the text. */
    public static final int TEXT_PAD = 8;
    /** Alpha of the panel: the ground stays readable through it. */
    public static final int PANEL_ALPHA = 0x70;
    /** Period of the border pulse while the encounter runs, in ticks. */
    public static final int PULSE_TICKS = 24;

    /** The banner's state. */
    public enum Phase {
        /** Nothing announced. */
        HIDDEN,
        /** The warning countdown runs. */
        WARNING,
        /** The fight runs; the survival countdown ticks. */
        ACTIVE,
        /** The boss was survived; the confirmation flash is up. */
        CLEARED
    }

    private static final Color PANEL_BACK = new Color(0x10, 0x1C, 0x1E, PANEL_ALPHA);
    private static final Color PANEL_FLASH = new Color(0x3A, 0x2E, 0x0C, PANEL_ALPHA);
    private static final Stroke BORDER = new BasicStroke(2f);

    private final Strings strings;
    private Phase phase = Phase.HIDDEN;
    private String bossName = "";
    private String line = "";
    private String title = "";
    private int remaining;
    private int clearedLeft;
    private int shownSeconds = -1;
    private long ticks;
    private int announcements;
    private int lineFont = LINE_FONT_MAX;

    /**
     * Creates a banner.
     *
     * @param strings the string table the words come from
     */
    public BossBanner(Strings strings) {
        this.strings = Objects.requireNonNull(strings, "strings");
        this.title = strings.get(StringKey.BOSS_TITLE);
    }

    /**
     * Shows the banner for a warning the simulation just raised.
     *
     * @param fact the telegraph
     */
    public void announce(TickFact.BossWarning fact) {
        Objects.requireNonNull(fact, "fact");
        bossName = nameOf(fact.bossId(), fact.worldId());
        remaining = Math.max(0, fact.warningTicks());
        shownSeconds = -1;
        phase = Phase.WARNING;
        announcements++;
        refreshLine();
    }

    /**
     * Switches the banner to the fight the simulation just started. The boss keeps the name the
     * warning announced — the started fact names no world (E26: the encounter's owner does).
     *
     * @param fact the fight opener
     */
    public void announce(TickFact.BossStarted fact) {
        Objects.requireNonNull(fact, "fact");
        remaining = Math.max(0, fact.surviveTicks());
        shownSeconds = -1;
        phase = Phase.ACTIVE;
        announcements++;
        refreshLine();
    }

    /**
     * Confirms the boss was survived.
     *
     * @param fact the clear
     */
    public void announce(TickFact.BossCleared fact) {
        Objects.requireNonNull(fact, "fact");
        phase = Phase.CLEARED;
        clearedLeft = CLEARED_TICKS;
        remaining = 0;
        line = strings.format(StringKey.BOSS_CLEARED, bossName);
    }

    /** One live tick: the countdowns run, the clear flash ages out. */
    public void tick() {
        ticks++;
        switch (phase) {
            case WARNING:
            case ACTIVE:
                if (remaining > 0) {
                    remaining--;
                }
                refreshLine();
                break;
            case CLEARED:
                if (--clearedLeft <= 0) {
                    phase = Phase.HIDDEN;
                }
                break;
            case HIDDEN:
            default:
                break;
        }
    }

    /** Hides the banner (a new run). */
    public void reset() {
        phase = Phase.HIDDEN;
        remaining = 0;
        clearedLeft = 0;
        bossName = "";
        line = "";
        shownSeconds = -1;
        announcements = 0;
    }

    /** Re-reads the title and rebuilds the line (a live language switch). */
    public void refreshTexts() {
        title = strings.get(StringKey.BOSS_TITLE);
        // A switch can land mid-second: drop the memo so the countdown line is re-read in the new
        // language even though the second itself has not changed.
        shownSeconds = -1;
        refreshLine();
    }

    private void refreshLine() {
        int seconds = (remaining + Playfield.TICK_RATE - 1) / Playfield.TICK_RATE;
        if (seconds == shownSeconds) {
            return;
        }
        shownSeconds = seconds;
        if (phase == Phase.WARNING) {
            line = strings.format(StringKey.BOSS_WARNING, bossName, seconds);
        } else if (phase == Phase.ACTIVE) {
            line = strings.format(StringKey.BOSS_FIGHT, bossName, seconds);
        }
    }

    /**
     * The owner of the encounter in words: a world boss by its world, a challenge boss by its
     * challenge (E26).
     *
     * @param bossId the encounter's owner id
     * @param worldId the world cleared, or {@code null} for a challenge boss
     * @return the name, or the raw id when the content does not ship the owner
     */
    private String nameOf(String bossId, String worldId) {
        return ProgressionText.name(strings,
                worldId == null ? ContentKind.CHALLENGE : ContentKind.WORLD, bossId);
    }

    /**
     * Whether the banner is drawn.
     *
     * @return {@code true} outside {@code HIDDEN}
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
     * @return the ticks, 0 outside the two countdowns
     */
    public int remaining() {
        return remaining;
    }

    /**
     * The boss in words, as announced.
     *
     * @return the name, empty when nothing was announced
     */
    public String bossName() {
        return bossName;
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
     * How many facts were announced since construction or reset.
     *
     * @return the count
     */
    public int announcements() {
        return announcements;
    }

    /**
     * The font size the line is drawn at after the last layout (tests).
     *
     * @return the size in points
     */
    public int lineFont() {
        return lineFont;
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
        int x = PANEL_RIGHT - PANEL_W;
        int h = PANEL_H;
        double cx = PANEL_RIGHT - PANEL_W / 2.0;
        boolean flash = phase == Phase.CLEARED;
        g.setColor(flash ? PANEL_FLASH : PANEL_BACK);
        g.fillRoundRect(x, PANEL_Y, PANEL_W, h, 10, 10);
        Stroke old = g.getStroke();
        g.setStroke(BORDER);
        // The border pulses through the warning and the fight and holds bright on the clear; no
        // trigonometry and no per-frame colour objects.
        double p = Math.floorMod(ticks, (long) PULSE_TICKS) / (double) PULSE_TICKS;
        double pulse = p < 0.5 ? p * 2 : 2 - p * 2;
        g.setColor(flash || pulse > 0.5 ? ProceduralArt.accentColor(palette)
                : ProceduralArt.TEXT_MUTED);
        g.drawRoundRect(x, PANEL_Y, PANEL_W - 1, h - 1, 10, 10);
        g.setStroke(old);
        int maxW = PANEL_W - 2 * TEXT_PAD;
        lineFont = LINE_FONT_MAX;
        while (lineFont > LINE_FONT_MIN && TextPainter.width(g, Fonts.bold(lineFont), line)
                > maxW) {
            lineFont--;
        }
        g.setFont(Fonts.bold(TITLE_FONT));
        g.setColor(ProceduralArt.accentColor(palette));
        TextPainter.draw(g, title, cx, PANEL_Y + 13.0, Align.CENTER);
        g.setFont(Fonts.bold(lineFont));
        TextPainter.drawOutlined(g, line, cx, PANEL_Y + h - 8.0, Align.CENTER,
                ProceduralArt.TEXT_LIGHT, ProceduralArt.color(palette,
                        ProceduralArt.Tone.LETTERBOX), 2);
    }
}
