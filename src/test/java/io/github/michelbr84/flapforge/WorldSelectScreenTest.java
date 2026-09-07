package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.SelectionManager;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.screens.ProgressionText;
import io.github.michelbr84.flapforge.ui.screens.WorldSelectScreen;
import java.awt.Graphics2D;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The world picker of the home hub (D17, M7, M10), driven headlessly through the input queue
 * and the loop: one card per world in content order, a locked card refused with a toast, an
 * owned card written through the selection manager (saved once) and popped, the difficulty
 * row's snap-back and write, the language switch and a non-blank render whose palette follows
 * the selection.
 */
class WorldSelectScreenTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;
    private static final long NOW = 1_700_000_000_000L;

    /** A root under the picker, so a pop has somewhere to land. */
    private static final class Root implements Screen {
        @Override
        public void tick(InputFrame input) {
        }

        @Override
        public void render(Graphics2D g, double alpha) {
        }
    }

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private NullPresenter presenter;
    private GameLoop loop;
    private GameContent content;
    private Strings strings;
    private PlayerProfile profile;
    private SelectionManager selection;
    private ToastLayer toasts;
    private Root root;
    private int saves;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        presenter = new NullPresenter(screens, viewport, Playfield.WIDTH, Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        strings = Strings.load("en");
        Strings.use(strings);
        content = GameContent.load();
        FixedTimeSource time = new FixedTimeSource(NOW);
        profile = PlayerProfile.fresh(NOW).normalize();
        ProgressionManager progression = new ProgressionManager(time,
                ProgressionManager.AchievementHook.NONE, UnlockEvaluator.of(content));
        selection = new SelectionManager(progression, () -> saves++);
        toasts = new ToastLayer();
        root = new Root();
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

    private WorldSelectScreen open() {
        WorldSelectScreen screen = new WorldSelectScreen(screens, strings, content, profile,
                selection, toasts);
        screens.setRoot(root);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    @Test
    void oneCardPerWorldInContentOrderWithTheSelectedOneFocused() {
        WorldSelectScreen screen = open();
        List<String> ids = content.worlds().ids();
        assertEquals(ids, screen.worldIds());
        assertEquals(ids.size(), screen.worldGrid().size());
        for (int i = 0; i < ids.size(); i++) {
            WorldDef def = content.worlds().get(ids.get(i));
            CardGrid.Card card = screen.worldGrid().cards().get(i);
            assertEquals(ids.get(i), card.id());
            assertEquals(strings.format(StringKey.WORLD_SELECT_CARD, def.order(),
                    ProgressionText.name(strings, ContentKind.WORLD, def.id())), card.title());
            assertEquals(!profile.isUnlocked(def.unlockableId()), card.isLocked(), def.id());
            assertEquals(def.id().equals(profile.selected.worldId), card.isSelected(), def.id());
        }
        assertSame(screen.worldGrid().card(profile.selected.worldId), screen.focusRing().focused(),
                "the selected world's card takes focus on entry");
        assertEquals("green_fields", screen.currentWorldId());
        assertTrue(screen.worldGrid().cards().get(0).subtitle().startsWith(
                strings.format(StringKey.BIRDS_WORLD_HAZARDS, "").trim()),
                "an owned world says what it spawns: "
                        + screen.worldGrid().cards().get(0).subtitle());
        assertTrue(screen.worldGrid().cards().get(1).subtitle().startsWith(
                strings.format(StringKey.BIRDS_WORLD_LOCKED, "").trim()),
                "a locked world says how it opens: "
                        + screen.worldGrid().cards().get(1).subtitle());
        assertFalse(screen.descriptionLine().isEmpty(), "the focused world is described");
        assertEquals(ProgressionText.description(strings, ContentKind.WORLD, "green_fields"),
                screen.descriptionLine());
    }

    @Test
    void aLockedCardIsRefusedWithAToast() {
        WorldSelectScreen screen = open();
        tap(Keys.DOWN);
        assertSame(screen.worldGrid().card("wind_valley"), screen.focusRing().focused());
        assertEquals("wind_valley", screen.currentWorldId(), "the description follows the focus");
        assertEquals(ProgressionText.description(strings, ContentKind.WORLD, "wind_valley"),
                screen.descriptionLine());
        tap(Keys.ENTER);
        ticks(GRACE);
        assertSame(screen, screens.top(), "nothing was popped");
        assertEquals("green_fields", profile.selected.worldId);
        assertEquals(0, saves);
        assertEquals(1, toasts.pushedCount(), "the refusal is said out loud");
    }

    @Test
    void anOwnedCardWritesTheSelectionOnceAndPops() {
        profile.unlock("world:iron_forge");
        WorldSelectScreen screen = open();
        tap(Keys.DOWN);
        tap(Keys.DOWN);
        assertSame(screen.worldGrid().card("iron_forge"), screen.focusRing().focused());
        tap(Keys.ENTER);
        ticks(GRACE);
        assertEquals("iron_forge", profile.selected.worldId);
        assertEquals(1, saves, "the selection is written now");
        assertSame(root, screens.top(), "the picker popped back to the hub");
        assertEquals(0, toasts.pushedCount());
    }

    @Test
    void theDifficultyRowSnapsBackOnALockedTierAndWritesAnOwnedOne() {
        WorldSelectScreen screen = open();
        assertEquals(content.tiers().ids(), screen.tierIds());
        assertEquals(strings.get(StringKey.WORLD_SELECT_TIER), screen.tierList().label());
        screen.focusRing().focus(screen.tierList());
        ticks(1);
        tap(Keys.RIGHT);
        assertEquals("normal", profile.selected.tierId, "a locked tier is refused");
        assertEquals(0, screen.tierList().selectedIndex(), "the row snapped back");
        assertEquals(1, toasts.pushedCount());
        assertEquals(0, saves);

        profile.unlock("tier:hard");
        screen.refreshState();
        tap(Keys.RIGHT);
        assertEquals("hard", profile.selected.tierId);
        assertEquals(1, screen.tierList().selectedIndex());
        assertEquals(1, saves);
    }

    @Test
    void aLanguageSwitchRelabelsTheScreen() {
        WorldSelectScreen screen = open();
        Strings.active().reload("pt_BR");
        Strings.use(Strings.active());
        ticks(1);

        Strings pt = Strings.load("pt_BR");
        assertEquals(pt.get(StringKey.COMMON_BACK), screen.backButton().text());
        assertEquals(pt.get(StringKey.WORLD_SELECT_TIER), screen.tierList().label());
        assertEquals(pt.format(StringKey.WORLD_SELECT_CARD, 1,
                pt.name("world", "green_fields")), screen.worldGrid().cards().get(0).title());
        assertEquals(pt.desc("world", "green_fields"), screen.descriptionLine());
    }

    @Test
    void theScreenRendersAndItsPaletteFollowsTheSelection() {
        profile.unlock("world:storm_sky");
        WorldSelectScreen screen = open();
        assertEquals(WorldPalette.from(content.worlds().get("green_fields").palette()),
                screen.palette());
        assertEquals(screen.palette().letterbox(), screens.letterboxRgb());
        presenter.present(0.5);
        assertNotNull(presenter.image(), "a frame was drawn");
        assertEquals(Playfield.WIDTH, presenter.image().getWidth());
        assertEquals(content.worlds().size() + 2, screen.focusRing().nodes().size(),
                "cards, difficulty and back are focusable");

        profile.selected.worldId = "storm_sky";
        screen.refreshState();
        assertEquals(WorldPalette.from(content.worlds().get("storm_sky").palette()),
                screen.palette());
        assertTrue(screen.worldGrid().card("storm_sky").isSelected());
        assertFalse(screen.worldGrid().card("green_fields").isSelected());
        presenter.present(0.5);
        assertNotNull(presenter.image());

        WorldSelectScreen bare = new WorldSelectScreen(screens, strings, content, profile, null,
                null);
        screens.push(bare);
        screens.applyPending();
        ticks(GRACE);
        assertSame(bare, screens.top());
        tap(Keys.ENTER);
        ticks(GRACE);
        assertSame(screen, screens.top(), "without a writer an owned card just pops");
    }
}
