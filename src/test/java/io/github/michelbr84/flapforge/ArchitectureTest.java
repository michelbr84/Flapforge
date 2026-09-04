package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Source-scanning purity guard (D4, D5, E30.c): the pure packages must not touch the windowing
 * toolkit, wall clocks, threads or platform-dependent math; nothing in any source set may use
 * Swing; and {@code System.exit} exists only in the shutdown watchdog.
 */
class ArchitectureTest {

    private static final Path SOURCES = Path.of("src");
    private static final Path MAIN_SOURCES = SOURCES.resolve(Path.of("main", "java"));
    private static final String ROOT = "io/github/michelbr84/flapforge/";
    private static final List<String> PURE_PACKAGES = List.of("core", "input", "gameplay", "ability",
            "modifier", "content", "progression", "persistence");
    private static final List<String> BUS_FREE_PACKAGES = List.of("gameplay", "progression");
    private static final String WATCHDOG_OWNER = ROOT + "app/GameApplication.java";

    private record Ban(String label, Pattern pattern) {
        static Ban literal(String text) {
            return new Ban(text, Pattern.compile(Pattern.quote(text)));
        }

        static Ban regex(String label, String regex) {
            return new Ban(label, Pattern.compile(regex));
        }
    }

    private static final List<Ban> BANS = List.of(
            Ban.literal("java.awt"),
            Ban.literal("javax."),
            Ban.literal("sun."),
            Ban.literal("Math.random"),
            Ban.literal("System.currentTimeMillis"),
            Ban.literal("System.nanoTime"),
            Ban.regex("java.time now()",
                    "\\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\\.now\\("),
            Ban.literal("Clock.system"),
            Ban.regex("new Random() without a seed", "new\\s+Random\\s*\\(\\s*\\)"),
            Ban.literal("ThreadLocalRandom"),
            Ban.literal("Thread."),
            Ban.regex("new Thread(", "new\\s+Thread\\s*\\("),
            Ban.literal("Executors."),
            Ban.literal("CompletableFuture"),
            Ban.literal("ForkJoinPool"),
            Ban.regex("static import of java.lang.Math", "import\\s+static\\s+java\\.lang\\.Math\\."),
            Ban.regex("platform-dependent Math function (use StrictMath or a LUT)",
                    "(?<![A-Za-z0-9_$])Math\\.(sin|cos|tan|atan2?|asin|acos|sinh|cosh|tanh|exp|expm1"
                            + "|pow|log|log10|log1p|cbrt)\\("));

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\r\\n]*");
    private static final Pattern EVENT_IMPORT = Pattern.compile(
            "import\\s+io\\.github\\.michelbr84\\.flapforge\\.event\\.");
    private static final Pattern SYSTEM_EXIT = Pattern.compile("System\\s*\\.\\s*exit\\s*\\(");

    @Test
    void pureSourcesExist() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES), "run from the project root");
        List<Path> pure = pureSources();
        assertFalse(pure.isEmpty(), "no pure sources found under " + MAIN_SOURCES);
    }

    @Test
    void purePackagesDoNotUseBannedApis() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : pureSources()) {
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            for (Ban ban : BANS) {
                Matcher m = ban.pattern().matcher(code);
                if (m.find()) {
                    violations.add(relative(file) + ": " + ban.label() + " at offset " + m.start());
                }
            }
            String pkg = packageOf(file);
            if (BUS_FREE_PACKAGES.contains(pkg) && EVENT_IMPORT.matcher(code).find()) {
                violations.add(relative(file) + ": imports the event package");
            }
        }
        assertTrue(violations.isEmpty(), () -> "Purity violations:\n" + String.join("\n", violations));
    }

    @Test
    void mathBanHasALeftBoundaryAndCatchesStaticImports() {
        Ban math = BANS.get(BANS.size() - 1);
        assertFalse(math.pattern().matcher("double y = StrictMath.sin(x);").find(),
                "StrictMath is the prescribed replacement and must pass");
        assertTrue(math.pattern().matcher("double y = Math.sin(x);").find());
        assertTrue(math.pattern().matcher("return 2 *Math.pow(a, b);").find());
        assertTrue(math.pattern().matcher("Math.sqrt(x) + Math.hypot(a, b) + Math.floor(y)")
                .results().findAny().isEmpty(), "sqrt/hypot/floor are allowed");
        Ban staticImport = BANS.get(BANS.size() - 2);
        assertTrue(staticImport.pattern().matcher("import static java.lang.Math.sin;").find());
        assertTrue(staticImport.pattern().matcher("import static java.lang.Math.*;").find());
    }

    @Test
    void noSwingInAnySourceSet() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path self = Path.of("src", "test", "java").resolve(ROOT).resolve("ArchitectureTest.java");
        for (Path file : allSources()) {
            if (file.normalize().endsWith(self.normalize())) {
                continue;
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.contains("javax.swing")) {
                offenders.add(SOURCES.relativize(file).toString().replace('\\', '/'));
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Swing references:\n" + String.join("\n", offenders));
    }

    @Test
    void systemExitOnlyInTheShutdownWatchdog() throws IOException {
        List<String> offenders = new ArrayList<>();
        boolean watchdogSeen = false;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(ArchitectureTest::isJava)::iterator) {
                String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
                if (!SYSTEM_EXIT.matcher(code).find()) {
                    continue;
                }
                if (relative(file).equals(WATCHDOG_OWNER)) {
                    watchdogSeen = true;
                } else {
                    offenders.add(relative(file));
                }
            }
        }
        assertTrue(watchdogSeen, "the watchdog in " + WATCHDOG_OWNER + " is the one allowed exit");
        assertTrue(offenders.isEmpty(), () -> "System.exit outside the watchdog:\n"
                + String.join("\n", offenders));
    }

    private static List<Path> pureSources() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(ArchitectureTest::isJava)::iterator) {
                if (PURE_PACKAGES.contains(packageOf(file))) {
                    out.add(file);
                }
            }
        }
        return out;
    }

    /** Every Java file under {@code src} (main, test and future tools source sets). */
    private static List<Path> allSources() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(ArchitectureTest::isJava)::iterator) {
                out.add(file);
            }
        }
        return out;
    }

    private static boolean isJava(Path p) {
        return Files.isRegularFile(p) && p.toString().endsWith(".java");
    }

    private static String relative(Path file) {
        return MAIN_SOURCES.relativize(file).toString().replace('\\', '/');
    }

    /** First path segment after the package root, or {@code ""} for the root package. */
    private static String packageOf(Path file) {
        String rel = relative(file);
        if (!rel.startsWith(ROOT)) {
            return "";
        }
        String rest = rel.substring(ROOT.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? "" : rest.substring(0, slash);
    }

    private static String stripComments(String code) {
        String noBlocks = BLOCK_COMMENT.matcher(code).replaceAll(" ");
        return LINE_COMMENT.matcher(noBlocks).replaceAll(" ");
    }
}
