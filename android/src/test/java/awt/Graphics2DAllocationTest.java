package awt;

import static org.junit.Assert.assertTrue;

import awt.geom.Ellipse2D;
import awt.geom.Line2D;
import awt.geom.Path2D;
import awt.geom.RoundRectangle2D;
import awt.image.BufferedImage;

import java.lang.reflect.Method;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Allocation micro-measure for the {@link Graphics2D} hot path (the M10 P4 allocation pass).
 * Draws a frame-like batch — 300 shape fills, 100 stroked draws, 50 {@code getFontMetrics} +
 * {@code drawString} pairs, 10 sprite blits and one viewport-style transform/clip bracket — and
 * reports the Java heap bytes the calling thread allocated per batch (HotSpot's
 * {@code com.sun.management.ThreadMXBean}, reached reflectively because the unit tests compile
 * against the android bootclasspath, which has no {@code java.lang.management}). Every shape,
 * font, stroke, colour and string the batch uses is built once in {@link #setUp()}, so the
 * number is the shim's own per-call cost plus the game-side {@code new Path2D.Double()} builds
 * the census performs each frame.
 *
 * <p>The figure is printed to stdout (captured in the JUnit XML report) and only a loose ceiling
 * is asserted: the test is a regression tripwire for a gross allocation leak, not a benchmark.
 * On a JVM without thread allocation accounting the test is skipped.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Graphics2DAllocationTest {

    private static final int WIDTH = 420;
    private static final int HEIGHT = 640;
    private static final int WARM_UP_BATCHES = 20;
    private static final int MEASURED_BATCHES = 200;
    /** Loose ceiling per batch (the pre-P4 shim measured well under this). */
    private static final long CEILING_BYTES_PER_BATCH = 4L * 1024 * 1024;

    private BufferedImage image;
    private Graphics2D g;
    private Color[] colors;
    private Font[] fonts;
    private BasicStroke[] strokes;
    private String[] labels;
    private Path2D.Double[] paths;
    private BufferedImage[] frames;
    private final Ellipse2D.Double ellipse = new Ellipse2D.Double();
    private final RoundRectangle2D.Double round = new RoundRectangle2D.Double();
    private final Line2D.Double line = new Line2D.Double();
    private final Ellipse2D.Double appended = new Ellipse2D.Double(2, 2, 6, 6);

    /** Keeps the metric results observable so nothing in the batch is dead code. */
    private long sink;

    @Before
    public void setUp() {
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        colors = new Color[] {new Color(255, 0, 0), new Color(0, 160, 0, 200),
                new Color(30, 60, 200), new Color(240, 200, 20)};
        Font base = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        fonts = new Font[] {base, base.deriveFont(Font.BOLD, 20f),
                new Font(Font.MONOSPACED, Font.PLAIN, 12)};
        strokes = new BasicStroke[] {new BasicStroke(1.5f),
                new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
                new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[] {6f, 5f}, 0f)};
        labels = new String[] {"Flapforge", "SCORE 1234", "Forja", "x3"};
        paths = new Path2D.Double[4];
        for (int p = 0; p < paths.length; p++) {
            paths[p] = gearOutline(60 + 40 * p, 120 + 90 * p, 18 + 3 * p, 8 + p);
        }
        BufferedImage sheet = new BufferedImage(64, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setColor(colors[0]);
        sg.fillRect(0, 0, 64, 16);
        sg.dispose();
        frames = new BufferedImage[4];
        for (int f = 0; f < frames.length; f++) {
            frames[f] = sheet.getSubimage(16 * f, 0, 16, 16);
        }
    }

    @Test
    public void reportsAllocatedBytesPerFrameLikeBatch() throws Exception {
        AllocationCounter counter = AllocationCounter.open();
        Assume.assumeTrue("thread allocation accounting is a HotSpot feature", counter != null);

        for (int i = 0; i < WARM_UP_BATCHES; i++) {
            batch(i);
        }
        long before = counter.allocatedBytes();
        for (int i = 0; i < MEASURED_BATCHES; i++) {
            batch(i);
        }
        long after = counter.allocatedBytes();
        long perBatch = (after - before) / MEASURED_BATCHES;

        System.out.println("Graphics2DAllocationTest: " + perBatch + " bytes allocated per batch"
                + " (300 fills, 100 draws, 50 getFontMetrics+drawString, 10 drawImage;"
                + " " + MEASURED_BATCHES + " batches after " + WARM_UP_BATCHES + " warm-up;"
                + " sink=" + sink + ")");

        int inked = PixelTestSupport.countInked(image);
        assertTrue("the batch inks a large part of the playfield, got " + inked, inked > 20000);
        assertTrue("allocation ceiling: " + perBatch + " bytes per batch",
                perBatch < CEILING_BYTES_PER_BATCH);
    }

    /** One frame-like batch; {@code frame} only perturbs the coordinates. */
    private void batch(int frame) {
        int shift = frame % 7;
        // Viewport bracket: a scale/translate pair around the frame and the logical clip.
        g.translate(0.5 + shift, 0.25);
        g.scale(1.5, 1.5);
        Shape saved = g.getClip();
        g.clipRect(0, 0, WIDTH, HEIGHT);

        // 300 fills: 100 ellipses, 80 rounded rectangles, 40 fillRect, 20 fillOval, 40 prebuilt
        // paths, 20 paths built the way BackgroundRenderer builds them each frame.
        for (int i = 0; i < 100; i++) {
            g.setColor(colors[i & 3]);
            ellipse.setFrame(10 + (i % 20) * 18, 20 + (i / 20) * 30 + shift, 14, 10);
            g.fill(ellipse);
        }
        for (int i = 0; i < 80; i++) {
            g.setColor(colors[(i + 1) & 3]);
            round.setRoundRect(12 + (i % 16) * 24, 200 + (i / 16) * 28 + shift, 20, 22, 8, 8);
            g.fill(round);
        }
        for (int i = 0; i < 40; i++) {
            g.setColor(colors[(i + 2) & 3]);
            g.fillRect(8 + (i % 10) * 40, 340 + (i / 10) * 20 + shift, 30, 12);
        }
        for (int i = 0; i < 20; i++) {
            g.setColor(colors[(i + 3) & 3]);
            g.fillOval(8 + (i % 10) * 40, 420 + (i / 10) * 24 + shift, 24, 16);
        }
        for (int i = 0; i < 40; i++) {
            g.setColor(colors[i & 3]);
            g.fill(paths[i & 3]);
        }
        for (int i = 0; i < 20; i++) {
            g.setColor(colors[i & 3]);
            Path2D.Double block = new Path2D.Double();
            double x = 10 + i * 18;
            double y = 470 + shift;
            block.moveTo(x, y);
            block.lineTo(x + 14, y);
            block.lineTo(x + 14, y + 40);
            block.lineTo(x, y + 40);
            block.closePath();
            block.append(appended, false);
            g.fill(block);
        }

        // 100 stroked draws under three strokes (one dashed).
        for (int i = 0; i < 40; i++) {
            g.setStroke(strokes[i % 3]);
            g.setColor(colors[i & 3]);
            g.drawLine(4, 520 + i * 2 + shift, 200 + (i % 5) * 20, 522 + i * 2);
        }
        for (int i = 0; i < 20; i++) {
            g.setStroke(strokes[i % 3]);
            line.x1 = 220;
            line.y1 = 520 + i * 4 + shift;
            line.x2 = 400 - i;
            line.y2 = 525 + i * 4;
            g.draw(line);
        }
        for (int i = 0; i < 20; i++) {
            g.setStroke(strokes[i % 3]);
            ellipse.setFrame(10 + i * 20, 600 + shift, 16, 12);
            g.draw(ellipse);
        }
        for (int i = 0; i < 20; i++) {
            g.setStroke(strokes[i % 3]);
            g.drawRoundRect(10 + i * 20, 615 + shift, 16, 14, 6, 6);
        }

        // 50 text draws through the TextPainter pattern: metrics, then a centred baseline.
        for (int i = 0; i < 50; i++) {
            g.setFont(fonts[i % 3]);
            g.setColor(colors[i & 3]);
            String label = labels[i & 3];
            FontMetrics fm = g.getFontMetrics();
            int width = fm.stringWidth(label);
            int ascent = fm.getAscent();
            sink += width + ascent + fm.getDescent();
            g.drawString(label, 40 + (i % 5) * 70 - width / 2f, 30 + (i / 5) * 60 + ascent / 2f);
        }

        // 10 sprite blits (natural size and scaled) from a shared sprite sheet.
        for (int i = 0; i < 10; i++) {
            if ((i & 1) == 0) {
                g.drawImage(frames[i & 3], 300 + i * 10, 100 + shift, null);
            } else {
                g.drawImage(frames[i & 3], 300 + i * 10, 140 + shift, 24, 24, null);
            }
        }

        g.setClip(saved);
        g.scale(1 / 1.5, 1 / 1.5);
        g.translate(-0.5 - shift, -0.25);
    }

    /**
     * {@code com.sun.management.ThreadMXBean.getCurrentThreadAllocatedBytes()} through
     * reflection; {@code null} when the running JVM has no thread allocation accounting.
     */
    private static final class AllocationCounter {

        private final Object bean;
        private final Method allocated;

        private AllocationCounter(Object bean, Method allocated) {
            this.bean = bean;
            this.allocated = allocated;
        }

        static AllocationCounter open() throws Exception {
            Class<?> hotspot;
            try {
                hotspot = Class.forName("com.sun.management.ThreadMXBean");
            } catch (ClassNotFoundException e) {
                return null;
            }
            Object bean = Class.forName("java.lang.management.ManagementFactory")
                    .getMethod("getThreadMXBean").invoke(null);
            if (!hotspot.isInstance(bean)
                    || !(Boolean) hotspot.getMethod("isThreadAllocatedMemorySupported").invoke(bean)
                    || !(Boolean) hotspot.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean)) {
                return null;
            }
            return new AllocationCounter(bean,
                    hotspot.getMethod("getCurrentThreadAllocatedBytes"));
        }

        long allocatedBytes() throws Exception {
            return (Long) allocated.invoke(bean);
        }
    }

    /** A gear-like closed outline (moveTo/lineTo/closePath, the GearRenderer pattern). */
    private static Path2D.Double gearOutline(double cx, double cy, double radius, int teeth) {
        Path2D.Double path = new Path2D.Double();
        int points = teeth * 2;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double r = (i & 1) == 0 ? radius : radius * 0.7;
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }
}
