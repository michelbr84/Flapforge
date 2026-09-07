package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Objects;

/**
 * The player card of the home hub (D17, M10): the selected bird as an avatar, the player name,
 * a crown with the level, the prestige badge and an XP track, on a chip plate. Activating it
 * opens the Profile.
 */
public final class PlayerCard extends UiNode {

    /** Size of the avatar. */
    public static final int AVATAR_SIZE = 30;
    /** Height of the XP track. */
    public static final int TRACK_H = 6;

    private static final int AVATAR_CX = 26;
    private static final int TEXT_X = 52;
    private static final int NAME_BASELINE = 16;
    private static final int LEVEL_BASELINE = 32;
    private static final int TRACK_TOP = 38;
    private static final int CROWN_SIZE = 9;
    private static final int WING_PERIOD_TICKS = 48;
    private static final Color TRACK = new Color(0x10, 0x1C, 0x1E, 0xC0);

    private String name = "";
    private String levelText = "";
    private String prestigeText = "";
    private double xpFraction;
    private GameContent content;
    private PlayerProfile profile;
    private Color accent = ProceduralArt.COIN_GOLD;
    private long ticks;

    /**
     * Creates the card.
     *
     * @param onAction the action run on activation, or {@code null}
     */
    PlayerCard(Runnable onAction) {
        setOnAction(onAction);
    }

    /**
     * Points the card at a profile.
     *
     * @param newName the player name
     * @param newLevelText the level line ("Lv. 4")
     * @param newXpFraction the progress within the level in {@code [0, 1]}
     * @param newPrestigeText the prestige badge, empty for none
     * @param newContent the loaded content the avatar is resolved through, or {@code null}
     * @param newProfile the profile whose selection the avatar shows, or {@code null}
     * @param newAccent the colour of the badge and the track
     */
    void bind(String newName, String newLevelText, double newXpFraction,
            String newPrestigeText, GameContent newContent, PlayerProfile newProfile,
            Color newAccent) {
        this.name = Objects.requireNonNull(newName, "name");
        this.levelText = Objects.requireNonNull(newLevelText, "levelText");
        this.xpFraction = MathUtil.clamp(newXpFraction, 0, 1);
        this.prestigeText = newPrestigeText == null ? "" : newPrestigeText;
        this.content = newContent;
        this.profile = newProfile;
        this.accent = Objects.requireNonNull(newAccent, "accent");
    }

    /**
     * Advances the avatar's wing animation.
     *
     * @param newTicks the screen's tick count
     */
    void setTicks(long newTicks) {
        this.ticks = newTicks;
    }

    /**
     * The player name.
     *
     * @return the text
     */
    public String name() {
        return name;
    }

    /**
     * The level line.
     *
     * @return the text
     */
    public String levelText() {
        return levelText;
    }

    /**
     * The progress within the level.
     *
     * @return the fraction in {@code [0, 1]}
     */
    public double xpFraction() {
        return xpFraction;
    }

    /**
     * The prestige badge.
     *
     * @return the text, empty when the profile never prestiged
     */
    public String prestigeText() {
        return prestigeText;
    }

    /**
     * Current visual state.
     *
     * @return the state derived from the flags
     */
    public ButtonState state() {
        return ButtonState.of(isEnabled(), isFocused(), isHovered());
    }

    @Override
    public void render(Graphics2D g) {
        int bx = (int) Math.round(x());
        int by = (int) Math.round(y());
        int bw = (int) Math.round(width());
        int bh = (int) Math.round(height());
        ProceduralArt.chip(g, bx, by, bw, bh, state());
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        BirdPortrait.draw(g, content, profile, bx + AVATAR_CX, by + bh / 2.0, AVATAR_SIZE,
                phase);
        Shape unclipped = g.getClip();
        g.clipRect(bx + TEXT_X - 2, by, bw - TEXT_X, bh);
        g.setFont(Fonts.bold(13));
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, name, bx + TEXT_X, by + NAME_BASELINE);
        double x = bx + TEXT_X;
        ProceduralArt.drawCrown(g, x + CROWN_SIZE / 2.0, by + LEVEL_BASELINE - 4, CROWN_SIZE,
                ProceduralArt.COIN_GOLD);
        x += CROWN_SIZE + 4;
        g.setFont(Fonts.bold(12));
        TextPainter.draw(g, levelText, x, by + LEVEL_BASELINE);
        if (!prestigeText.isEmpty()) {
            x += TextPainter.width(g, levelText) + 8;
            g.setFont(Fonts.bold(10));
            g.setColor(accent);
            TextPainter.draw(g, prestigeText, x, by + LEVEL_BASELINE);
        }
        g.setClip(unclipped);
        int trackX = bx + TEXT_X;
        int trackW = bw - TEXT_X - 8;
        g.setColor(TRACK);
        g.fillRoundRect(trackX, by + TRACK_TOP, trackW, TRACK_H, TRACK_H, TRACK_H);
        int fill = (int) Math.round(trackW * xpFraction);
        if (fill > 0) {
            g.setColor(ProceduralArt.COIN_GOLD);
            g.fillRoundRect(trackX, by + TRACK_TOP, fill, TRACK_H, TRACK_H, TRACK_H);
        }
    }
}
