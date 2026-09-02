package io.github.michelbr84.flapforge.tools;

import io.github.michelbr84.flapforge.render.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks {@code assets/manifest.json} the way a player's install sees it (D18, M7): every entry
 * must resolve on the classpath, carry a licence, and be the kind of file it claims to be.
 *
 * <pre>
 * ./gradlew assetValidator
 * ./gradlew assetValidator -PtoolArgs="--manifest path/to/manifest.json"
 * </pre>
 *
 * <p>The kind check reads the file's magic number rather than its extension: a {@code SPRITE}
 * or {@code SHEET} must start with the PNG signature, an {@code AUDIO} entry must be a RIFF/WAVE
 * container (the only one the game decodes, D19), a {@code FONT} must be a TrueType
 * ({@code 00 01 00 00} or {@code 'true'}) or OpenType ({@code 'OTTO'}) file. The shipped manifest
 * is empty and passes; the run fails — which fails the Gradle task with status 1 — on any
 * problem. {@code System.exit} is not used: D4 reserves it for the shutdown watchdog, so the
 * status travels as a {@link ToolFailure}.
 */
public final class AssetValidator {

    /** Exit status when every entry is sound. */
    public static final int EXIT_OK = 0;
    /** Exit status for a usage error or a broken manifest. */
    public static final int EXIT_PROBLEM = 1;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WAVE = {'W', 'A', 'V', 'E'};
    private static final byte[] TTF = {0x00, 0x01, 0x00, 0x00};
    private static final byte[] TRUE = {'t', 'r', 'u', 'e'};
    private static final byte[] OTTO = {'O', 'T', 'T', 'O'};

    private AssetValidator() {
    }

    /**
     * Runs the check.
     *
     * @param args {@code --manifest PATH} to check a file instead of the classpath manifest,
     *     {@code --quiet} to print only the problems, {@code --help} for the usage text
     */
    public static void main(String[] args) {
        Path manifestFile = null;
        boolean quiet = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--quiet":
                    quiet = true;
                    break;
                case "--manifest":
                    if (i + 1 >= args.length) {
                        System.err.println("--manifest needs a path");
                        System.err.println(usage());
                        throw new ToolFailure(EXIT_PROBLEM);
                    }
                    manifestFile = Path.of(args[++i]);
                    break;
                case "--help":
                case "-h":
                    System.out.println(usage());
                    return;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.err.println(usage());
                    throw new ToolFailure(EXIT_PROBLEM);
            }
        }
        AssetManager manager;
        String where;
        if (manifestFile == null) {
            manager = AssetManager.fromClasspath();
            where = AssetManager.MANIFEST_RESOURCE + " (classpath)";
        } else {
            try {
                manager = AssetManager.fromJson(Files.readString(manifestFile,
                        StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("Cannot read " + manifestFile + ": " + e.getMessage());
                throw new ToolFailure(EXIT_PROBLEM);
            }
            where = manifestFile.toString();
        }
        List<String> problems = check(manager);
        if (!quiet) {
            System.out.println("Manifest: " + where);
            System.out.println("Entries: " + manager.entries().size());
            for (AssetManager.Entry entry : manager.entries().values()) {
                System.out.println("  " + entry.id() + "  " + entry.kind() + "  " + entry.path()
                        + "  [" + (entry.license().isBlank() ? "no licence" : entry.license())
                        + "]");
            }
        }
        for (String problem : problems) {
            System.out.println("  ERROR  " + problem);
        }
        System.out.println("Asset check: " + (problems.isEmpty() ? "OK" : "FAILED") + " — "
                + problems.size() + " problem(s), " + manager.entries().size() + " entries.");
        if (!problems.isEmpty()) {
            throw new ToolFailure(EXIT_PROBLEM);
        }
    }

    /**
     * Every problem of a manifest: parse errors, unresolved paths, missing licences and kind
     * mismatches, in manifest order.
     *
     * @param manager the parsed manifest
     * @return the problems, empty when the manifest is sound
     */
    public static List<String> check(AssetManager manager) {
        List<String> problems = new ArrayList<>(manager.errors());
        for (Map.Entry<String, AssetManager.Entry> e : manager.entries().entrySet()) {
            AssetManager.Entry entry = e.getValue();
            String label = entry.id() + " (" + entry.path() + ")";
            if (entry.license() == null || entry.license().isBlank()) {
                problems.add(label + ": missing licence");
            }
            byte[] head = head(AssetManager.ASSET_ROOT + entry.path(), 12);
            if (head == null) {
                problems.add(label + ": not on the classpath under " + AssetManager.ASSET_ROOT);
                continue;
            }
            String mismatch = kindMismatch(entry.kind(), head);
            if (mismatch != null) {
                problems.add(label + ": " + mismatch);
            }
        }
        return problems;
    }

    /**
     * Whether the first bytes of a file match what a kind requires.
     *
     * @param kind the declared kind
     * @param head at least the first 12 bytes of the file (fewer when the file is shorter)
     * @return the problem, or {@code null} when the bytes fit the kind
     */
    public static String kindMismatch(AssetManager.Kind kind, byte[] head) {
        switch (kind) {
            case SPRITE:
            case SHEET:
                return startsWith(head, PNG, 0) ? null : "declared " + kind + " but not a PNG";
            case AUDIO:
                return startsWith(head, RIFF, 0) && startsWith(head, WAVE, 8) ? null
                        : "declared AUDIO but not a RIFF/WAVE file";
            case FONT:
                return startsWith(head, TTF, 0) || startsWith(head, TRUE, 0)
                        || startsWith(head, OTTO, 0) ? null
                        : "declared FONT but not a TrueType/OpenType file";
            default:
                return "unknown kind " + kind;
        }
    }

    private static boolean startsWith(byte[] data, byte[] magic, int offset) {
        if (data.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first bytes of a classpath resource.
     *
     * @param resource the absolute resource name
     * @param count how many bytes to read
     * @return the bytes read (possibly fewer than asked), or {@code null} when the resource is
     *     missing or unreadable
     */
    private static byte[] head(String resource, int count) {
        try (InputStream in = AssetValidator.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            byte[] buffer = new byte[count];
            int read = 0;
            while (read < count) {
                int n = in.read(buffer, read, count - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            byte[] out = new byte[read];
            System.arraycopy(buffer, 0, out, 0, read);
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The usage text.
     *
     * @return the text
     */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: assetValidator [--manifest PATH] [--quiet]",
                "",
                "  --manifest PATH  check this manifest file instead of the classpath one",
                "  --quiet          print only the problems",
                "  --help           print this text",
                "",
                "Every entry must resolve on the classpath under /assets/, carry a licence and",
                "start with the magic number of its kind (PNG, RIFF/WAVE, TrueType/OpenType).",
                "The run fails with status 1 on any problem.");
    }

    /**
     * Thrown instead of calling {@code System.exit}, which D4 reserves for the shutdown watchdog:
     * it fails the Gradle task and leaves an embedding JVM alive.
     */
    static final class ToolFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final int status;

        ToolFailure(int status) {
            super("asset check finished with status " + status);
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}
