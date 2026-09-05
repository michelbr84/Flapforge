package awt;

import awt.geom.Path2D;
import awt.geom.Rectangle2D;

/**
 * android.graphics shim for the M10 build-time source transform (java.awt.* -> awt.*).
 *
 * <p>Stand-in for {@code java.awt.Shape}: the minimal interface the census needs. The game uses
 * {@code Shape} purely as a value type flowing into {@code Graphics2D.fill/draw/clip}
 * (72/30/1 call sites) and through save/restore of {@code getClip/setClip}; every concrete shape
 * in the census is one of the shim geometry classes ({@code awt.geom.*}) or a clip shape handed
 * back by {@link Graphics2D#getClip()}.
 *
 * <p>Beyond the AWT {@code getBounds2D}, the interface exposes the one consumption hook
 * {@link Graphics2D} needs (permitted by the frozen-list design note "whatever the geom classes
 * must expose for Graphics2D to consume them"): appending the shape's outline as double-precision
 * segments into a {@link Path2D} sink, from where the Graphics2D pipeline transforms the geometry
 * in double space and builds the android path.
 */
public interface Shape {

    /**
     * A high-precision bounding box of the shape (AWT parity of {@code getBounds2D}).
     *
     * @return the bounds as a {@code Rectangle2D.Double}
     */
    Rectangle2D getBounds2D();

    /**
     * Shim infrastructure (not part of the game-facing AWT surface): appends this shape's outline
     * to {@code sink} as MOVETO/LINETO/CUBICTO/CLOSE segments in this shape's own user
     * coordinates. Implemented by every concrete shape; {@link Graphics2D} then transforms the
     * segments with the current matrix before any float conversion happens.
     *
     * <p>The sink is not always empty: {@code Path2D.Double.append} hands over the live path,
     * subpath state included. A shape therefore opens its own subpath (a MOVETO) before any other
     * segment and closes only a subpath it opened; a degenerate shape that emits no outline
     * appends nothing at all, so the result is the same as appending through a fresh sink.
     *
     * @param sink the segment sink to append to
     */
    void appendTo(Path2D.Double sink);
}
