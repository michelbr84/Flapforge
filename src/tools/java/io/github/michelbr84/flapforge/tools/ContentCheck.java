package io.github.michelbr84.flapforge.tools;

import io.github.michelbr84.flapforge.content.ContentException;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.ContentLoader;
import io.github.michelbr84.flapforge.content.ContentValidator;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.UnlockGraph;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TreeDef;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import java.util.List;

/**
 * Loads the shipped {@code data/*.json}, runs the full {@link ContentValidator} and prints the
 * {@link UnlockGraph} (milestone M4).
 *
 * <pre>
 * ./gradlew contentCheck
 * ./gradlew contentCheck -PtoolArgs="--quiet"
 * </pre>
 *
 * <p>It is the one place that sees the content the way a player's install does: every file, every
 * cross-reference, every unlock edge and every string key. The run fails — which fails the Gradle
 * task — on any validation error; warnings are printed and do not fail. {@code System.exit} is
 * not used: D4 reserves it for the shutdown watchdog, so the status travels as a
 * {@link ToolFailure}.
 */
public final class ContentCheck {

    /** Exit status when the content is sound. */
    public static final int EXIT_OK = 0;
    /** Exit status for a usage error. */
    public static final int EXIT_USAGE = 1;
    /** Exit status when the content is broken. */
    public static final int EXIT_PROBLEM = 2;

    private ContentCheck() {
    }

    /**
     * Runs the check.
     *
     * @param args {@code --quiet} to skip the graph, {@code --graph-only} to skip the summary,
     *     {@code --help} for the usage text
     */
    public static void main(String[] args) {
        boolean quiet = false;
        boolean graphOnly = false;
        for (String arg : args) {
            switch (arg) {
                case "--quiet":
                    quiet = true;
                    break;
                case "--graph-only":
                    graphOnly = true;
                    break;
                case "--help":
                case "-h":
                    System.out.println(usage());
                    return;
                default:
                    System.err.println("Unknown option: " + arg);
                    System.err.println(usage());
                    throw new ToolFailure(EXIT_USAGE);
            }
        }
        GameContent content;
        try {
            content = GameContent.fromJson(ContentLoader.loadAll(ContentLoader.FILES));
        } catch (ContentException e) {
            System.out.println("Content check: FAILED to load " + ContentLoader.FILES);
            for (String error : e.errors()) {
                System.out.println("  ERROR  " + error);
            }
            System.out.println();
            System.out.println(e.errors().size() + " error(s).");
            throw new ToolFailure(EXIT_PROBLEM);
        }

        List<String> errors = ContentValidator.errorsOf(content);
        List<String> warnings = ContentValidator.warningsOf(content);
        ContentValidator.StringReport strings = ContentValidator.checkStrings(content);

        if (!graphOnly) {
            printSummary(content);
        }
        for (String error : errors) {
            System.out.println("  ERROR  " + error);
        }
        for (String error : strings.errors()) {
            System.out.println("  ERROR  " + error);
        }
        for (String warning : warnings) {
            System.out.println("  WARN   " + warning);
        }
        for (String warning : strings.warnings()) {
            System.out.println("  WARN   " + warning);
        }
        if (!quiet) {
            System.out.println();
            System.out.println(UnlockGraph.of(content).render());
        }
        int total = errors.size() + strings.errors().size();
        int warned = warnings.size() + strings.warnings().size();
        System.out.println("Content check: " + (total == 0 ? "OK" : "FAILED") + " — " + total
                + " error(s), " + warned + " warning(s).");
        if (total > 0) {
            throw new ToolFailure(EXIT_PROBLEM);
        }
    }

    private static void printSummary(GameContent content) {
        System.out.println("Files: " + content.files());
        System.out.println();
        System.out.println("Birds (" + content.birds().size() + "):");
        for (BirdDef bird : content.birds()) {
            StringBuilder palettes = new StringBuilder();
            for (PaletteDef palette : bird.palettes()) {
                palettes.append(palettes.length() == 0 ? "" : ", ").append(palette.id());
            }
            System.out.println("  " + pad(bird.id()) + " " + bird.archetype() + ", slots "
                    + bird.passiveSlots() + ", palettes [" + palettes + "]");
        }
        System.out.println();
        System.out.println("Upgrade trees (" + content.trees().size() + ") and nodes ("
                + content.upgrades().size() + "):");
        for (TreeDef tree : content.trees()) {
            System.out.println("  " + pad(tree.id()) + " "
                    + UnlockGraph.describe(tree.unlock()));
            for (UpgradeDef node : content.upgrades()) {
                if (node.tree().equals(tree.id())) {
                    System.out.println("    tier " + node.tier() + "  " + pad(node.id())
                            + " maxLevel " + node.maxLevel() + ", costs " + node.costs()
                            + (node.prereqs().isEmpty() ? "" : ", after " + node.prereqs())
                            + (node.grants().isEmpty() ? "" : ", grants " + node.grants()));
                }
            }
        }
        System.out.println();
        System.out.println("Abilities (" + content.abilities().size() + ", playable: "
                + content.playable(ContentKind.ABILITY) + "):");
        for (AbilityDef ability : content.abilities()) {
            System.out.println("  " + pad(ability.id()) + " " + ability.kind() + " "
                    + ability.tags() + ", levels " + ability.levels().size());
        }
        System.out.println();
        System.out.println("Worlds (" + content.worlds().size() + "):");
        for (WorldDef world : content.worlds()) {
            String boss = world.boss() == null || world.boss().reward() == null ? "no boss"
                    : "boss at gate " + world.boss().atGate() + " pays "
                            + world.boss().reward().coins() + " coins "
                            + world.boss().reward().unlocks();
            System.out.println("  " + pad(world.id()) + " order " + world.order() + ", curve "
                    + world.curve() + ", playable: "
                    + content.playable(ContentKind.WORLD, world.id()) + ", " + boss);
        }
        System.out.println();
        System.out.println("Challenges (" + content.challenges().size() + ", playable: "
                + content.playable(ContentKind.CHALLENGE) + "):");
        for (ChallengeDef challenge : content.challenges()) {
            System.out.println("  " + pad(challenge.id()) + " " + challenge.world() + " / "
                    + challenge.curve() + ", " + challenge.objective().type() + " "
                    + challenge.objective().value() + ", pays "
                    + challenge.rewardsOrNone().coins() + " coins "
                    + challenge.rewardsOrNone().unlocks());
        }
        System.out.println();
        System.out.println("Achievements: " + content.achievements().size() + " (playable: "
                + content.playable(ContentKind.ACHIEVEMENT) + ")");
        System.out.println("Features: " + content.features().ids());
        System.out.println("Tiers: " + content.tiers().ids());
        System.out.println("Aliases: version " + content.aliases().version()
                + (content.aliases().isEmpty() ? " (empty)" : ""));
        System.out.println();
    }

    private static String pad(String id) {
        return id.length() >= 20 ? id : id + " ".repeat(20 - id.length());
    }

    /**
     * The usage text.
     *
     * @return the text
     */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: contentCheck [--quiet] [--graph-only]",
                "",
                "  --quiet       do not print the unlock graph",
                "  --graph-only  print only the problems and the unlock graph",
                "  --help        print this text",
                "",
                "The run fails when the shipped content breaks a validator rule.");
    }

    /**
     * Thrown instead of calling {@code System.exit}, which D4 reserves for the shutdown watchdog:
     * it fails the Gradle task and leaves an embedding JVM alive.
     */
    static final class ToolFailure extends RuntimeException {

        private final int status;

        ToolFailure(int status) {
            super("content check finished with status " + status);
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}
