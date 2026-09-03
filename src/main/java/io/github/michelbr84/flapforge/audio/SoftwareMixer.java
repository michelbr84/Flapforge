package io.github.michelbr84.flapforge.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * The real audio output: one pre-opened {@link SourceDataLine} and one daemon thread that sums
 * every active {@link Voice} into it (D19).
 *
 * <p>Design in one paragraph. Opening a line is slow and the number of lines a machine offers is
 * small, so the mixer opens exactly one — 44.1 kHz, 16-bit, stereo, a {@value #BUFFER_MS} ms
 * buffer — and keeps it for the life of the process; sounds are software-mixed into it rather
 * than each grabbing hardware. The game loop never touches the line, never allocates a clip and
 * never waits: {@link #play(String, float, float)} only offers a small command to a bounded queue
 * ({@value #COMMAND_CAPACITY} entries) and returns, and a full queue drops the request instead of
 * blocking, because a dropped blip is invisible while a stalled loop is a dropped frame. The
 * mixing thread drains commands, mixes, encodes and writes; the write itself is what paces the
 * loop, since the line accepts data no faster than it plays it.
 *
 * <p>Everything expensive happens off the mixing path. {@link SoundBank} does mono-to-stereo
 * widening and sample-rate conversion once, at load time, so the inner loop is two multiply-adds
 * per voice per frame. The summed buffer goes through a soft limiter with a knee at
 * {@value #LIMITER_KNEE}: below it nothing is touched, above it the signal is compressed with
 * {@code tanh} so a burst of simultaneous sounds saturates smoothly and can never exceed full
 * scale, where hard clipping would buzz.
 *
 * <p>Thread ownership: {@link #play}, {@link #stopAll}, {@link #setMasterGain} and
 * {@link #warmUp} are callable from any thread (the loop, in practice); the voice list and the
 * mixing buffers belong to the mixing thread alone. The thread comes from an injected
 * {@link ThreadFactory} — {@code Threads.audioThreadFactory()} in the application — and the line
 * from an injected {@link LineSupplier}, so tests exercise the whole path without a sound card.
 *
 * <p>Underruns are survivable, not fatal: if the line has drained completely by the time the
 * mixer is ready to write, a counter goes up ({@link #underruns()}) and the buffer is written as
 * usual. There is no attempt to "catch up" by skipping audio, which would turn a hiccup into a
 * glitch. The check sits immediately <em>before</em> the write, not at the top of the pass:
 * straight after the previous blocking write returned, the device buffer is as full as it will
 * ever be, so a check there could never see a drained line.
 */
public final class SoftwareMixer implements AudioBackend {

    /** Output sample rate, in hertz. */
    public static final int SAMPLE_RATE = SoundBank.SAMPLE_RATE;
    /** Output channel count. */
    public static final int CHANNELS = SoundBank.CHANNELS;
    /** Output sample size, in bits. */
    public static final int BITS = 16;
    /** Target line buffer, in milliseconds. */
    public static final int BUFFER_MS = 30;
    /**
     * Passes of headroom the device buffer is opened with. One pass of buffer would mean the line
     * can only ever hold exactly what the mixer is about to replace, so a single slow pass — a
     * cold sound bank costs 16-38 ms against a 30 ms pass — starves it. Four passes bound the
     * added latency at about {@code 3 * BUFFER_MS} while giving the device something to play
     * while the mixer is busy.
     */
    public static final int BUFFER_PASSES = 4;
    /** Voices mixed simultaneously; the oldest is dropped when a new one does not fit. */
    public static final int MAX_VOICES = 24;
    /** Commands the queue holds before it starts dropping. */
    public static final int COMMAND_CAPACITY = 128;
    /** Amplitude below which the limiter is completely transparent. */
    public static final float LIMITER_KNEE = 0.8f;
    /**
     * Length of every music gain ramp — starts, crossfades, the pause duck, the boss layer — in
     * frames: half a second at the mixer's rate (M8, D19). Long enough to be inaudible as a step,
     * short enough that a screen change does not smear two songs together.
     */
    public static final int MUSIC_RAMP_FRAMES = SAMPLE_RATE / 2;
    /** How long {@link #close()} waits for the mixing thread, in milliseconds. */
    public static final long CLOSE_TIMEOUT_MS = 1_000L;

    /** The output format: signed 16-bit little-endian stereo at {@link #SAMPLE_RATE}. */
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false);

    /** Frames mixed and written per pass; one buffer's worth. */
    private static final int FRAMES_PER_PASS = SAMPLE_RATE * BUFFER_MS / 1000;

    /** Supplies the output line, so tests can inject one instead of asking the sound system. */
    @FunctionalInterface
    public interface LineSupplier {

        /**
         * Returns an unopened output line for the mixer's format.
         *
         * @return the line
         * @throws LineUnavailableException when the platform has none to give
         */
        SourceDataLine get() throws LineUnavailableException;
    }

    /** What the loop thread asks the mixing thread to do. */
    private enum Kind {
        /** Start a voice. */
        PLAY,
        /** Start a looping voice, or retarget the one already looping under this id. */
        PLAY_LOOP,
        /** Fade a looping voice out; it is dropped when its ramp reaches silence. */
        STOP_LOOP,
        /** Stop every voice. */
        STOP_ALL,
        /** Decode and cache every known sound. */
        WARM_UP
    }

    /** One queued request. Immutable, so it crosses the thread boundary safely. */
    private static final class Command {

        private final Kind kind;
        private final String id;
        private final float gain;
        private final float pan;

        private Command(Kind kind, String id, float gain, float pan) {
            this.kind = kind;
            this.id = id;
            this.gain = gain;
            this.pan = pan;
        }
    }

    private final SoundBank bank;
    private final ThreadFactory threadFactory;
    private final LineSupplier lineSupplier;
    private final Consumer<String> log;
    private final BlockingQueue<Command> commands = new ArrayBlockingQueue<>(COMMAND_CAPACITY);
    private final List<Voice> voices = new ArrayList<>();
    private final AtomicLong droppedCommands = new AtomicLong();
    private final AtomicLong startedVoices = new AtomicLong();
    private final AtomicLong stolenVoices = new AtomicLong();
    private final AtomicLong underruns = new AtomicLong();
    private final AtomicLong passes = new AtomicLong();

    private volatile float masterGain = 1.0f;
    private volatile boolean running;
    private volatile SourceDataLine line;
    private Thread thread;

    /**
     * Creates a mixer that opens a real line from {@link AudioSystem}.
     *
     * @param bank resolves sound ids to buffers
     * @param threadFactory supplies the daemon mixing thread
     */
    public SoftwareMixer(SoundBank bank, ThreadFactory threadFactory) {
        this(bank, threadFactory, () -> AudioSystem.getSourceDataLine(FORMAT), null);
    }

    /**
     * Creates a mixer with everything injected.
     *
     * @param bank resolves sound ids to buffers
     * @param threadFactory supplies the mixing thread; it is forced to daemon so a stuck mixer can
     *     never keep the JVM alive
     * @param lineSupplier supplies the output line
     * @param log receives diagnostics, or {@code null} for {@code System.err}
     */
    public SoftwareMixer(SoundBank bank, ThreadFactory threadFactory, LineSupplier lineSupplier,
            Consumer<String> log) {
        this.bank = Objects.requireNonNull(bank, "bank");
        this.threadFactory = Objects.requireNonNull(threadFactory, "threadFactory");
        this.lineSupplier = Objects.requireNonNull(lineSupplier, "lineSupplier");
        this.log = log != null ? log : System.err::println;
    }

    /**
     * The format the mixer writes.
     *
     * @return the output format
     */
    public static AudioFormat format() {
        return FORMAT;
    }

    /**
     * Frames mixed per pass.
     *
     * @return the pass size in stereo frames
     */
    public static int framesPerPass() {
        return FRAMES_PER_PASS;
    }

    @Override
    public synchronized void open() throws LineUnavailableException {
        if (running) {
            return;
        }
        // The thread is created *before* the line so a factory that refuses (the CI fallback
        // case, and the test that simulates it) never leaves a device open behind it.
        Thread mixer = threadFactory.newThread(this::mix);
        if (mixer == null) {
            throw new IllegalArgumentException("the audio thread factory returned no thread");
        }
        mixer.setDaemon(true);
        SourceDataLine opened = lineSupplier.get();
        if (opened == null) {
            throw new LineUnavailableException("the line supplier returned no line");
        }
        try {
            opened.open(FORMAT, FRAMES_PER_PASS * CHANNELS * (BITS / 8) * BUFFER_PASSES);
            opened.start();
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException e) {
            opened.close();
            throw e;
        }
        line = opened;
        running = true;
        thread = mixer;
        mixer.start();
    }

    @Override
    public void play(String id, float gain, float pan) {
        if (id == null) {
            return;
        }
        offer(new Command(Kind.PLAY, id, gain, pan));
    }

    /**
     * Starts the loop under an id, or — when a looping voice with the same id is already in
     * flight — retargets its gain with a ramp instead of starting a second copy (M8, D19): the
     * music voice is one per id, and repeated requests (a new volume, the pause duck) just move
     * it. Never blocks; a request that does not fit in the queue is dropped.
     *
     * @param id the sound id, resolved through {@link SoundBank}
     * @param gain target linear gain in {@code [0, 1]}, reached over {@link #MUSIC_RAMP_FRAMES}
     * @param pan {@code -1} hard left, {@code 0} centre, {@code +1} hard right
     */
    @Override
    public void playLooping(String id, float gain, float pan) {
        if (id == null) {
            return;
        }
        offer(new Command(Kind.PLAY_LOOP, id, gain, pan));
    }

    /**
     * Fades the looping voice under an id out over {@link #MUSIC_RAMP_FRAMES}; the mixing thread
     * drops it when the ramp reaches silence. A no-op when nothing loops under that id.
     *
     * @param id the sound id
     */
    @Override
    public void stopLooping(String id) {
        if (id == null) {
            return;
        }
        offer(new Command(Kind.STOP_LOOP, id, 0.0f, 0.0f));
    }

    @Override
    public void stopAll() {
        // Pending plays are exactly what a stop is meant to cancel, so clearing first is correct
        // and also guarantees the stop itself finds room in the queue.
        commands.clear();
        offer(new Command(Kind.STOP_ALL, null, 0.0f, 0.0f));
    }

    @Override
    public void registerLoop(String id, float[] samples) {
        bank.register(id, samples);
    }

    @Override
    public boolean hasLoop(String id) {
        return bank.isLoaded(id);
    }

    @Override
    public void setMasterGain(float gain) {
        masterGain = Float.isFinite(gain) ? Math.max(0.0f, Math.min(1.0f, gain)) : 0.0f;
    }

    @Override
    public void warmUp() {
        offer(new Command(Kind.WARM_UP, null, 0.0f, 0.0f));
    }

    @Override
    public void warmUpBlocking() {
        // The bank's cache is a ConcurrentHashMap and the mixing thread only reads it, so warming
        // it from the boot thread is safe and keeps the decode off the write cadence entirely.
        bank.warmUp();
    }

    @Override
    public void close() {
        Thread mixer;
        SourceDataLine open;
        synchronized (this) {
            if (!running && thread == null && line == null) {
                return;
            }
            running = false;
            mixer = thread;
            open = line;
            thread = null;
        }
        // Stopping and flushing the line is what unblocks a mixing thread parked inside
        // SourceDataLine.write; an interrupt on its own would not, because that call is not
        // interruptible.
        if (open != null) {
            try {
                open.stop();
                open.flush();
            } catch (RuntimeException e) {
                log.accept("Audio: line refused to stop (" + e + ").");
            }
        }
        if (mixer != null && mixer != Thread.currentThread()) {
            try {
                mixer.join(CLOSE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (open != null) {
            open.close();
        }
        line = null;
        commands.clear();
        if (mixer == null || !mixer.isAlive()) {
            // The voice list belongs to the mixing thread; touch it only once that thread is
            // gone (it clears the list itself on the way out) or was never started.
            voices.clear();
        }
    }

    @Override
    public boolean isRealDevice() {
        return true;
    }

    /**
     * Whether the mixing thread is running.
     *
     * @return {@code true} between {@link #open()} and {@link #close()}
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Requests that could not be queued because the queue was full.
     *
     * @return the count
     */
    public long droppedCommands() {
        return droppedCommands.get();
    }

    /**
     * Voices started since construction.
     *
     * @return the count
     */
    public long startedVoices() {
        return startedVoices.get();
    }

    /**
     * Voices cut short because {@link #MAX_VOICES} was already in flight.
     *
     * @return the count
     */
    public long stolenVoices() {
        return stolenVoices.get();
    }

    /**
     * Times the line was found completely drained when the mixer was ready to write to it.
     *
     * @return the count
     */
    public long underruns() {
        return underruns.get();
    }

    /**
     * Mixing passes completed.
     *
     * @return the count
     */
    public long passes() {
        return passes.get();
    }

    /**
     * Voices currently in flight. Only meaningful from the mixing thread or while stopped.
     *
     * @return the count
     */
    public int activeVoices() {
        return voices.size();
    }

    /**
     * Drains the command queue and mixes one buffer, without any line involved. This is the whole
     * signal path — command handling, summing, master gain, limiter — so a test can assert on it
     * on the calling thread, with no device and no thread spawned.
     *
     * @param out interleaved stereo accumulator, overwritten (not added to); at least
     *     {@code frames * 2} long
     * @param frames how many stereo frames to produce
     * @return the number of voices still in flight afterwards
     */
    public int render(float[] out, int frames) {
        Objects.requireNonNull(out, "out");
        drainCommands();
        int samples = Math.min(out.length, frames * CHANNELS);
        Arrays.fill(out, 0, samples, 0.0f);
        for (int i = voices.size() - 1; i >= 0; i--) {
            Voice voice = voices.get(i);
            voice.mixInto(out, frames);
            if (voice.finished()) {
                voices.remove(i);
            }
        }
        float gain = masterGain;
        for (int i = 0; i < samples; i++) {
            out[i] = limit(out[i] * gain);
        }
        passes.incrementAndGet();
        return voices.size();
    }

    /**
     * The soft limiter. Transparent below {@link #LIMITER_KNEE}, {@code tanh}-compressed above it,
     * and bounded by full scale for any input, so summed voices saturate smoothly instead of
     * clipping. A very loud sum approaches — and in float arithmetic reaches — exactly
     * {@code ±1}, which is full scale and encodes cleanly; it never wraps.
     *
     * @param value the summed sample
     * @return the limited sample, within {@code [-1, 1]}
     */
    public static float limit(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        float magnitude = Math.abs(value);
        if (magnitude <= LIMITER_KNEE) {
            return value;
        }
        float headroom = 1.0f - LIMITER_KNEE;
        float over = (magnitude - LIMITER_KNEE) / headroom;
        float shaped = LIMITER_KNEE + headroom * (float) StrictMath.tanh(over);
        return value < 0.0f ? -shaped : shaped;
    }

    private void offer(Command command) {
        if (!commands.offer(command)) {
            droppedCommands.incrementAndGet();
        }
    }

    private void drainCommands() {
        Command command;
        while ((command = commands.poll()) != null) {
            switch (command.kind) {
                case PLAY:
                    start(command);
                    break;
                case PLAY_LOOP:
                    startLooping(command);
                    break;
                case STOP_LOOP:
                    stopLooping(command);
                    break;
                case STOP_ALL:
                    voices.clear();
                    break;
                case WARM_UP:
                    bank.warmUp();
                    break;
                default:
                    break;
            }
        }
    }

    private void start(Command command) {
        float[] samples;
        try {
            samples = bank.samples(command.id);
        } catch (RuntimeException e) {
            log.accept("Audio: cannot load '" + command.id + "' (" + e + ").");
            return;
        }
        if (samples.length < 2) {
            return;
        }
        if (voices.size() >= MAX_VOICES) {
            voices.remove(0);
            stolenVoices.incrementAndGet();
        }
        voices.add(new Voice(command.id, samples, command.gain, command.pan));
        startedVoices.incrementAndGet();
    }

    /**
     * Handles a {@link Kind#PLAY_LOOP}: the music voice is one per id, so a looping voice still
     * in flight under the id is retargeted (it may even be mid fade-out — the new target revives
     * it) and only an absent one is created, fading in from silence over the music ramp (M8).
     */
    private void startLooping(Command command) {
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            if (voice.id().equals(command.id) && voice.isLooping() && !voice.finished()) {
                voice.revive();
                voice.rampTo(command.gain, command.pan, MUSIC_RAMP_FRAMES);
                return;
            }
        }
        float[] samples;
        try {
            samples = bank.samples(command.id);
        } catch (RuntimeException e) {
            log.accept("Audio: cannot load '" + command.id + "' (" + e + ").");
            return;
        }
        if (samples.length < 2) {
            return;
        }
        if (voices.size() >= MAX_VOICES) {
            voices.remove(0);
            stolenVoices.incrementAndGet();
        }
        voices.add(Voice.loop(command.id, samples, command.gain, command.pan,
                MUSIC_RAMP_FRAMES));
        startedVoices.incrementAndGet();
    }

    /** Handles a {@link Kind#STOP_LOOP}: fade whatever loops under the id, drop it at silence. */
    private void stopLooping(Command command) {
        for (int i = 0; i < voices.size(); i++) {
            Voice voice = voices.get(i);
            if (voice.id().equals(command.id) && voice.isLooping() && !voice.finished()) {
                voice.fadeOut(MUSIC_RAMP_FRAMES);
            }
        }
    }

    /** The mixing thread body: drain, mix, encode, write, repeat. */
    private void mix() {
        float[] buffer = new float[FRAMES_PER_PASS * CHANNELS];
        byte[] bytes = new byte[FRAMES_PER_PASS * CHANNELS * (BITS / 8)];
        boolean firstPass = true;
        while (running) {
            SourceDataLine open = line;
            if (open == null) {
                break;
            }
            render(buffer, FRAMES_PER_PASS);
            encode(buffer, bytes);
            // Checked here, after the mixing work: this is the moment the device has had the
            // longest to drain, and a line that is completely free has run out of audio. An empty
            // line is only an underrun once something has been written to it — on the very first
            // pass it is empty because playback has not started, which is normal.
            if (!firstPass && open.available() >= open.getBufferSize()) {
                underruns.incrementAndGet();
            }
            firstPass = false;
            try {
                open.write(bytes, 0, bytes.length);
            } catch (RuntimeException e) {
                if (running) {
                    log.accept("Audio: output failed (" + e + "); stopping the mixer.");
                }
                break;
            }
        }
        voices.clear();
    }

    /** Converts the mixed float buffer to signed 16-bit little-endian frames. */
    private static void encode(float[] buffer, byte[] out) {
        for (int i = 0; i < buffer.length; i++) {
            int sample = Math.round(buffer[i] * Short.MAX_VALUE);
            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }
            out[i * 2] = (byte) (sample & 0xFF);
            out[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }
}
