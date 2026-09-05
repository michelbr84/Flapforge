package jssound;

/**
 * android.media shim for the M10 build-time source transform
 * ({@code javax.sound.sampled.*} -> {@code jssound.*}).
 *
 * <p>Stand-in for {@code javax.sound.sampled.UnsupportedAudioFileException}: the stream handed to
 * {@link AudioSystem#getAudioInputStream(java.io.InputStream)} is not a container the shim reads
 * (anything but RIFF/WAVE carrying 16-bit PCM). Census: declared by {@code SoundBank.decode}
 * (audio/SoundBank.java:311) and caught in {@code SoundBank.decodeOverride} (:270), which logs one
 * line and falls through to the synthesised sound.
 *
 * <p>It extends {@link Exception} — not {@link java.io.IOException} — exactly like AWT's: the
 * catch site at SoundBank.java:270 lists it in a multi-catch beside {@code IOException}, which
 * would not compile if the two were related by subclassing.
 */
public class UnsupportedAudioFileException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a detail message.
     *
     * @param message what the stream is, as far as the sniffer could tell
     */
    public UnsupportedAudioFileException(String message) {
        super(message);
    }
}
