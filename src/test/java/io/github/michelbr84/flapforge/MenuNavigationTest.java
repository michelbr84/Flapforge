package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Vec2;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.screens.GameScreen;
import io.github.michelbr84.flapforge.ui.screens.MainMenuScreen;
import io.github.michelbr84.flapforge.ui.screens.SettingsScreen;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Menu navigation through the queue, the loop, the viewport mapping and the screen stack,
 * headless with a {@link NullPresenter} at 2x (the toolkit path is covered by the
 * {@code gui}-tagged {@code SmokeWindowTest}).
 */
class MenuNavigationTest {

    private static final int W = Playfield.WIDTH * 2;
    private static final int H = Playfield.HEIGHT * 2;
    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    /** Screen that counts renders and can pose as an overlay. */
    static final class CountingScreen implements Screen {
        final boolean overlay;
        int renders;

        CountingScreen(boolean overlay) {
            this.overlay = overlay;
        }

        @Override
        public void tick(InputFrame input) {
        }

        @Override
        public void render(Graphics2D g, double alpha) {
            renders++;
        }

        @Override
        public boolean isOverlay() {
            return overlay;
        }
    }

    private ManualClock clock;
    private InputQueue input;
    private Viewport viewport;
    private ScreenManager screens;
    private NullPresenter presenter;
    private GameLoop loop;
    private MainMenuScreen menu;
    private boolean closed;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        viewport = new Viewport(W, H, false);
        screens = new ScreenManager(viewport);
        presenter = new NullPresenter(screens, viewport, W, H);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(() -> {
            closed = true;
            loop.stop();
        });
        // The screen captures the active table; give it a fresh English one so a previous test
        // cannot leave another language behind.
        Strings.use(Strings.load("en"));
        menu = new MainMenuScreen(screens);
        screens.push(menu);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
    }

    @AfterEach
    void tearDown() {
        Strings.use(Strings.load("en"));
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            clock.advance(Playfield.TICK_NS);
            loop.frame();
        }
    }

    private void tap(int keyCode) {
        input.offer(new RawInput.KeyDown(keyCode, stamp++));
        input.offer(new RawInput.KeyUp(keyCode, stamp++));
        ticks(1);
    }

    private void click(UiNode node) {
        Vec2 w = viewport.toWindow(node.centerX(), node.centerY());
        int wx = (int) Math.round(w.x());
        int wy = (int) Math.round(w.y());
        input.offer(new RawInput.MouseMove(wx, wy));
        input.offer(new RawInput.MouseDown(Keys.BUTTON_LEFT, wx, wy));
        input.offer(new RawInput.MouseUp(Keys.BUTTON_LEFT, wx, wy));
        ticks(1);
    }

    @Test
    void keyboardMovesFocusOpensTheGameAndEscapesBack() {
        assertSame(menu.playButton(), menu.focusRing().focused(), "Play focused on entry");
        tap(Keys.DOWN);
        assertSame(menu.settingsButton(), menu.focusRing().focused(), "Down moves to Settings");
        tap(Keys.UP);
        assertSame(menu.playButton(), menu.focusRing().focused(), "Up moves back to Play");
        tap(Keys.ENTER);
        assertTrue(screens.top() instanceof GameScreen, "Enter on Play pushes the game");
        ticks(GRACE);
        tap(Keys.ESCAPE);
        assertSame(menu, screens.top(), "Esc pops back to the menu");
        assertEquals(1, screens.depth());
    }

    @Test
    void mouseClicksMapThroughTheViewportAtTwoTimes() {
        assertEquals(2.0, viewport.scale(), 1e-9);
        click(menu.settingsButton());
        assertTrue(screens.top() instanceof SettingsScreen, "click on Settings pushes it");
        SettingsScreen settings = (SettingsScreen) screens.top();
        ticks(GRACE);
        click(settings.backButton());
        assertSame(menu, screens.top(), "click on Back pops to the menu");
    }

    @Test
    void aLanguageSwitchRelabelsTheMenuOnTheNextTick() {
        // M2's "switch to pt_BR live": the settings screen reloads the shared table and pops back,
        // and the menu below has to notice on its own -- nothing tells it. This is exactly what
        // GameContext.applyLanguage does: reload the active instance, then re-publish it.
        assertEquals(Strings.load("en").get(StringKey.MENU_PLAY), menu.playButton().text());

        Strings active = Strings.active();
        active.reload("pt_BR");
        Strings.use(active);
        ticks(1);

        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.MENU_PLAY), menu.playButton().text(), "Play was relabelled");
        assertEquals(pt.get(StringKey.MENU_SETTINGS), menu.settingsButton().text());
        assertEquals(pt.get(StringKey.MENU_QUIT), menu.quitButton().text());
        assertNotEquals(Strings.load("en").get(StringKey.MENU_PLAY), menu.playButton().text(),
                "the two languages must actually differ for this to prove anything");
    }

    @Test
    void quitThroughTheMenuRequestsClose() {
        tap(Keys.DOWN);
        tap(Keys.DOWN);
        assertSame(menu.quitButton(), menu.focusRing().focused());
        tap(Keys.ENTER);
        assertTrue(closed, "Quit runs the close handler");
        assertTrue(screens.isCloseRequested());
        assertFalse(loop.isRunning());
    }

    @Test
    void presenterRendersTheMenuNonUniformly() {
        ticks(2);
        BufferedImage image = presenter.image();
        assertNotNull(image);
        assertEquals(W, image.getWidth());
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 8) {
            for (int x = 0; x < image.getWidth(); x += 8) {
                colours.add(image.getRGB(x, y) & 0xffffff);
            }
        }
        assertTrue(colours.size() >= 2, "menu render is uniform");
    }

    @Test
    void renderDrawsTheTopFullScreenAndTheOverlaysAboveIt() {
        CountingScreen base = new CountingScreen(false);
        CountingScreen overlay = new CountingScreen(true);
        CountingScreen another = new CountingScreen(false);
        screens.push(base);
        screens.push(overlay);
        screens.applyPending();
        ticks(1);
        assertEquals(1, base.renders, "the full screen below the overlay is drawn");
        assertEquals(1, overlay.renders);

        screens.push(another);
        screens.applyPending();
        ticks(1);
        assertEquals(1, base.renders, "screens below a full screen are not drawn");
        assertEquals(1, overlay.renders);
        assertEquals(1, another.renders);
        assertEquals(4, screens.depth());
        assertEquals(another, screens.screens().get(3), "screens() is bottom to top");
        assertEquals(menu, screens.screens().get(0));
    }
}
