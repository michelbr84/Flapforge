package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.app.FrameLimiter;
import io.github.michelbr84.flapforge.app.GameLoop;
import io.github.michelbr84.flapforge.app.NullPresenter;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputQueue;
import io.github.michelbr84.flapforge.input.KeyBindings;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.PrestigeSystem;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.Viewport;
import io.github.michelbr84.flapforge.support.ManualClock;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.screens.StatisticsScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The prestige panel of the statistics screen (M9, E4, E23), driven headlessly through the input
 * queue the way {@code StatisticsScreenTest} drives the screen. What is asserted is what the panel
 * promises: the words are there (the requirement, the banked bonus, the keeps and resets), the
 * button refuses a profile below level 25 and one at the cap, and a prestige takes two presses —
 * the first arms the question, the second resets the profile and the panel says so.
 */
class PrestigeUiTest {

    private static final int GRACE = ScreenManager.TRANSITION_GRACE_TICKS + 2;

    private ManualClock clock;
    private InputQueue input;
    private ScreenManager screens;
    private GameLoop loop;
    private Strings strings;
    private PlayerProfile profile;
    private long stamp = 1;

    @BeforeEach
    void setUp() {
        clock = new ManualClock(1_000_000_000L);
        input = new InputQueue(KeyBindings.defaults());
        Viewport viewport = new Viewport(Playfield.WIDTH, Playfield.HEIGHT, false);
        screens = new ScreenManager(viewport);
        NullPresenter presenter = new NullPresenter(screens, viewport, Playfield.WIDTH,
                Playfield.HEIGHT);
        screens.setPresenter(presenter);
        loop = new GameLoop(clock, input, screens, presenter, FrameLimiter.uncapped(clock));
        screens.setCloseHandler(loop::stop);
        strings = Strings.load("en");
        Strings.use(strings);
        profile = PlayerProfile.fresh(1000L).normalize();
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

    private StatisticsScreen open() {
        StatisticsScreen screen = new StatisticsScreen(screens, strings, profile);
        screens.push(screen);
        screens.applyPending();
        loop.start();
        ticks(GRACE);
        return screen;
    }

    /** Focus walks off the history list onto the prestige action (eligible profiles only). */
    private void focusPrestige(StatisticsScreen screen) {
        tap(Keys.DOWN);
        assertSame(screen.prestigeButton(), screen.focusRing().focused(),
                "Down moves to the prestige action");
    }

    @Test
    void thePanelSaysWhatAPrestigeCostsAndWhatItBanked() {
        profile.prestigeCount = 2;
        StatisticsScreen screen = open();

        assertNotNull(screen.row("prestigeCount"));
        assertEquals("2", screen.row("prestigeCount").value());
        assertEquals("+10% coins", screen.row("prestigeBonus").value(),
                "two prestiges bank 2 × 5 %");
        assertEquals(strings.format(StringKey.PRESTIGE_NEEDS_LEVEL, 25),
                screen.row("prestigeRequirement").value());
        assertEquals("birds, achievements, cosmetics, lifetime statistics",
                screen.row("prestigeKeeps").value());
        assertEquals(strings.get(StringKey.PRESTIGE_RESETS_LIST),
                screen.row("prestigeResets").value());
    }

    @Test
    void theButtonRefusesAProfileBelowTheLevelGate() {
        profile.level = 24;
        Wallet.of(profile).set(PlayerProfile.CURRENCY_COINS, 700);
        StatisticsScreen screen = open();

        assertFalse(screen.prestigeButton().isEnabled(), "the gate shows as a dead button");
        assertEquals(strings.format(StringKey.PRESTIGE_NEEDS_LEVEL, 25),
                screen.prestigeButton().text());

        // A dead button is out of the focus walk entirely: Down steps over it onto Back.
        tap(Keys.DOWN);
        assertSame(screen.backButton(), screen.focusRing().focused(),
                "the dead prestige action is skipped");
        tap(Keys.ENTER);
        assertFalse(screen.prestigeArmed(), "a refused activation arms nothing");
        assertEquals(24, profile.level);
        assertEquals(700, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS),
                "the wallet is untouched");
        assertEquals(0, profile.prestigeCount);
    }

    @Test
    void theButtonRefusesAProfileAtTheCap() {
        profile.level = 25;
        profile.prestigeCount = 5;
        StatisticsScreen screen = open();

        assertFalse(screen.prestigeButton().isEnabled());
        assertEquals(strings.get(StringKey.PRESTIGE_MAXED), screen.prestigeButton().text());
        tap(Keys.DOWN);
        assertSame(screen.backButton(), screen.focusRing().focused(),
                "the capped-out action is skipped too");
        tap(Keys.ENTER);
        assertEquals(5, profile.prestigeCount, "the cap held");
    }

    @Test
    void aPrestigeTakesTwoPressesAndThenThePanelSaysSo() {
        profile.level = 25;
        Wallet.of(profile).set(PlayerProfile.CURRENCY_COINS, 700);
        profile.upgrades.put("glide_1", 1);
        StatisticsScreen screen = open();

        focusPrestige(screen);
        assertEquals(strings.get(StringKey.PRESTIGE_ACTION), screen.prestigeButton().text());

        // First press: arm the question. Nothing has happened yet.
        tap(Keys.ENTER);
        assertTrue(screen.prestigeArmed(), "the first press arms the confirm");
        assertEquals(strings.get(StringKey.PRESTIGE_CONFIRM), screen.prestigeButton().text());
        assertEquals(25, profile.level);
        assertEquals(700, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS));

        // BACK is an escape hatch: it disarms instead of leaving the question hanging.
        tap(Keys.ESCAPE);
        assertFalse(screen.prestigeArmed(), "BACK disarms");
        assertEquals(strings.get(StringKey.PRESTIGE_ACTION), screen.prestigeButton().text());
        assertEquals(25, profile.level, "the profile survived the disarm");

        // Second visit, both presses: the reset lands.
        tap(Keys.ENTER);
        tap(Keys.ENTER);
        assertFalse(screen.prestigeArmed());
        assertEquals(1, profile.prestigeCount);
        assertEquals(1, profile.level, "the level starts over");
        assertEquals(0, Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS));
        assertTrue(profile.upgrades.isEmpty(), "the nodes are gone");
        assertTrue(profile.isUnlocked("cosmetic:classic:prestige"), "the badge is granted");

        // The panel re-reads the profile: the count row and the button both say what happened.
        assertEquals("1", screen.row("prestigeCount").value());
        assertEquals("+5% coins", screen.row("prestigeBonus").value());
        assertFalse(screen.prestigeButton().isEnabled(), "level 1 cannot prestige again");
        assertEquals(strings.format(StringKey.PRESTIGE_NEEDS_LEVEL, 25),
                screen.prestigeButton().text());
    }

    @Test
    void theRowBuildsBackAfterAPrestigeInTheSameSession() {
        profile.level = 25;
        StatisticsScreen screen = open();
        PrestigeSystem.prestige(profile, null);
        // A screen that is re-entered re-reads the profile it shows.
        screen.onEnter();

        assertEquals("1", screen.row("prestigeCount").value());
        assertTrue(profile.isUnlocked("cosmetic:classic:prestige"));
        assertEquals(PlayerProfile.DEFAULT_WORLD, profile.selected.worldId,
                "E15: the selection cannot point at an unlock the reset dropped");
    }
}
