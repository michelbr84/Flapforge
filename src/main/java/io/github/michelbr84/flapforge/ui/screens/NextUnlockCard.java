package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.ProceduralArt.ButtonState;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

/**
 * The "next unlock" card of the home hub (D17, M10): the nearest measurable unlockable, its
 * counter and a gold track, on a chip plate with an icon of the unlockable's kind. Activating it
 * opens the screen where that kind is earned or bought. When nothing measurable is left the
 * card says so and takes no focus.
 */
public final class NextUnlockCard extends UiNode {

    /** Size of the kind icon. */
    public static final int ICON_SIZE = 14;
    /** Height of the progress track. */
    public static final int TRACK_H = 8;

    private static final int PADDING = 10;
    private static final int LABEL_BASELINE = 19;
    private static final int TRACK_TOP = 28;
    private static final Color TRACK = new Color(0x10, 0x1C, 0x1E, 0xC0);

    private String label = "";
    private String counterText = "";
    private double fraction;
    private ContentKind kind;
    private String shown = "";
    private String shownSource;
    private int shownWidth = -1;
    private double shownScale;

    /**
     * Creates the card.
     *
     * @param onAction the action run on activation, or {@code null}
     */
    NextUnlockCard(Runnable onAction) {
        setOnAction(onAction);
    }

    /**
     * Points the card at an unlockable.
     *
     * @param newLabel the label ("Next unlock: Guardian")
     * @param newCounterText the counter ("2 / 3"), empty for none
     * @param newFraction the progress in {@code [0, 1]}
     * @param newKind the unlockable's kind, or {@code null} when nothing is left
     */
    void bind(String newLabel, String newCounterText, double newFraction, ContentKind newKind) {
        this.label = newLabel == null ? "" : newLabel;
        this.counterText = newCounterText == null ? "" : newCounterText;
        this.fraction = MathUtil.clamp(newFraction, 0, 1);
        this.kind = newKind;
        setFocusable(newKind != null);
    }

    /**
     * The label.
     *
     * @return the text
     */
    public String label() {
        return label;
    }

    /**
     * The counter.
     *
     * @return the text, empty when nothing is left to unlock
     */
    public String counterText() {
        return counterText;
    }

    /**
     * The progress.
     *
     * @return the fraction in {@code [0, 1]}
     */
    public double fraction() {
        return fraction;
    }

    /**
     * The kind of the unlockable shown.
     *
     * @return the kind, or {@code null} when nothing is left
     */
    public ContentKind kind() {
        return kind;
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
        int left = bx + PADDING;
        if (kind != null) {
            drawKindIcon(g, left + ICON_SIZE / 2.0, by + LABEL_BASELINE - 5);
            left += ICON_SIZE + 6;
        }
        g.setFont(Fonts.bold(13));
        int counterWidth = counterText.isEmpty() ? 0 : TextPainter.width(g, counterText) + 8;
        int textWidth = bx + bw - PADDING - counterWidth - left;
        double scale = Fonts.textScale();
        if (shownSource != label || shownWidth != textWidth || shownScale != scale) {
            // Measured only when the label, the width or the text scale changed.
            shown = TextPainter.ellipsise(g, label, textWidth);
            shownSource = label;
            shownWidth = textWidth;
            shownScale = scale;
        }
        Shape unclipped = g.getClip();
        g.clipRect(left, by, Math.max(0, textWidth), bh);
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, shown, left, by + LABEL_BASELINE);
        g.setClip(unclipped);
        if (!counterText.isEmpty()) {
            g.setColor(ProceduralArt.COIN_GOLD);
            TextPainter.drawRight(g, counterText, bx + bw - PADDING, by + LABEL_BASELINE);
        }
        if (kind == null) {
            return;
        }
        int trackX = bx + PADDING;
        int trackW = bw - 2 * PADDING;
        g.setColor(TRACK);
        g.fillRoundRect(trackX, by + TRACK_TOP, trackW, TRACK_H, TRACK_H, TRACK_H);
        int fill = (int) Math.round(trackW * fraction);
        if (fill > 0) {
            g.setColor(ProceduralArt.COIN_GOLD);
            g.fillRoundRect(trackX, by + TRACK_TOP, fill, TRACK_H, TRACK_H, TRACK_H);
        }
    }

    private void drawKindIcon(Graphics2D g, double cx, double cy) {
        Color ink = ProceduralArt.COIN_GOLD;
        switch (kind) {
            case BIRD:
                ProceduralArt.drawBirdSilhouette(g, cx, cy, ICON_SIZE, ink);
                break;
            case WORLD:
                ProceduralArt.drawBanner(g, cx - ICON_SIZE * 0.3, cy - ICON_SIZE / 2.0,
                        ICON_SIZE, 0, ink, ink);
                break;
            case TREE:
                ProceduralArt.drawHammer(g, cx, cy, ICON_SIZE, 0.5, ink, ink);
                break;
            case CHALLENGE:
                ProceduralArt.drawScroll(g, cx, cy, ICON_SIZE, ink, ProceduralArt.TEXT_DARK);
                break;
            default:
                ProceduralArt.drawAwning(g, cx, cy, ICON_SIZE, ink, ProceduralArt.TEXT_DARK);
                break;
        }
    }
}
