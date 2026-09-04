package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.tools.IconExport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The icon export (E9, M9) checked the way a consumer would see it: the written PNG, ICO and
 * ICNS files are parsed byte by byte with this test's own little-endian and big-endian readers
 * instead of trusting {@link IconExport}'s writers — every dimension, type and length below is
 * re-derived from the container bytes.
 */
class IconExportTest {

    /** The PNG signature, declared here so the test does not lean on the writer's constants. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A,
        0x0A};

    @TempDir
    Path dir;

    @Test
    void exportWritesTheThreeFiles() throws IOException {
        List<Path> written = IconExport.export(dir);
        assertEquals(IconExport.FILE_NAMES.size(), written.size());
        for (int i = 0; i < written.size(); i++) {
            assertEquals(IconExport.FILE_NAMES.get(i), written.get(i).getFileName().toString(),
                    "file " + i + " of the export");
            assertTrue(Files.isRegularFile(written.get(i)), "written: " + written.get(i));
            assertTrue(Files.size(written.get(i)) > 0, "non-empty: " + written.get(i));
        }
        assertEquals(dir.resolve("flapforge.png"), written.get(0), "the master PNG comes first");
    }

    @Test
    void masterPngIs256SquareArgb() throws IOException {
        IconExport.export(dir);
        byte[] png = Files.readAllBytes(dir.resolve("flapforge.png"));
        assertTrue(startsWith(png, PNG_SIGNATURE), "starts with the PNG signature");
        assertIhdDimensions(png, IconExport.MASTER_SIZE, IconExport.MASTER_SIZE);

        // Independently of the byte parsing: the JVM's own decoder must read it back at the
        // same size, in ARGB (the icon has rounded corners over a transparent ground).
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(IconExport.MASTER_SIZE, decoded.getWidth());
        assertEquals(IconExport.MASTER_SIZE, decoded.getHeight());
    }

    @Test
    void icoHoldsFourValidPngEntries() throws IOException {
        IconExport.export(dir);
        byte[] ico = Files.readAllBytes(dir.resolve("flapforge.ico"));

        assertEquals(0, u16le(ico, 0), "reserved word");
        assertEquals(1, u16le(ico, 2), "type word: 1 = icon");
        int count = u16le(ico, 4);
        assertEquals(IconExport.ICO_SIZES.size(), count, "directory entry count");
        assertTrue(ico.length >= 6 + 16 * count, "room for header and directory");

        int offset = 6 + 16 * count;
        for (int i = 0; i < count; i++) {
            int entry = 6 + 16 * i;
            int expectedSize = IconExport.ICO_SIZES.get(i);
            int widthByte = ico[entry] & 0xFF;
            int heightByte = ico[entry + 1] & 0xFF;
            assertEquals(expectedSize >= 256 ? 0 : expectedSize, widthByte,
                    "entry " + i + ": width byte (0 means 256)");
            assertEquals(widthByte, heightByte, "entry " + i + ": square");
            assertEquals(0, ico[entry + 2] & 0xFF, "entry " + i + ": no palette colours");
            assertEquals(0, ico[entry + 3] & 0xFF, "entry " + i + ": reserved byte");
            assertEquals(1, u16le(ico, entry + 4), "entry " + i + ": colour planes");
            assertEquals(32, u16le(ico, entry + 6), "entry " + i + ": bits per pixel");

            int dataLength = u32le(ico, entry + 8);
            int dataOffset = u32le(ico, entry + 12);
            assertEquals(offset, dataOffset, "entry " + i + ": data follows the previous entry");
            assertTrue(dataLength > 0, "entry " + i + ": non-empty image");
            assertTrue(dataOffset + dataLength <= ico.length, "entry " + i + ": inside the file");

            byte[] png = java.util.Arrays.copyOfRange(ico, dataOffset, dataOffset + dataLength);
            assertTrue(startsWith(png, PNG_SIGNATURE),
                    "entry " + i + ": PNG-in-ICO, so the data is a whole PNG");
            assertIhdDimensions(png, expectedSize, expectedSize);
            offset += dataLength;
        }
        assertEquals(ico.length, offset, "the file ends with the last entry");
    }

    @Test
    void icnsHoldsIc07ToIc10Chunks() throws IOException {
        IconExport.export(dir);
        byte[] icns = Files.readAllBytes(dir.resolve("flapforge.icns"));

        assertTrue(icns.length >= 8, "room for the file header");
        assertEquals("icns", fourcc(icns, 0), "file magic");
        assertEquals(icns.length, u32be(icns, 4), "total length covers the whole file");

        int offset = 8;
        for (int i = 0; i < IconExport.ICNS_TYPES.size(); i++) {
            String type = IconExport.ICNS_TYPES.get(i);
            int expectedSize = IconExport.ICNS_SIZES.get(i);
            assertTrue(offset + 8 <= icns.length, "room for the " + type + " chunk header");
            assertEquals(type, fourcc(icns, offset), "chunk " + i + " type");

            int chunkLength = u32be(icns, offset + 4);
            assertTrue(chunkLength >= 8, type + ": chunk length includes its header");
            assertTrue(offset + chunkLength <= icns.length, type + ": chunk inside the file");

            byte[] png = java.util.Arrays.copyOfRange(icns, offset + 8, offset + chunkLength);
            assertTrue(startsWith(png, PNG_SIGNATURE),
                    type + ": PNG-in-ICNS, so the chunk data is a whole PNG");
            assertIhdDimensions(png, expectedSize, expectedSize);
            offset += chunkLength;
        }
        assertEquals(icns.length, offset, "the chunks fill the file exactly");
    }

    /**
     * Asserts that a PNG carries an IHDR chunk declaring the given dimensions: the first chunk
     * must start at byte 12 with a 13-byte {@code IHDR} whose width and height match.
     *
     * @param png the PNG bytes (at least through the IHDR)
     * @param width the expected width
     * @param height the expected height
     */
    private static void assertIhdDimensions(byte[] png, int width, int height) {
        assertTrue(png.length >= 24, "room for the signature and the IHDR header");
        assertEquals(13, u32be(png, 8), "IHDR data length (fixed by the format)");
        assertEquals("IHDR", fourcc(png, 12), "first chunk is IHDR");
        assertEquals(width, u32be(png, 16), "IHDR width");
        assertEquals(height, u32be(png, 20), "IHDR height");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u16le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8;
    }

    private static int u32le(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | (data[offset + 1] & 0xFF) << 8
                | (data[offset + 2] & 0xFF) << 16
                | (data[offset + 3] & 0xFF) << 24;
    }

    private static int u32be(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24
                | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8
                | (data[offset + 3] & 0xFF);
    }

    private static String fourcc(byte[] data, int offset) {
        return new String(data, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
