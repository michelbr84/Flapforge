package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The factory is what makes the game survive a machine with no sound card, no permission to open
 * one, or a JVM that will not give the mixer a thread (E30.j). Every one of those has to end in a
 * {@link NullAudio}, one log line, and no exception escaping into the boot sequence.
 *
 * <p>Failure is simulated with a {@link ThreadFactory} that refuses, which is deliberate: the
 * mixer builds its thread before it touches the sound system, so none of these cases needs — or
 * risks — a real audio device, and the suite passes identically on a developer machine and on a
 * headless CI runner.
 */
class AudioBackendCreateTest {

    @Test
    void disabledAudioSkipsTheSoundSystemEntirely() {
        AtomicInteger threadRequests = new AtomicInteger();
        List<String> log = new ArrayList<>();
        AudioBackend backend = AudioBackend.create(false, r -> {
            threadRequests.incrementAndGet();
            return new Thread(r);
        }, new SoundBank(), log::add);

        assertInstanceOf(NullAudio.class, backend);
        assertFalse(backend.isRealDevice());
        assertEquals(0, threadRequests.get(), "--no-audio must not build a mixing thread");
        assertEquals(List.of(), log, "turning audio off on purpose is not a warning");
    }

    @Test
    void aRefusedThreadFallsBackToNullAudioWithOneLogLine() {
        List<String> log = new ArrayList<>();
        AudioBackend backend = AudioBackend.create(true, r -> {
            throw new SecurityException("no threads for you");
        }, new SoundBank(), log::add);

        assertInstanceOf(NullAudio.class, backend);
        assertFalse(backend.isRealDevice());
        assertEquals(1, log.size(), () -> "expected exactly one line, got " + log);
        assertTrue(log.get(0).contains("SecurityException"), log.get(0));
        assertTrue(log.get(0).contains("without sound"), log.get(0));
    }

    @Test
    void aFactoryThatReturnsNothingAlsoFallsBack() {
        List<String> log = new ArrayList<>();
        AudioBackend backend = AudioBackend.create(true, r -> null, new SoundBank(), log::add);

        assertInstanceOf(NullAudio.class, backend);
        assertEquals(1, log.size(), () -> "expected exactly one line, got " + log);
        assertTrue(log.get(0).contains("IllegalArgumentException"), log.get(0));
    }

    @Test
    void theFallbackBackendIsFullyUsable() {
        AudioBackend backend = AudioBackend.create(true, r -> {
            throw new IllegalStateException("no audio here");
        }, new SoundBank(), message -> {
        });

        // The rest of the game must not have to know: every call still works, silently.
        backend.play(ToneSynth.FLAP);
        backend.play(ToneSynth.CRASH, 0.5f, -0.5f);
        backend.setMasterGain(0.4f);
        backend.warmUp();
        backend.stopAll();
        backend.close();

        NullAudio silent = assertInstanceOf(NullAudio.class, backend);
        assertEquals(2, silent.plays());
        assertEquals(1, silent.stops());
        assertEquals(ToneSynth.CRASH, silent.lastId());
        assertEquals(0.4f, silent.masterGain(), 0.0f);
        assertTrue(silent.isClosed());
    }

    @Test
    void nullAudioReportsItselfAsNotADevice() {
        NullAudio audio = new NullAudio();
        assertFalse(audio.isRealDevice());
        assertFalse(audio.isOpen());
        audio.open();
        assertTrue(audio.isOpen());
        audio.close();
        assertFalse(audio.isOpen());
        assertTrue(audio.isClosed());
    }
}
