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
    /** Message on the M0 game placeholder. */
    public static final String GAME_STUB_MESSAGE = "M1 will put the game here";
    /** Hint on the M0 game placeholder. */
    public static final String GAME_STUB_HINT = "Esc or Back returns to the menu";
    /** Message on the M0 settings placeholder. */
    public static final String SETTINGS_STUB_MESSAGE = "Settings arrive in M2";
    /** Footer hint listing the global keys. */
    public static final String KEYS_HINT = "F3 debug   F11 fullscreen";
    /** Prefix before the version number in the footer. */
    public static final String VERSION_PREFIX = "v";

    private UiText() {
    }
}
