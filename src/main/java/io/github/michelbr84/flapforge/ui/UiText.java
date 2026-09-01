package io.github.michelbr84.flapforge.ui;

/**
 * The few UI strings of M0, kept in one place so M2's {@code Strings}/{@code StringKey} can
 * replace them without hunting through screens (D25). English only until then.
 */
public final class UiText {

    /** Game title (drawn procedurally, never as an inherited wordmark). */
    public static final String TITLE = "Flapforge";
    /** Line under the title. */
    public static final String TAGLINE = "Arcade roguelite";
    /** Main menu: start a run. */
    public static final String PLAY = "Play";
    /** Main menu: open settings. */
    public static final String SETTINGS = "Settings";
    /** Main menu: leave the game. */
    public static final String QUIT = "Quit";
    /** Return to the previous screen. */
    public static final String BACK = "Back";
    /** Blinking hint shown while a run waits for its first flap. */
    public static final String READY_HINT = "Press Space / click to flap";
    /** Title of the pause overlay. */
    public static final String PAUSED = "Paused";
    /** How to leave the pause overlay and keep playing. */
    public static final String PAUSE_RESUME_HINT = "Space or click to resume";
    /** How to leave the pause overlay and abandon the run. */
    public static final String PAUSE_QUIT_HINT = "Esc quits to the menu";
    /** Title of the game-over overlay. */
    public static final String GAME_OVER = "Game over";
    /** Blinking prompt of the game-over overlay (D29). */
    public static final String GAME_OVER_PROMPT =
            "Space: retry   Enter: summary (M3)   Esc: menu";
    /** Game-over row: scoring columns cleared. */
    public static final String GATES = "Gates";
    /** Game-over row: points scored. */
    public static final String POINTS = "Points";
    /** Game-over row: how long the bird stayed alive. */
    public static final String TIME_ALIVE = "Time alive";
    /** Prefix of the HUD seed line, shown while the run is seeded. */
    public static final String SEED_PREFIX = "seed ";
    /** Message on the M0 settings placeholder. */
    public static final String SETTINGS_STUB_MESSAGE = "Settings arrive in M2";
    /** Footer hint listing the global keys. */
    public static final String KEYS_HINT = "F3 debug   F11 fullscreen";
    /** Prefix before the version number in the footer. */
    public static final String VERSION_PREFIX = "v";

    private UiText() {
    }
}
