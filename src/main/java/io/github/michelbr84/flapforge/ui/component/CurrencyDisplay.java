package io.github.michelbr84.flapforge.ui.component;

import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.UiNode;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.Objects;

/**
 * A wallet readout (D17, D13): the procedural coin icon followed by an amount that rolls up to
 * its new value instead of jumping, so a reward the player just earned is visible as movement on
 * the screen they land on.
 *
 * <p>The roll is driven by {@link #tick()} on the simulation clock, never by frame time, so the
 * animation is the same on a 60 Hz and on a 240 Hz display and a screenshot taken at tick
 * {@code n} always shows the same number. While it runs, the icon turns; at rest it is face on,
 * which keeps a still screen still. {@link #setAmountNow(long)} skips the animation, for the
 * initial value a screen is built with.
 *
 * <p>Displays are readouts, not controls, so they are never focusable. The amount is formatted
 * through a template the screen hands in already localised ({@code hud.coins}), like every other
 * component; the string is rebuilt only when the displayed number changes.
 */
public class CurrencyDisplay extends UiNode {

    /** Ticks one roll-up takes. */
    public static final int ROLL_TICKS = 30;
    /** Radius of the coin icon. */
    public static final int ICON_RADIUS = 8;
    /** Gap between the icon and the number. */
    public static final int ICON_GAP = 7;
    /** Point size of the amount. */
    public static final int DEFAULT_FONT_SIZE = 17;

    private final Ellipse2D.Double icon = new Ellipse2D.Double();
    private String format = "";
    private String text = "";
    private long amount;
    private long displayed;
    private long rollFrom;
    private int rollTicks;
    private long animTicks;
    private long textShown = Long.MIN_VALUE;
    private int fontSize = DEFAULT_FONT_SIZE;
    private Align align = Align.LEFT;

    /** Creates an empty wallet readout. */
    public CurrencyDisplay() {
        this(0);
    }

    /**
     * Creates a readout showing an amount straight away.
     *
     * @param amount the amount
     */
    public CurrencyDisplay(long amount) {
        setFocusable(false);
        setAmountNow(amount);
    }

    /**
     * Sets the number format.
     *
     * @param format the template, {@code {0}} standing for the amount; {@code null} shows the
     *     bare number
     */
    public void setFormat(String format) {
        this.format = format == null ? "" : format;
        this.textShown = Long.MIN_VALUE;
    }

    /**
     * The amount the readout is heading for.
     *
     * @return the target amount
     */
    public long amount() {
        return amount;
    }

    /**
     * The amount currently on screen; equal to {@link #amount()} once the roll-up finished.
     *
     * @return the displayed amount
     */
    public long displayedAmount() {
        return displayed;
    }

    /**
     * Whether a roll-up is running.
     *
     * @return {@code true} while the displayed amount is still catching up
     */
    public boolean isRolling() {
        return rollTicks > 0;
    }

    /**
     * Sets a new amount and starts the roll-up from whatever is on screen.
     *
     * @param newAmount the new amount
     */
    public void setAmount(long newAmount) {
        if (newAmount == amount) {
            return;
        }
        rollFrom = displayed;
        amount = newAmount;
        rollTicks = ROLL_TICKS;
    }

    /**
     * Sets the amount without animating it.
     *
     * @param newAmount the new amount
     */
    public final void setAmountNow(long newAmount) {
        amount = newAmount;
        displayed = newAmount;
        rollFrom = newAmount;
        rollTicks = 0;
    }

    /**
     * Changes the size of the number.
     *
     * @param fontSize the point size
     */
    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * Aligns the icon and the number inside the bounds: {@code LEFT} puts the icon at the left
     * edge, {@code RIGHT} ends the number at the right edge, {@code CENTER} centres the pair.
     *
     * @param align the alignment
     */
    public void setAlign(Align align) {
        this.align = Objects.requireNonNull(align, "align");
    }

    /** Advances the roll-up and the icon's spin by one tick. */
    public void tick() {
        if (rollTicks <= 0) {
            return;
        }
        animTicks++;
        rollTicks--;
        if (rollTicks == 0) {
            displayed = amount;
            return;
        }
        double t = 1.0 - rollTicks / (double) ROLL_TICKS;
        double eased = 1 - (1 - t) * (1 - t);
        displayed = rollFrom + Math.round((amount - rollFrom) * eased);
    }

    @Override
    public void render(Graphics2D g) {
        if (displayed != textShown) {
            textShown = displayed;
            String number = Long.toString(displayed);
            text = format.isEmpty() ? number : format.replace("{0}", number);
        }
        g.setFont(Fonts.bold(fontSize));
        double textWidth = TextPainter.width(g, text);
        double total = 2 * ICON_RADIUS + ICON_GAP + textWidth;
        double left;
        switch (align) {
            case RIGHT:
                left = x() + width() - total;
                break;
            case CENTER:
                left = x() + (width() - total) / 2;
                break;
            default:
                left = x();
                break;
        }
        double cy = centerY();
        ProceduralArt.drawCoin(g, icon, left + ICON_RADIUS, cy, ICON_RADIUS,
                rollTicks > 0 ? ProceduralArt.coinSpin(animTicks) : 1);
        g.setColor(ProceduralArt.TEXT_LIGHT);
        TextPainter.draw(g, text, left + 2 * ICON_RADIUS + ICON_GAP,
                TextPainter.centeredBaseline(g, cy));
    }
}
