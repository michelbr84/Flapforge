package jssound;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * In-code RIFF/WAVE fixtures for the jssound tests: no audio asset is copied into the test
 * tree, every byte is synthesised here. The builders are deliberately dumb so a test reads as
 * the container it describes: {@link #riff(byte[]...)} wraps chunks, {@link #chunk(String,
 * byte[])} pads to even length like the format requires, and the {@code fmt} builders emit the
 * three sizes the reader accepts.
 */
final class WaveFixtures {

    static final int TAG_PCM = 0x0001;
    static final int TAG_IEEE_FLOAT = 0x0003;
    static final int TAG_EXTENSIBLE = 0xFFFE;

    private WaveFixtures() {
    }

    /**
     * Interleaved 16-bit little-endian PCM of a sine; the second channel (if any) is a quarter
     * period ahead so stereo frames are distinguishable from doubled mono.
     */
    static byte[] sinePcm(int channels, int sampleRate, int frames, double hz) {
        byte[] pcm = new byte[frames * channels * 2];
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                double phase = 2 * Math.PI * hz * f / sampleRate + c * Math.PI / 2;
                short value = (short) Math.round(Math.sin(phase) * 12000);
                int at = (f * channels + c) * 2;
                pcm[at] = (byte) (value & 0xFF);
                pcm[at + 1] = (byte) ((value >> 8) & 0xFF);
            }
        }
        return pcm;
    }

    /** The canonical 44-byte header followed by the PCM bytes. */
    static byte[] canonical(int channels, int sampleRate, byte[] pcm) {
        return riff(fmt16(TAG_PCM, channels, sampleRate, 16), chunk("data", pcm));
    }

    static byte[] riff(byte[]... chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int size = 4;
        for (byte[] c : chunks) {
            size += c.length;
        }
        ascii(out, "RIFF");
        le32(out, size);
        ascii(out, "WAVE");
        for (byte[] c : chunks) {
            out.write(c, 0, c.length);
        }
        return out.toByteArray();
    }

    /** A chunk with its declared size equal to the body length, padded to an even length. */
    static byte[] chunk(String id, byte[] body) {
        return chunk(id, body, body.length);
    }

    /** A chunk whose header declares {@code declaredSize} regardless of the body written. */
    static byte[] chunk(String id, byte[] body, long declaredSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ascii(out, id);
        le32(out, (int) declaredSize);
        out.write(body, 0, body.length);
        if ((body.length & 1) != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    /** The 16-byte PCM fmt chunk. */
    static byte[] fmt16(int tag, int channels, int sampleRate, int bits) {
        return chunk("fmt ", fmtBody(tag, channels, sampleRate, bits));
    }

    /** The 18-byte fmt chunk ({@code cbSize == 0}) some encoders write for plain PCM. */
    static byte[] fmt18(int tag, int channels, int sampleRate, int bits) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] body = fmtBody(tag, channels, sampleRate, bits);
        out.write(body, 0, body.length);
        le16(out, 0);
        return chunk("fmt ", out.toByteArray());
    }

    /** The 40-byte WAVE_FORMAT_EXTENSIBLE fmt chunk with the given sub-format tag. */
    static byte[] fmt40(int channels, int sampleRate, int bits, int subFormatTag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] body = fmtBody(TAG_EXTENSIBLE, channels, sampleRate, bits);
        out.write(body, 0, body.length);
        le16(out, 22); // cbSize
        le16(out, bits); // valid bits per sample
        le32(out, channels == 1 ? 0x4 : 0x3); // channel mask
        le16(out, subFormatTag);
        byte[] tail = {
            0x00, 0x00, 0x00, 0x00, 0x10, 0x00, (byte) 0x80, 0x00,
            0x00, (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71
        };
        out.write(tail, 0, tail.length);
        return chunk("fmt ", out.toByteArray());
    }

    private static byte[] fmtBody(int tag, int channels, int sampleRate, int bits) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int blockAlign = channels * ((bits + 7) / 8);
        le16(out, tag);
        le16(out, channels);
        le32(out, sampleRate);
        le32(out, sampleRate * blockAlign);
        le16(out, blockAlign);
        le16(out, bits);
        return out.toByteArray();
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    static void ascii(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    static void le16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    static void le32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
}
