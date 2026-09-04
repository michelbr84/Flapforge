package io.github.michelbr84.flapforge.tools;

import io.github.michelbr84.flapforge.render.ProceduralArt;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Exports the procedural application icon for {@code jpackage} (E9, M9): the same vector shape
 * {@link ProceduralArt#drawIcon} paints on the window, rendered at each size from the vector form
 * — never by scaling a bitmap — and wrapped in the two container formats the per-OS packaging
 * needs.
 *
 * <pre>
 * ./gradlew iconExport
 * ./gradlew iconExport -PtoolArgs="--dest path/to/dir"
 * </pre>
 *
 * <p>The tool writes three files (default directory {@code build/icon}):
 *
 * <ul>
 *   <li>{@code flapforge.png} — the 256×256 master PNG (Linux {@code jpackage --icon});</li>
 *   <li>{@code flapforge.ico} — a PNG-in-ICO container with 16/32/48/256 entries (Windows
 *       {@code jpackage --icon});</li>
 *   <li>{@code flapforge.icns} — a PNG-in-ICNS container with the ic07/ic08/ic09/ic10 types,
 *       i.e. the 128/256/512/1024 renders (macOS {@code jpackage --icon}).</li>
 * </ul>
 *
 * <p>{@code scripts/package.sh} and {@code .github/workflows/release.yml} pass the per-OS file to
 * {@code jpackage --icon}. The containers are hand-written byte assemblies (little-endian ICO,
 * big-endian ICNS) so the tools source set needs no new dependency, and
 * {@code IconExportTest} parses them back independently of this writer.
 *
 * <p>{@code System.exit} is not used: D4 reserves it for the shutdown watchdog, so a failure
 * travels as a {@link ToolFailure}.
 */
public final class IconExport {

    /** Side length of the master PNG. */
    public static final int MASTER_SIZE = 256;
    /** Entry sizes of the ICO container, smallest first (E9). */
    public static final List<Integer> ICO_SIZES = List.of(16, 32, 48, 256);
    /** ICNS chunk types, in the order they are written (E9). */
    public static final List<String> ICNS_TYPES = List.of("ic07", "ic08", "ic09", "ic10");
    /** Pixel size each ICNS type stands for: ic07=128, ic08=256, ic09=512, ic10=1024. */
    public static final List<Integer> ICNS_SIZES = List.of(128, 256, 512, 1024);
    /** Names of the three written files, in {@link #export} order. */
    public static final List<String> FILE_NAMES = List.of("flapforge.png", "flapforge.ico",
            "flapforge.icns");
    /** Default output directory when {@code --dest} is not given. */
    public static final String DEFAULT_DEST = "build/icon";

    /** Exit status when every file was written. */
    public static final int EXIT_OK = 0;
    /** Exit status for a usage error or an unwritable output directory. */
    public static final int EXIT_PROBLEM = 1;

    private IconExport() {
    }

    /**
     * Runs the export.
     *
     * @param args {@code --dest DIR} to write somewhere other than {@code build/icon},
     *     {@code --quiet} to print only the file list, {@code --help} for the usage text
     */
    public static void main(String[] args) {
        Path dest = Path.of(DEFAULT_DEST);
        boolean quiet = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--quiet":
                    quiet = true;
                    break;
                case "--dest":
                    if (i + 1 >= args.length) {
                        System.err.println("--dest needs a path");
                        System.err.println(usage());
                        throw new ToolFailure(EXIT_PROBLEM);
                    }
                    dest = Path.of(args[++i]);
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
        if (!quiet) {
            System.out.println("Rendering the icon from the vector form (" + MASTER_SIZE + "x"
                    + MASTER_SIZE + " master, ICO " + ICO_SIZES + ", ICNS " + ICNS_TYPES + ")");
        }
        List<Path> written;
        try {
            written = export(dest);
        } catch (IOException e) {
            System.err.println("Cannot write the icons into " + dest + ": " + e.getMessage());
            throw new ToolFailure(EXIT_PROBLEM);
        }
        for (int i = 0; i < written.size(); i++) {
            System.out.println("  " + written.get(i) + "  " + sizeOf(written.get(i)) + " bytes");
        }
        System.out.println("Icon export: OK — " + written.size() + " file(s) in " + dest + ".");
    }

    /**
     * Renders and writes the three icon files into a directory, creating it when missing.
     *
     * @param dir the output directory
     * @return the written files in {@link #FILE_NAMES} order
     * @throws IOException when a file cannot be rendered or written
     */
    public static List<Path> export(Path dir) throws IOException {
        Files.createDirectories(dir);
        List<Path> written = new ArrayList<>(FILE_NAMES.size());
        written.add(write(dir, FILE_NAMES.get(0), pngBytes(MASTER_SIZE)));
        written.add(write(dir, FILE_NAMES.get(1), icoBytes()));
        written.add(write(dir, FILE_NAMES.get(2), icnsBytes()));
        return List.copyOf(written);
    }

    /**
     * Renders the icon at one size straight from the vector form and PNG-encodes it.
     *
     * @param size the side length in pixels
     * @return the PNG bytes
     * @throws IOException when no PNG writer is registered
     */
    public static byte[] pngBytes(int size) throws IOException {
        BufferedImage image = ProceduralArt.icon(size);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("no PNG writer is registered with ImageIO");
        }
        return out.toByteArray();
    }

    /**
     * Builds the ICO container: a 6-byte header, one 16-byte directory entry per size
     * ({@link #ICO_SIZES}, little-endian throughout) and then the PNG data of each entry. A 256
     * pixel side is stored in the directory as byte 0, per the format.
     *
     * @return the ICO bytes
     * @throws IOException when a size cannot be rendered
     */
    public static byte[] icoBytes() throws IOException {
        List<byte[]> images = new ArrayList<>(ICO_SIZES.size());
        for (int size : ICO_SIZES) {
            images.add(pngBytes(size));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u16le(out, 0);          // reserved
        u16le(out, 1);          // 1 = icon (2 would be a cursor)
        u16le(out, images.size());
        int offset = 6 + 16 * images.size();
        for (int i = 0; i < images.size(); i++) {
            int size = ICO_SIZES.get(i);
            out.write(size >= 256 ? 0 : size);  // width, 0 meaning 256
            out.write(size >= 256 ? 0 : size);  // height, 0 meaning 256
            out.write(0);                       // colours in the palette (none: PNG entry)
            out.write(0);                       // reserved
            u16le(out, 1);                      // colour planes
            u16le(out, 32);                     // bits per pixel
            u32le(out, images.get(i).length);
            u32le(out, offset);
            offset += images.get(i).length;
        }
        for (byte[] image : images) {
            out.writeBytes(image);
        }
        return out.toByteArray();
    }

    /**
     * Builds the ICNS container: the {@code icns} magic, a big-endian total length and one chunk
     * per type ({@link #ICNS_TYPES} / {@link #ICNS_SIZES}), each an 8-byte header (type plus
     * big-endian chunk length, header included) followed by the PNG data at that type's size.
     *
     * @return the ICNS bytes
     * @throws IOException when a size cannot be rendered
     */
    public static byte[] icnsBytes() throws IOException {
        List<byte[]> images = new ArrayList<>(ICNS_TYPES.size());
        for (int size : ICNS_SIZES) {
            images.add(pngBytes(size));
        }
        int total = 8;
        for (byte[] image : images) {
            total += 8 + image.length;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{'i', 'c', 'n', 's'});
        u32be(out, total);
        for (int i = 0; i < images.size(); i++) {
            byte[] type = ICNS_TYPES.get(i).getBytes(StandardCharsets.US_ASCII);
            out.writeBytes(type);
            u32be(out, 8 + images.get(i).length);
            out.writeBytes(images.get(i));
        }
        return out.toByteArray();
    }

    private static Path write(Path dir, String name, byte[] bytes) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private static void u16le(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void u32le(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void u32be(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /**
     * The usage text.
     *
     * @return the text
     */
    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: iconExport [--dest DIR] [--quiet]",
                "",
                "  --dest DIR  write the icons here (default " + DEFAULT_DEST + ")",
                "  --quiet     print only the written files",
                "  --help      print this text",
                "",
                "Writes flapforge.png (" + MASTER_SIZE + "x" + MASTER_SIZE + "), flapforge.ico",
                "(16/32/48/256 as PNG-in-ICO) and flapforge.icns (ic07/ic08/ic09/ic10 as",
                "PNG-in-ICNS), each rendered from the vector form. jpackage consumes them",
                "through scripts/package.sh and the release workflow (E9).");
    }

    /**
     * Thrown instead of calling {@code System.exit}, which D4 reserves for the shutdown watchdog:
     * it fails the Gradle task and leaves an embedding JVM alive.
     */
    static final class ToolFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final int status;

        ToolFailure(int status) {
            super("icon export finished with status " + status);
            this.status = status;
        }

        int status() {
            return status;
        }
    }
}
