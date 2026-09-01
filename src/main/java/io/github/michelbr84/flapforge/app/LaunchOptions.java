package io.github.michelbr84.flapforge.app;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Command-line options (§8). Parsed before any windowing class loads so {@code --headless-run}
 * and {@code --no-window} can switch the JVM to headless mode first (E10).
 *
 * @param seed run seed, or {@code null} for random
 * @param world world id override, or {@code null}
 * @param bird bird id override, or {@code null}
 * @param tier tier id override, or {@code null}
 * @param scale integer window scale override, or {@code null} for automatic
 * @param fullscreen start in borderless fullscreen
 * @param noAudio disable audio output
 * @param home profile directory override, or {@code null}
 * @param headlessRun number of frames to simulate without a window (0 = normal launch)
 * @param resetSave delete the save file before starting
 * @param lang language override ({@code en}, {@code pt_BR}), or {@code null} for auto
 * @param noWindow do not open a window (implies headless)
 * @param help print usage and exit
 */
public record LaunchOptions(Long seed, String world, String bird, String tier, Integer scale,
        boolean fullscreen, boolean noAudio, Path home, int headlessRun, boolean resetSave,
        String lang, boolean noWindow, boolean help) {

    /** Options with every flag at its default. */
    public static final LaunchOptions DEFAULTS = new LaunchOptions(null, null, null, null, null,
            false, false, null, 0, false, null, false, false);

    /** Thrown for unknown flags or malformed values; the message is user facing. */
    public static final class UsageException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Creates the exception.
         *
         * @param message the user-facing message
         */
        public UsageException(String message) {
            super(message);
        }
    }

    /**
     * Whether the launch must run without a window.
     *
     * @return {@code true} for {@code --headless-run N} or {@code --no-window}
     */
    public boolean headless() {
        return headlessRun > 0 || noWindow;
    }

    /**
     * Parses command-line arguments. Both {@code --flag value} and {@code --flag=value} forms
     * are accepted.
     *
     * @param args the arguments
     * @return the options
     * @throws UsageException for an unknown flag, a missing or malformed value
     */
    public static LaunchOptions parse(String[] args) throws UsageException {
        Objects.requireNonNull(args, "args");
        Long seed = null;
        String world = null;
        String bird = null;
        String tier = null;
        Integer scale = null;
        boolean fullscreen = false;
        boolean noAudio = false;
        Path home = null;
        int headlessRun = 0;
        boolean resetSave = false;
        String lang = null;
        boolean noWindow = false;
        boolean help = false;

        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            String name = arg;
            String inlineValue = null;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                name = arg.substring(0, eq);
                inlineValue = arg.substring(eq + 1);
            }
            switch (name) {
                case "--help", "-h" -> help = true;
                case "--fullscreen" -> fullscreen = true;
                case "--no-audio" -> noAudio = true;
                case "--reset-save" -> resetSave = true;
                case "--no-window" -> noWindow = true;
                case "--seed" -> {
                    String v = value(args, i, inlineValue, name);
                    seed = parseLong(name, v);
                    i += inlineValue == null ? 1 : 0;
                }
                case "--world" -> {
                    world = value(args, i, inlineValue, name);
                    i += inlineValue == null ? 1 : 0;
                }
                case "--bird" -> {
                    bird = value(args, i, inlineValue, name);
                    i += inlineValue == null ? 1 : 0;
                }
                case "--tier" -> {
                    tier = value(args, i, inlineValue, name);
                    i += inlineValue == null ? 1 : 0;
                }
                case "--scale" -> {
                    String v = value(args, i, inlineValue, name);
                    int s = (int) parseLong(name, v);
                    if (s < 1 || s > 8) {
                        throw new UsageException("--scale must be between 1 and 8, got " + v);
                    }
                    scale = s;
                    i += inlineValue == null ? 1 : 0;
                }
                case "--home" -> {
                    home = Path.of(value(args, i, inlineValue, name));
                    i += inlineValue == null ? 1 : 0;
                }
                case "--headless-run" -> {
                    String v = value(args, i, inlineValue, name);
                    long n = parseLong(name, v);
                    if (n < 1 || n > Integer.MAX_VALUE) {
                        throw new UsageException("--headless-run needs a positive frame count, got "
                                + v);
                    }
                    headlessRun = (int) n;
                    i += inlineValue == null ? 1 : 0;
                }
                case "--lang" -> {
                    lang = value(args, i, inlineValue, name);
                    i += inlineValue == null ? 1 : 0;
                }
                default -> throw new UsageException("Unknown option: " + arg);
            }
            i++;
        }
        return new LaunchOptions(seed, world, bird, tier, scale, fullscreen, noAudio, home,
                headlessRun, resetSave, lang, noWindow, help);
    }

    private static String value(String[] args, int index, String inlineValue, String name)
            throws UsageException {
        if (inlineValue != null) {
            if (inlineValue.isEmpty()) {
                throw new UsageException(name + " needs a value");
            }
            return inlineValue;
        }
        if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
            throw new UsageException(name + " needs a value");
        }
        return args[index + 1];
    }

    private static long parseLong(String name, String value) throws UsageException {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new UsageException(name + " needs an integer, got " + value);
        }
    }

    /**
     * Usage text printed for {@code --help} and on errors.
     *
     * @return the multi-line usage text
     */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: flapforge [options]",
                "  --seed N            run seed (default: random)",
                "  --world ID          start in the given world",
                "  --bird ID           start with the given bird",
                "  --tier ID           start with the given difficulty tier",
                "  --scale N           integer window scale (1-8; default: largest that fits the screen)",
                "  --fullscreen        start in borderless fullscreen",
                "  --no-audio          disable audio",
                "  --home PATH         profile directory override",
                "  --headless-run N    simulate N frames without a window and print a summary",
                "  --reset-save        delete the save file before starting",
                "  --lang CODE         language (en, pt_BR)",
                "  --no-window         do not open a window",
                "  --help, -h          print this help");
    }
}
