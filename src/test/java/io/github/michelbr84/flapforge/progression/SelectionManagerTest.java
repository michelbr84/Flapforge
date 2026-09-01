package io.github.michelbr84.flapforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.support.FixedTimeSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SelectionManager}: a selection is only ever set to something owned and playable, and
 * every accepted change is written straight away (D15's selection write trigger).
 */
class SelectionManagerTest {

    private static GameContent content;

    private PlayerProfile profile;
    private ProgressionManager progression;
    private SelectionManager selection;
    private int saves;

    @BeforeAll
    static void loadContent() {
        content = GameContent.load();
    }

    @BeforeEach
    void setUp() {
        profile = PlayerProfile.fresh(0).normalize();
        progression = new ProgressionManager(new FixedTimeSource(0));
        saves = 0;
        selection = new SelectionManager(progression, () -> saves++);
    }

    @Test
    void aBirdMustBeOwned() {
        assertFalse(selection.selectBird(profile, "guardian", content));
        assertEquals("classic", profile.selected.birdId);
        assertFalse(progression.isDirty());
        assertEquals(0, saves);

        profile.unlock("bird:guardian");
        profile.unlock("cosmetic:guardian:default");
        assertTrue(selection.selectBird(profile, "guardian", content));
        assertEquals("guardian", profile.selected.birdId);
        assertTrue(progression.isDirty());
        assertEquals(1, saves, "a selection change is written now");
    }

    @Test
    void changingTheBirdRepairsThePalette() {
        profile.unlock("bird:guardian");
        profile.unlock("cosmetic:guardian:default");
        profile.unlock("cosmetic:classic:ember");
        assertTrue(selection.selectPalette(profile, "ember", content));
        assertEquals("ember", profile.selected.paletteId);

        assertTrue(selection.selectBird(profile, "guardian", content));
        assertEquals("default", profile.selected.paletteId,
                "ember belongs to Forgewing, not to Ironbeak");
    }

    @Test
    void aPaletteMustBelongToTheSelectedBirdAndBeOwned() {
        assertFalse(selection.selectPalette(profile, "ember", content), "not owned yet");
        assertFalse(selection.selectPalette(profile, "bronze", content),
                "bronze is a Guardian palette");
        assertFalse(selection.selectPalette(profile, "nope", content));
        assertEquals("default", profile.selected.paletteId);
        assertEquals(0, saves);
    }

    @Test
    void anUnknownOrUnownedTierIsRefused() {
        assertFalse(selection.selectTier(profile, "hard", content));
        assertFalse(selection.selectTier(profile, "impossible", content));
        assertEquals("normal", profile.selected.tierId);

        profile.unlock("tier:hard");
        assertTrue(selection.selectTier(profile, "hard", content));
        assertEquals("hard", profile.selected.tierId);
        assertEquals("hard", RunLoadout.configFor(profile, content, 1,
                RunConfig.classic(1).mode()).tierId(), "the run is played on it");
    }

    @Test
    void aWorldThisBuildCannotPlayIsRefusedEvenWhenOwned() {
        profile.unlock("world:wind_valley");
        assertFalse(selection.selectWorld(profile, "wind_valley", content),
                "E19: only Green Fields is playable until M7");
        assertEquals("green_fields", profile.selected.worldId);
        assertTrue(selection.selectWorld(profile, "green_fields", content));
        assertEquals(0, saves, "re-selecting what is already selected writes nothing");
    }

    @Test
    void aSelectionThatDoesNotChangeAnythingIsNotWritten() {
        assertTrue(selection.selectBird(profile, "classic", content));
        assertTrue(selection.selectPalette(profile, "default", content));
        assertTrue(selection.selectTier(profile, "normal", content));
        assertFalse(progression.isDirty());
        assertEquals(0, saves);
    }
}
