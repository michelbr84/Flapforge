package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.defs.GrantType;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

/**
 * The paint of the rebuilt Forge (M13): the colours, glyphs and small shared geometry the tree
 * viewport and the detail panel are drawn with, in one place, the way {@link ShopArt} carries the
 * shop's.
 *
 * <p>Every colour is a constant, every painter is a constant or built once per card, and the two
 * measured helpers ({@link #drawTierPill}, {@link #pips}) are pure. Nothing in the content files
 * names a picture, so the node and stat glyphs are
 * derived from what the node touches: the same stat always wears the same glyph on a card and on
 * its stat row, which is what makes the panel read as "the number this card moves".
 */
final class ForgeArt {

    /** Prerequisite connector, from the reference design. */
    static final Color CONNECTOR = new Color(0xF6, 0xDE, 0x64);
    /** Selection ring of the card the detail panel is about. */
    static final Color SELECTION = new Color(0xF6, 0xEE, 0x7E);
    /** Fill of a tier pill. */
    static final Color TIER_PILL = new Color(0x54, 0xBA, 0x62);
    /** Ink on a tier pill. */
    static final Color TIER_INK = new Color(0x14, 0x35, 0x2B);
    /** Fill of the header coin pill. */
    static final Color COIN_PILL = new Color(0x0E, 0x41, 0x49);
    /** Filled pip of a stat row. */
    static final Color PIP_FILLED = new Color(0xFC, 0xCB, 0x45);
    /** Empty pip of a stat row. */
    static final Color PIP_EMPTY = new Color(0x0D, 0x27, 0x2C);
    /** Divider between the purchase half of the panel and the stat summary. */
    static final Color DIVIDER = new Color(0x1B, 0x62, 0x6B);
    /** The red of the not-enough-coins note, from the reference design. */
    static final Color NO_COINS_RED = new Color(0xD4, 0x57, 0x52);
    /** Fill of the detail panel, from the reference design. */
    static final Color PANEL_FILL = new Color(0x13, 0x42, 0x48);
    /** Ink of the per-tree header subtitle. */
    static final Color SUBTITLE_INK = new Color(0x0B, 0x4A, 0x5E);
    /** Radius of the detail panel's corners (the reference is generous everywhere). */
    static final int PANEL_RADIUS = 24;
    /** Segments of a stat row's pip bar. */
    static final int PIP_COUNT = 5;

    /** Veil over a locked or dimmed card body. */
    static final Color CARD_VEIL = new Color(0x10, 0x1C, 0x1E, 0x9C);
    /** Body colour of the padlock a locked card wears. */
    static final Color PADLOCK = new Color(0xD8, 0xE2, 0xE4);
    /** Width of a prerequisite connector, in logical pixels. */
    static final float CONNECTOR_W = 3f;

    private static final Stroke CONNECTOR_STROKE = new BasicStroke(CONNECTOR_W);
    private static final Stroke PANEL_BORDER_STROKE = new BasicStroke(1f);
    private static final Color PANEL_BORDER = new Color(0xFF, 0xFF, 0xFF, 0x40);
    private static final Color PANEL_SHADOW = new Color(0, 0, 0, 0x40);
    private static final Color PANEL_FILL_HC = new Color(0x14, 0x24, 0x26, 0xFA);
    private static final Color PANEL_BORDER_HC = new Color(0xFF, 0xFF, 0xFF, 0xE0);
    private static final Color GOLD_GLOW = ProceduralArt.COIN_GOLD;
    private static final Color[] HERO_RAMP = ProceduralArt.alphaRamp(GOLD_GLOW, 16);
    private static final Ellipse2D.Double COIN = new Ellipse2D.Double();

    private ForgeArt() {
    }

    // ------------------------------------------------------------------ measures

    /**
     * How many pips of a stat row are filled: the resolved value normalised over the stat's hard
     * clamp range, five segments, rounded (M13 architecture decision — the reference mock's
     * literal pip counts are unreliable, the range is not).
     *
     * @param value the resolved value
     * @param min the lower clamp of the stat
     * @param max the upper clamp of the stat
     * @return the filled count in {@code [0, PIP_COUNT]}; {@code 0} for a zero-width range
     */
    static int pips(double value, double min, double max) {
        double range = max - min;
        if (range <= 0) {
            return 0;
        }
        double t = MathUtil.clamp((value - min) / range, 0.0, 1.0);
        return (int) Math.round(t * PIP_COUNT);
    }

    /**
     * The same measure against a stat's own clamp range.
     *
     * @param value the resolved value
     * @param stat the stat
     * @return the filled count
     */
    static int pips(double value, StatId stat) {
        return pips(value, stat.min(), stat.max());
    }

    // ------------------------------------------------------------------ glyphs

    /**
     * The glyph of one tree tab: a wing for flight, a coin for economy, a hammer for the forge.
     *
     * @param treeId the bare tree id
     * @return the painter
     */
    static IconPainter tabIcon(String treeId) {
        switch (treeId) {
            case "flight":
                return ProceduralArt::drawWing;
            case "economy":
                return ForgeArt::coinGlyph;
            case "forge":
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawHammer(g, cx, cy, size, 0.5, color, ProceduralArt.WOOD);
            default:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawGear(g, cx, cy, size, color, ProceduralArt.TEXT_DARK);
        }
    }

    /**
     * The glyph of an upgrade node: what its first stat effect touches, or what it grants when it
     * has none — the same mapping the stat rows use, so a card and its row match.
     *
     * @param def the node
     * @return the painter
     */
    static IconPainter nodeIcon(UpgradeDef def) {
        if (!def.effectsPerLevel().isEmpty()) {
            return statIcon(def.effectsPerLevel().get(0).stat());
        }
        for (var grant : def.grants()) {
            if (grant.type() == GrantType.PASSIVE_SLOT) {
                return ProceduralArt::drawSlotRing;
            }
            if (grant.type() == GrantType.ABILITY_CAP) {
                return ProceduralArt::drawHourglass;
            }
            if (grant.type() == GrantType.UNLOCK && grant.id().startsWith("tier:")) {
                return ProceduralArt::drawCrown;
            }
        }
        return (g, cx, cy, size, color) ->
                ProceduralArt.drawHammer(g, cx, cy, size, 0.5, color, ProceduralArt.WOOD);
    }

    /**
     * The glyph of a stat, shared by node cards and stat rows.
     *
     * @param stat the stat
     * @return the painter
     */
    static IconPainter statIcon(StatId stat) {
        switch (stat) {
            case GRAVITY:
                return ProceduralArt::drawChevron;
            case FLAP_VELOCITY:
                return ProceduralArt::drawWing;
            case MAX_FALL_SPEED:
            case ABILITY_DURATION_MULT:
            case COIN_SPAWN_RATE:
                return ProceduralArt::drawSpark;
            case HITBOX_SCALE:
                return (g, cx, cy, size, color) ->
                        ProceduralArt.drawGear(g, cx, cy, size, color, ProceduralArt.TEXT_DARK);
            case ABILITY_COOLDOWN_MULT:
                return ProceduralArt::drawHourglass;
            case COIN_MULT:
                return ForgeArt::coinGlyph;
            case XP_MULT:
                return ProceduralArt::drawCrown;
            case MAGNET_RADIUS:
                return ProceduralArt::drawMagnet;
            case SHIELD_CHARGES:
                return ProceduralArt::drawShield;
            case REVIVES:
                return ProceduralArt::drawHeart;
            default:
                return ProceduralArt::drawSpark;
        }
    }

    static void coinGlyph(Graphics2D g, double cx, double cy, double size, Color color) {
        ProceduralArt.drawCoin(g, COIN, cx, cy, size / 2, 1);
    }

    // ------------------------------------------------------------------ shapes

    /**
     * Draws one prerequisite edge: down from the source's bottom centre, across at the midpoint,
     * down into the target's top centre. Two edges into the same target share the last segment,
     * which is the merge junction the reference shows.
     *
     * <p>The caller clips and draws these before the cards, so a long edge passes behind a row
     * in between rather than over it.
     *
     * @param g the context
     * @param x1 the source's centre x
     * @param y1 the source's bottom y
     * @param x2 the target's centre x
     * @param y2 the target's top y
     */
    static void drawConnector(Graphics2D g, double x1, double y1, double x2, double y2) {
        double mid = (y1 + y2) / 2;
        Stroke old = g.getStroke();
        g.setStroke(CONNECTOR_STROKE);
        g.setColor(CONNECTOR);
        g.drawLine((int) Math.round(x1), (int) Math.round(y1),
                (int) Math.round(x1), (int) Math.round(mid));
        g.drawLine((int) Math.round(x1), (int) Math.round(mid),
                (int) Math.round(x2), (int) Math.round(mid));
        g.drawLine((int) Math.round(x2), (int) Math.round(mid),
                (int) Math.round(x2), (int) Math.round(y2));
        g.setStroke(old);
    }

    /**
     * Draws a tier pill: a small fully-rounded green plate above its tier's first card row.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param text the translated label
     */
    static void drawTierPill(Graphics2D g, double x, double y, String text) {
        g.setFont(Fonts.bold(9));
        int w = Math.max(45, TextPainter.width(g, text) + 12);
        int h = 15;
        int px = (int) Math.round(x);
        int py = (int) Math.round(y);
        g.setColor(TIER_PILL);
        g.fillRoundRect(px, py, w, h, h, h);
        g.setColor(TIER_INK);
        TextPainter.draw(g, text, px + w / 2.0,
                TextPainter.centeredBaseline(g, py + h / 2.0), Align.CENTER);
    }

    /**
     * Draws one stat row's pip bar: {@link #PIP_COUNT} rounded segments, filled from the left.
     *
     * @param g the context
     * @param x the left edge of the bar
     * @param cy the vertical centre of the bar
     * @param width the total width of the bar
     * @param filled how many segments are filled
     */
    static void drawPipBar(Graphics2D g, double x, double cy, double width, int filled) {
        int gap = 3;
        int h = 8;
        int segW = (int) ((width - gap * (PIP_COUNT - 1)) / PIP_COUNT);
        int y = (int) Math.round(cy - h / 2.0);
        for (int i = 0; i < PIP_COUNT; i++) {
            g.setColor(i < filled ? PIP_FILLED : PIP_EMPTY);
            g.fillRoundRect((int) Math.round(x + i * (segW + gap)), y, segW, h, 3, 3);
        }
    }

    /**
     * Draws the header coin pill the wallet readout sits in.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    static void drawCoinPill(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(COIN_PILL);
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(PANEL_BORDER);
        g.drawRoundRect(x, y, w - 1, h - 1, h, h);
    }

    /**
     * Draws the detail panel body: a dark rounded card with a soft shadow, in the reference's
     * own fill rather than the shared translucent panel (the stat summary has to stay readable
     * over the pips' dark empty segments).
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    static void drawDetailPanel(Graphics2D g, int x, int y, int w, int h) {
        boolean highContrast = Accessibility.isHighContrast();
        g.setColor(PANEL_SHADOW);
        g.fillRoundRect(x, y + 4, w, h, PANEL_RADIUS, PANEL_RADIUS);
        g.setColor(highContrast ? PANEL_FILL_HC : PANEL_FILL);
        g.fillRoundRect(x, y, w, h, PANEL_RADIUS, PANEL_RADIUS);
        Stroke old = g.getStroke();
        g.setStroke(PANEL_BORDER_STROKE);
        g.setColor(highContrast ? PANEL_BORDER_HC : PANEL_BORDER);
        g.drawRoundRect(x, y, w - 1, h - 1, PANEL_RADIUS, PANEL_RADIUS);
        g.setStroke(old);
    }

    /**
     * Draws the hero icon of the detail panel over its gold radial glow.
     *
     * @param g the context
     * @param cx the centre x
     * @param cy the centre y
     * @param size the icon size
     * @param icon the glyph of the selected node
     */
    static void drawHero(Graphics2D g, double cx, double cy, double size, IconPainter icon) {
        if (icon != null) {
            ProceduralArt.drawGlow(g, cx, cy, size * 0.95, HERO_RAMP, 0.5);
            icon.paint(g, cx, cy, size, ProceduralArt.TEXT_LIGHT);
        }
    }
}
