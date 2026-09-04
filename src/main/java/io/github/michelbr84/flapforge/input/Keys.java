package io.github.michelbr84.flapforge.input;

import java.util.HashMap;
import java.util.Map;

/**
 * Integer key codes used by the input layer, numerically identical to the AWT {@code KeyEvent}
 * {@code VK_*} constants so the bridge can pass {@code getKeyCode()} through unchanged. This class
 * lets the pure packages talk about keys without importing any windowing toolkit.
 *
 * <p>Mouse button ids match {@code MouseEvent.BUTTON1..3}.
 */
public final class Keys {

    /** Enter (VK_ENTER). */
    public static final int ENTER = 10;
    /** Backspace (VK_BACK_SPACE). */
    public static final int BACK_SPACE = 8;
    /** Tab (VK_TAB). */
    public static final int TAB = 9;
    /** Shift (VK_SHIFT). */
    public static final int SHIFT = 16;
    /** Control (VK_CONTROL). */
    public static final int CONTROL = 17;
    /** Alt (VK_ALT). */
    public static final int ALT = 18;
    /** Escape (VK_ESCAPE). */
    public static final int ESCAPE = 27;
    /** Space (VK_SPACE). */
    public static final int SPACE = 32;
    /** Page up (VK_PAGE_UP). */
    public static final int PAGE_UP = 33;
    /** Page down (VK_PAGE_DOWN). */
    public static final int PAGE_DOWN = 34;
    /** End (VK_END). */
    public static final int END = 35;
    /** Home (VK_HOME). */
    public static final int HOME = 36;
    /** Left arrow (VK_LEFT). */
    public static final int LEFT = 37;
    /** Up arrow (VK_UP). */
    public static final int UP = 38;
    /** Right arrow (VK_RIGHT). */
    public static final int RIGHT = 39;
    /** Down arrow (VK_DOWN). */
    public static final int DOWN = 40;
    /** Digit 0 (VK_0); digits 1..9 follow consecutively. */
    public static final int DIGIT_0 = 48;
    /** Letter A (VK_A); B..Z follow consecutively. */
    public static final int A = 65;
    /** Letter M (VK_M). */
    public static final int M = 77;
    /** Letter X (VK_X). */
    public static final int X = 88;
    /** Letter Z (VK_Z). */
    public static final int Z = 90;
    /** Delete (VK_DELETE). */
    public static final int DELETE = 127;
    /** F1 (VK_F1); F2..F12 follow consecutively. */
    public static final int F1 = 112;
    /** F3 (VK_F3). */
    public static final int F3 = 114;
    /** F11 (VK_F11). */
    public static final int F11 = 122;
    /** F12 (VK_F12). */
    public static final int F12 = 123;
    /** Insert (VK_INSERT). */
    public static final int INSERT = 155;
    /** Undefined key (VK_UNDEFINED). */
    public static final int UNDEFINED = 0;

    /** Left mouse button (MouseEvent.BUTTON1). */
    public static final int BUTTON_LEFT = 1;
    /** Middle mouse button (MouseEvent.BUTTON2). */
    public static final int BUTTON_MIDDLE = 2;
    /** Right mouse button (MouseEvent.BUTTON3). */
    public static final int BUTTON_RIGHT = 3;

    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        NAMES.put(ENTER, "Enter");
        NAMES.put(BACK_SPACE, "Backspace");
        NAMES.put(TAB, "Tab");
        NAMES.put(SHIFT, "Shift");
        NAMES.put(CONTROL, "Ctrl");
        NAMES.put(ALT, "Alt");
        NAMES.put(ESCAPE, "Esc");
        NAMES.put(SPACE, "Space");
        NAMES.put(PAGE_UP, "Page Up");
        NAMES.put(PAGE_DOWN, "Page Down");
        NAMES.put(END, "End");
        NAMES.put(HOME, "Home");
        NAMES.put(LEFT, "Left");
        NAMES.put(UP, "Up");
        NAMES.put(RIGHT, "Right");
        NAMES.put(DOWN, "Down");
        NAMES.put(DELETE, "Delete");
        NAMES.put(INSERT, "Insert");
    }

    private Keys() {
    }

    /**
     * Human-readable name of a key code for the settings screen.
     *
     * @param code the key code
     * @return a short label such as {@code "Space"}, {@code "F3"} or {@code "Key 200"}
     */
    public static String name(int code) {
        String known = NAMES.get(code);
        if (known != null) {
            return known;
        }
        if (code >= A && code <= Z) {
            return String.valueOf((char) ('A' + (code - A)));
        }
        if (code >= DIGIT_0 && code <= DIGIT_0 + 9) {
            return String.valueOf((char) ('0' + (code - DIGIT_0)));
        }
        if (code >= F1 && code <= F12) {
            return "F" + (1 + code - F1);
        }
        return "Key " + code;
    }
}
