package io.github.michelbr84.flapforge.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.MusicDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.support.CaptureAudioBackend;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The world music loop (M8, D19). Every shipped world's block renders — through the real
 * {@code worlds.json} content — into audio a {@link CaptureAudioBackend} mixes forward two
 * seconds of, and that audio carries signal; the same block renders byte-identical samples twice;
 * different worlds sound different; the boss variant is a strictly faster loop of the same block.
 * The render of each loop is timed and printed so the 150 ms budget stays visible.
 *
 * <p>The mixer-side behaviour the tests lean on (looping wrap, gain ramps, mute) is exercised
 * through {@link CaptureAudioBackend} and the manager, both of which mix with the production
 * {@link Voice}.
 */
class MusicSequencerTest {

    /** Seconds of loop the audibility assertions mix forward. */
    private static final double AUDIBLE_SECONDS = 2.0;
    /** RMS a two-second window of a normalised loop must clear to count as audible. */
    private static final double AUDIBLE_RMS = 0.02;
    /** Render budget per loop, in milliseconds (plan M8). */
    private static final double RENDER_BUDGET_MS = 150.0;

    private static GameContent content() {
        return GameContent.load();
    }

    private static List<WorldDef> worlds() {
        List<WorldDef> all = new ArrayList<>();
        content().worlds().forEach(all::add);
        return all;
    }

    @Test
    void everyWorldRendersAnAudibleLoop() {
        for (WorldDef world : worlds()) {
            MusicDef music = world.music();
            assertNotEquals(null, music, world.id() + " ships a music block");

            CaptureAudioBackend backend = new CaptureAudioBackend();
            float[] loop = MusicSequencer.render(music);
            backend.registerLoop(MusicSequencer.idForWorld(world.id()), loop);
            backend.playLooping(MusicSequencer.idForWorld(world.id()),
                    MusicSequencer.RUN_GAIN, 0.0f);
            double rms = rms(backend.mixedLoopSeconds(AUDIBLE_SECONDS));
            assertTrue(rms > AUDIBLE_RMS, () -> world.id() + " loop is audible: rms " + rms);
        }
    }

    @Test
    void theSameBlockRendersIdenticalBytes() {
        for (WorldDef world : worlds()) {
            float[] first = MusicSequencer.render(world.music());
            float[] second = MusicSequencer.render(world.music());
            assertTrue(Arrays.equals(first, second),
                    world.id() + " renders deterministically for its block");
            // The buffer is stereo-interleaved at the mixer rate and spans exactly eight bars.
            long stepFrames = Math.round(MusicSequencer.SAMPLE_RATE * 60.0
                    / (world.music().tempo() * MusicSequencer.STEPS_PER_BEAT));
            long frames = stepFrames * MusicSequencer.BARS * MusicSequencer.BEATS_PER_BAR
                    * MusicSequencer.STEPS_PER_BEAT;
            assertEquals((int) (frames * SoundBank.CHANNELS), first.length,
                    world.id() + " loop length follows the block's tempo");
        }
    }

    @Test
    void differentWorldsSoundDifferent() {
        List<float[]> loops = new ArrayList<>();
        for (WorldDef world : worlds()) {
            float[] loop = MusicSequencer.render(world.music());
            for (float[] previous : loops) {
                assertTrue(!Arrays.equals(previous, loop),
                        "two worlds must not share a loop");
            }
            loops.add(loop);
        }
        assertEquals(worlds().size(), loops.size());
    }

    @Test
    void theBossVariantIsFasterAndStillDeterministic() {
        for (WorldDef world : worlds()) {
            MusicDef music = world.music();
            float[] base = MusicSequencer.render(music);
            float[] boss = MusicSequencer.render(music, true);
            assertTrue(boss.length < base.length, () -> world.id()
                    + " boss variant raises the tempo, so the loop is shorter: " + boss.length
                    + " vs " + base.length);
            assertTrue(Arrays.equals(boss, MusicSequencer.render(music, true)),
                    world.id() + " boss variant is deterministic");
            assertEquals(base.length, MusicSequencer.render(music).length,
                    world.id() + " base loop length is stable across renders");
        }
    }

    @Test
    void everyWorldRendersWithinTheBudget() {
        for (WorldDef world : worlds()) {
            long start = System.nanoTime();
            float[] loop = MusicSequencer.render(world.music());
            long baseMs = (System.nanoTime() - start) / 1_000_000L;
            start = System.nanoTime();
            MusicSequencer.render(world.music(), true);
            long bossMs = (System.nanoTime() - start) / 1_000_000L;
            System.out.printf("music render %s: %d ms (boss variant %d ms), %d frames%n",
                    world.id(), baseMs, bossMs, loop.length / SoundBank.CHANNELS);
            assertTrue(baseMs < RENDER_BUDGET_MS,
                    () -> world.id() + " base loop rendered in " + baseMs + " ms");
            assertTrue(bossMs < RENDER_BUDGET_MS,
                    () -> world.id() + " boss loop rendered in " + bossMs + " ms");
        }
    }

    @Test
    void aMutedManagerKeepsTheMusicSilent() {
        CaptureAudioBackend backend = new CaptureAudioBackend();
        AudioManager manager = new AudioManager(backend);
        manager.setMuted(true);
        WorldDef world = worlds().get(0);
        manager.prepareMusic(MusicSequencer.idForWorld(world.id()),
                MusicSequencer.render(world.music()));
        manager.startMusic(MusicSequencer.idForWorld(world.id()), MusicSequencer.RUN_GAIN);

        assertEquals(0, backend.loopPlayCount(), "a muted manager queues no loop");
        assertEquals(0.0, rms(backend.mixedLoopSeconds(AUDIBLE_SECONDS)), 1e-9,
                "silence reaches the output");
        assertEquals(MusicSequencer.idForWorld(world.id()), manager.currentMusicId(),
                "the request is remembered for the unmute");

        manager.setMuted(false);
        assertEquals(1, backend.loopPlayCount(), "unmuting re-issues the loop");
        assertTrue(rms(backend.mixedLoopSeconds(AUDIBLE_SECONDS)) > AUDIBLE_RMS,
                "the loop is audible after the unmute");
    }

    @Test
    void theMusicVolumeReachesTheLoopGain() {
        CaptureAudioBackend backend = new CaptureAudioBackend();
        AudioManager manager = new AudioManager(backend);
        manager.setVolumes(1.0, 1.0, 0.5);
        WorldDef world = worlds().get(0);
        String id = MusicSequencer.idForWorld(world.id());
        manager.prepareMusic(id, MusicSequencer.render(world.music()));
        manager.startMusic(id, MusicSequencer.RUN_GAIN);

        assertEquals(1, backend.loopPlayCount());
        assertEquals(MusicSequencer.RUN_GAIN * 0.5, backend.loopPlayList().get(0).gain(), 1e-6,
                "the loop gain is the base gain times the music volume");

        manager.setVolumes(1.0, 1.0, 0.25);
        assertEquals(2, backend.loopPlayCount(), "a volume change retargets the live loop");
        assertEquals(MusicSequencer.RUN_GAIN * 0.25, backend.loopPlayList().get(1).gain(), 1e-6);
    }

    @Test
    void aScreenChangeCrossfadesAndTheOldLoopFadesOut() {
        CaptureAudioBackend backend = new CaptureAudioBackend();
        AudioManager manager = new AudioManager(backend);
        GameContent content = content();
        List<WorldDef> all = worlds();
        String first = MusicSequencer.idForWorld(all.get(0).id());
        String second = MusicSequencer.idForWorld(all.get(1).id());
        manager.prepareMusic(first, MusicSequencer.render(all.get(0).music()));
        manager.prepareMusic(second, MusicSequencer.render(all.get(1).music()));

        manager.startMusic(first, MusicSequencer.RUN_GAIN);
        manager.startMusic(second, MusicSequencer.RUN_GAIN);

        assertEquals(2, backend.loopPlayCount());
        assertTrue(backend.isLoopStopped(first), "the previous loop fades out");
        // The recorder advances its voices only when asked to mix, so mixing the ramp away is
        // what retires the outgoing loop; the incoming one stays.
        backend.mixedLoopSeconds(SoftwareMixer.MUSIC_RAMP_FRAMES / (double) SoundBank.SAMPLE_RATE
                + 0.1);
        assertEquals(List.of(second), backend.activeLoopIds(),
                "only the new loop keeps playing");

        manager.stopMusic();
        assertTrue(backend.isLoopStopped(second), "leaving for silence fades the loop out");
        backend.mixedLoopSeconds(SoftwareMixer.MUSIC_RAMP_FRAMES / (double) SoundBank.SAMPLE_RATE
                + 0.1);
        assertEquals(0.0, rms(backend.mixedLoopSeconds(1.0)), 1e-9,
                "past the fade both loops are silence");
    }

    @Test
    void thePauseDuckRetargetsTheLoopWithoutRestartingIt() {
        CaptureAudioBackend backend = new CaptureAudioBackend();
        AudioManager manager = new AudioManager(backend);
        WorldDef world = worlds().get(0);
        String id = MusicSequencer.idForWorld(world.id());
        manager.prepareMusic(id, MusicSequencer.render(world.music()));
        manager.startMusic(id, MusicSequencer.RUN_GAIN);

        manager.duckMusic(MusicSequencer.PAUSE_DUCK);
        assertEquals(2, backend.loopPlayCount());
        assertEquals(MusicSequencer.RUN_GAIN * manager.musicVolume()
                        * MusicSequencer.PAUSE_DUCK,
                backend.loopPlayList().get(1).gain(), 1e-6, "the duck lowers the gain");
        assertTrue(backend.loopPlayList().get(1).gain() < backend.loopPlayList().get(0).gain(),
                "the ducked loop is quieter than the run loop");

        manager.duckMusic(1.0f);
        assertEquals(3, backend.loopPlayCount());
        assertEquals(MusicSequencer.RUN_GAIN * manager.musicVolume(),
                backend.loopPlayList().get(2).gain(), 1e-6, "the undo restores the run gain");
    }

    @Test
    void theSequencerIdHelpersRoundTrip() {
        assertEquals("music/green_fields", MusicSequencer.idForWorld("green_fields"));
        assertEquals("music/green_fields/boss", MusicSequencer.bossIdForWorld("green_fields"));
        assertTrue(MusicSequencer.bossIdForWorld("void_echoes")
                .startsWith(MusicSequencer.ID_PREFIX));
    }

    /** RMS of an interleaved stereo buffer. */
    private static double rms(float[] samples) {
        if (samples.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (float sample : samples) {
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }
}
