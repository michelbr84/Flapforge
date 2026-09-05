package jssound;

/**
 * android.media shim for the M10 build-time source transform
 * ({@code javax.sound.sampled.*} -> {@code jssound.*}).
 *
 * <p>Stand-in for {@code javax.sound.sampled.LineUnavailableException}: the platform could not
 * hand out or open an output line. Census: constructed in {@code SoftwareMixer.open()}
 * (audio/SoftwareMixer.java:210, "the line supplier returned no line"), declared by
 * {@code AudioBackend.open()} (:31), {@code SoftwareMixer.LineSupplier.get()} (:101) and thrown by
 * {@link AudioSystem#getSourceDataLine(AudioFormat)} / {@link SourceDataLine#open(AudioFormat,
 * int)}; caught in {@code AudioBackend.create} (:175) and {@code SoftwareMixer.open} (:215).
 *
 * <p>It extends {@link Exception} — not {@link RuntimeException} — exactly like AWT's: both
 * catch sites list it in a multi-catch beside unchecked alternatives, which would not compile
 * if the two were related by subclassing.
 */
public class LineUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a detail message (census: SoftwareMixer.java:210).
     *
     * @param message why no line is available
     */
    public LineUnavailableException(String message) {
        super(message);
    }
}
