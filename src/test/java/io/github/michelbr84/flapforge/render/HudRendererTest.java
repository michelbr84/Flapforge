package io.github.michelbr84.flapforge.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.ability.AbilityInstance;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The ability half of the HUD (M5, D9, D17): the cooldown ring, the charge pips, the duration bar,
 * the shield icons and the three flashes.
 *
 * <p>Everything is driven through a real {@link Run} built from the shipped content, so the
 * numbers the HUD draws are the numbers {@code data/abilities.json} ships; and everything is drawn
 * into a {@link BufferedImage} with no asset manifest, so every pixel comes from
 * {@link ProceduralArt} and the panel is proven to be procedural rather than merely intended to be
 * (D18).
 *
 * <p>The labels are handed in the way the screen hands them in — already translated — and the
 * sweep runs in both shipped languages, because a HUD that reads the string table itself, or one
 * that caches a label across a language switch, would show English on a Portuguese screen.
 */
class HudRendererTest {

    /** Half-width of the region the badge, its pips and the shield row occupy. */
    private static final int PANEL_RIGHT = 160;
    /** Per-frame allocation budget of the ability HUD, in bytes. */
    private static final long ALLOCATION_BUDGET_BYTES = 4 * 1024;

    // ------------------------------------------------------------------ fixtures

    private static Run runWith(String activeAbilityId, String... passives) {
        GameContent content = GameContent.load();
        RunConfig.Builder config = RunConfig.builder(42).activeAbilityId(activeAbilityId);
        if (passives.length > 0) {
            config.passiveAbilityIds(java.util.List.of(passives));
        }
        return new RunFactory(content).newRun(config.build());
    }

    private static void start(Run run) {
        run.tick(new RunInput(true, false, RunInput.NO_CHOICE, false));
        assertEquals(RunPhase.FLYING, run.phase());
    }

    private static void fly(Run run, int ticks) {
        for (int i = 0; i < ticks; i++) {
            run.tick(RunInput.NONE);
        }
    }

    private static HudRenderer hud(Strings strings) {
        HudRenderer hud = new HudRenderer(strings.get(StringKey.GAME_READY_HINT));
        hud.setStreakLabel(strings.get(StringKey.HUD_STREAK));
        hud.setCoinLabel(strings.get(StringKey.HUD_COINS));
        hud.setAbilityStateLabels(strings.get(StringKey.HUD_ABILITY_READY),
                strings.get(StringKey.HUD_ABILITY_COOLDOWN));
        hud.setShieldLabel(strings.get(StringKey.HUD_SHIELD_CHARGES));
        return hud;
    }

    private static BufferedImage draw(HudRenderer hud, Run run) {
        BufferedImage image = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            ProceduralArt.fillBackground(g, WorldPalette.GREEN_FIELDS);
            hud.render(g, run, WorldPalette.GREEN_FIELDS, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** The colours in a rectangle, so "is anything drawn here" is one number. */
    private static int coloursIn(BufferedImage image, int x0, int y0, int x1, int y1) {
        Set<Integer> colours = new HashSet<>();
        for (int y = Math.max(0, y0); y < Math.min(image.getHeight(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(image.getWidth(), x1); x++) {
                colours.add(image.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return colours.size();
    }

    private static boolean same(BufferedImage a, BufferedImage b, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((a.getRGB(x, y) & 0xFFFFFF) != (b.getRGB(x, y) & 0xFFFFFF)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int badgeTop() {
        return HudRenderer.ABILITY_CY - HudRenderer.ABILITY_RADIUS - 2;
    }

    private static int badgeBottom() {
        return HudRenderer.ABILITY_CY + HudRenderer.ABILITY_RADIUS + 2;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void theCooldownRingFillsAsTheAbilityRecharges() {
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        Run run = runWith("dash");
        AbilityInstance dash = run.simulation().abilities().active();
        assertNotNull(dash);
        assertEquals(600, dash.levelDef().cooldownTicks(), "the shipped dash cools down 600 ticks");

        start(run);
        hud.tick(run);
        BufferedImage ready = draw(hud, run);
        assertEquals(strings.get(StringKey.HUD_ABILITY_READY), hud.abilityStateText(),
                "an ability off cooldown says so");
        assertTrue(coloursIn(ready, 0, badgeTop(), PANEL_RIGHT, badgeBottom()) >= 3,
                "the badge is drawn");

        run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        hud.tick(run);
        assertEquals(1, dash.activations(), "the dash was activated");
        assertTrue(dash.isActive(), "and its burst is running");
        BufferedImage running = draw(hud, run);
        assertFalse(same(ready, running, 0, badgeTop(), PANEL_RIGHT, badgeBottom() + 20),
                "a running ability does not look like a ready one");

        // The duration bar shrinks while the burst runs.
        int barTop = HudRenderer.ABILITY_CY + 2;
        int barBottom = HudRenderer.ABILITY_CY + 3 + HudRenderer.DURATION_BAR_H + 1;
        int barRight = HudRenderer.ABILITY_TEXT_X + HudRenderer.DURATION_BAR_W;
        assertTrue(coloursIn(running, HudRenderer.ABILITY_TEXT_X, barTop, barRight, barBottom) >= 2,
                "the duration bar is drawn while the ability runs");
        double full = dash.durationFraction(run.simulation().stats());
        fly(run, dash.levelDef().durationTicks() / 2);
        hud.tick(run);
        assertTrue(dash.durationFraction(run.simulation().stats()) < full, "the bar drains");
        BufferedImage half = draw(hud, run);
        assertFalse(same(running, half, HudRenderer.ABILITY_TEXT_X, barTop, barRight, barBottom),
                "and the drawn bar drains with it");

        // The burst ends, the cooldown runs, and the ring fills as it does.
        fly(run, dash.levelDef().durationTicks() + 4);
        hud.tick(run);
        assertFalse(dash.isActive());
        double early = dash.cooldownFraction(run.simulation().stats());
        BufferedImage earlyFrame = draw(hud, run);
        assertEquals(strings.format(StringKey.HUD_ABILITY_COOLDOWN, dash.cooldownRemaining()),
                hud.abilityStateText(), "the readout counts the ticks left");

        fly(run, 200);
        hud.tick(run);
        double late = dash.cooldownFraction(run.simulation().stats());
        assertTrue(late < early, "the cooldown ran down: " + early + " -> " + late);
        BufferedImage lateFrame = draw(hud, run);
        assertFalse(same(earlyFrame, lateFrame, 0, badgeTop(), PANEL_RIGHT, badgeBottom()),
                "the ring must move as the cooldown runs down");
    }

    @Test
    void theStateReadoutIsRebuiltOnlyWhenItsNumberChanges() {
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        Run run = runWith("dash");
        start(run);
        run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        fly(run, run.simulation().abilities().active().levelDef().durationTicks() + 4);
        hud.tick(run);
        draw(hud, run);
        String first = hud.abilityStateText();
        draw(hud, run);
        assertSame(first, hud.abilityStateText(),
                "a frame that changes no number allocates no string (D18)");
    }

    @Test
    void chargePipsFollowTheChargesOfAChargeGatedAbility() {
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        Run run = runWith("double_flap");
        AbilityInstance flap = run.simulation().abilities().active();
        assertEquals(2, flap.maxCharges(), "the shipped double flap ships two charges");

        start(run);
        fly(run, 20);
        hud.tick(run);
        BufferedImage full = draw(hud, run);
        int pipTop = HudRenderer.CHARGE_ROW_CY - HudRenderer.CHARGE_PIP_RADIUS - 2;
        int pipBottom = HudRenderer.CHARGE_ROW_CY + HudRenderer.CHARGE_PIP_RADIUS + 2;
        assertTrue(coloursIn(full, 0, pipTop, PANEL_RIGHT, pipBottom) >= 2, "the pips are drawn");

        run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        hud.tick(run);
        assertEquals(1, flap.charges(), "one charge was spent");
        BufferedImage spent = draw(hud, run);
        assertFalse(same(full, spent, 0, pipTop, PANEL_RIGHT, pipBottom),
                "a spent charge must empty one pip");
    }

    @Test
    void shieldIconsShowTheChargesAndFlashWhenOneIsConsumed() {
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        Run run = runWith(null, "shield");
        assertEquals(1, run.simulation().shield().maxCharges(),
                "the shield passive grants one charge (D9)");
        start(run);
        hud.tick(run);
        BufferedImage held = draw(hud, run);
        assertEquals(strings.format(StringKey.HUD_SHIELD_CHARGES, 1), hud.shieldText());
        int rowTop = HudRenderer.SHIELD_ROW_CY - 9;
        int rowBottom = HudRenderer.SHIELD_ROW_CY + 9;
        assertTrue(coloursIn(held, 0, rowTop, PANEL_RIGHT, rowBottom) >= 2,
                "the shield icon is drawn");
        assertEquals("", hud.abilityName(), "a passive-only loadout gets no ability badge");

        // Spending the charge is the run's business; the HUD notices it from the counter.
        assertTrue(run.simulation().shield().absorb());
        hud.tick(run);
        assertTrue(hud.isShieldFlashing(), "a consumed charge is flashed");
        BufferedImage spent = draw(hud, run);
        assertEquals(strings.format(StringKey.HUD_SHIELD_CHARGES, 0), hud.shieldText());
        assertFalse(same(held, spent, 0, rowTop, PANEL_RIGHT, rowBottom),
                "the empty icon and the flash must change the row");

        for (int i = 0; i < HudRenderer.FLASH_TICKS; i++) {
            hud.tick(run);
        }
        assertFalse(hud.isShieldFlashing(), "and the flash ends");
    }

    @Test
    void arunWithoutAnAbilityDrawsNeitherBadgeNorShieldRow() {
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        Run run = Run.classic(RunConfig.classic(42));
        start(run);
        hud.tick(run);
        draw(hud, run);
        assertEquals("", hud.abilityStateText(), "no badge, no state line");
        assertEquals("", hud.shieldText(), "no charges, no shield row");
    }

    @Test
    void theRefusalFlashRunsAndEnds() {
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        Run run = runWith("dash");
        start(run);
        hud.setAbilityName("Dash");
        hud.tick(run);
        BufferedImage quiet = draw(hud, run);

        hud.flashAbilityRefused();
        assertTrue(hud.isAbilityRefused());
        BufferedImage refused = draw(hud, run);
        assertFalse(same(quiet, refused, 0, badgeTop(), PANEL_RIGHT, badgeBottom()),
                "a refused press must be visible on the badge");

        for (int i = 0; i < HudRenderer.FLASH_TICKS; i++) {
            hud.tick(run);
        }
        assertFalse(hud.isAbilityRefused(), "the blink ends on its own");
    }

    /**
     * D18's "no per-frame allocation" rule, measured on the panel this milestone added: with the
     * badge, the pips, the shield row and their four strings on screen, a steady frame must not
     * allocate. The strings are rebuilt only when their number changes and every shape and colour
     * is owned by the renderer, so what is left is Java2D's own bookkeeping.
     *
     * <p>Tagged {@code perf} for the same reason the game-frame budget is (section 7): the number
     * drifts with the JDK and the machine, and an {@code assumeTrue} skip must not hide inside a
     * milestone's own {@code test} gate.
     */
    @Test
    @Tag("perf")
    void aSteadyAbilityHudFrameStaysWithinItsAllocationBudget() {
        com.sun.management.ThreadMXBean threads = allocationCounter();
        org.junit.jupiter.api.Assumptions.assumeTrue(threads != null,
                "no per-thread allocation counter on this JVM");
        Strings strings = Strings.load("en");
        HudRenderer hud = hud(strings);
        hud.setAbilityName("Dash");
        Run run = runWith("dash", "shield");
        start(run);
        run.tick(new RunInput(false, true, RunInput.NO_CHOICE, false));
        fly(run, run.simulation().abilities().active().levelDef().durationTicks() + 4);
        hud.tick(run);

        BufferedImage image = new BufferedImage(Playfield.WIDTH, Playfield.HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            ProceduralArt.prepare(g);
            for (int i = 0; i < 50; i++) {
                hud.render(g, run, WorldPalette.GREEN_FIELDS, null);
            }
            long id = Thread.currentThread().getId();
            long before = threads.getThreadAllocatedBytes(id);
            int frames = 300;
            for (int i = 0; i < frames; i++) {
                hud.render(g, run, WorldPalette.GREEN_FIELDS, null);
            }
            long perFrame = (threads.getThreadAllocatedBytes(id) - before) / frames;
            System.out.println("[hud] ability HUD frame allocates " + perFrame + " bytes");
            assertTrue(perFrame < ALLOCATION_BUDGET_BYTES,
                    "the ability HUD allocated " + perFrame + " bytes per frame, budget "
                            + ALLOCATION_BUDGET_BYTES);
        } finally {
            g.dispose();
        }
    }

    private static com.sun.management.ThreadMXBean allocationCounter() {
        java.lang.management.ThreadMXBean bean =
                java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean sun)
                || !sun.isThreadAllocatedMemorySupported()) {
            return null;
        }
        sun.setThreadAllocatedMemoryEnabled(true);
        return sun;
    }

    @Test
    void theAbilityPanelRendersInBothLanguages() {
        BufferedImage english = null;
        BufferedImage portuguese = null;
        for (String language : Strings.LANGUAGES) {
            Strings strings = Strings.load(language);
            HudRenderer hud = hud(strings);
            Run run = runWith("dash", "shield");
            hud.setAbilityName(strings.name("ability", "dash"));
            start(run);
            fly(run, 10);
            hud.tick(run);
            BufferedImage frame = draw(hud, run);
            assertTrue(coloursIn(frame, 0, badgeTop(), PANEL_RIGHT,
                    HudRenderer.SHIELD_ROW_CY + 9) >= 4,
                    "the ability panel is blank in " + language);
            assertEquals(strings.get(StringKey.HUD_ABILITY_READY), hud.abilityStateText(),
                    "the state word is translated in " + language);
            assertEquals(strings.format(StringKey.HUD_SHIELD_CHARGES, 1), hud.shieldText(),
                    "the shield readout is translated in " + language);
            if ("en".equals(language)) {
                english = frame;
            } else {
                portuguese = frame;
            }
        }
        assertNotNull(english);
        assertNotNull(portuguese);
        assertFalse(same(english, portuguese, 0, badgeTop(), PANEL_RIGHT, badgeBottom()),
                "the two languages must not draw the same pixels");
    }
}
