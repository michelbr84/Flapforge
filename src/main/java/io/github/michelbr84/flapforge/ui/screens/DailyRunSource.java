package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.RunFactory;
import io.github.michelbr84.flapforge.core.TimeSource;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.progression.DailyChallenge;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.RunLoadout;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The {@link SeededRunSource} of the daily challenge (D28, D29, M9): every run it builds is
 * today's pick — its world, its tier and its two forced modifiers — played with what the profile
 * has selected and bought, on the one seed that date has.
 *
 * <p>It is the {@link ChallengeRunSource} of the daily, with one difference that matters:
 * <b>the seed handed in is ignored</b>. A daily is the same run all day, so an instant retry
 * (D29: {@code Space} on the game-over strip, which asks for the next seed) plays the identical
 * configuration again and only the attempt counter moves —
 * {@code ProgressionManager.apply} records {@code attempts++} and the best gate count for every
 * run in {@link RunMode#DAILY}.
 *
 * <p>Asking for a run also <em>settles</em> the day (E27): {@link DailyChallenge#today} writes
 * the pick onto the profile the first time it is asked for, so a run started from a cold launch
 * is played on exactly the configuration the selection screen showed. The optional save hook is
 * what puts that write on the disk; without it the pick still reaches the disk with the next
 * save the session makes (the progression pass at the end of this very run).
 *
 * <p>A session without a profile — a test, the headless launch — plays the pick of a fresh
 * default profile: deterministic, and nothing is written anywhere.
 */
public final class DailyRunSource implements SeededRunSource {

    private final RunFactory runs;
    private final Supplier<PlayerProfile> profiles;
    private final DailyChallenge daily;
    private final Runnable onPicked;

    /**
     * Creates the source of the daily.
     *
     * @param content the loaded content
     * @param profiles the live profile of the session, read at the start of every run; may be
     *     {@code null}, and may return {@code null} in a session without persistence
     * @param clock the time source the UTC date comes from (D23)
     */
    public DailyRunSource(GameContent content, Supplier<PlayerProfile> profiles,
            TimeSource clock) {
        this(content, profiles, clock, null);
    }

    /**
     * Creates the source of the daily with a save hook.
     *
     * @param content the loaded content
     * @param profiles the live profile of the session, read at the start of every run
     * @param clock the time source the UTC date comes from (D23)
     * @param onPicked run after the pick has been written onto the profile, or {@code null}
     */
    public DailyRunSource(GameContent content, Supplier<PlayerProfile> profiles, TimeSource clock,
            Runnable onPicked) {
        this.runs = new RunFactory(Objects.requireNonNull(content, "content"));
        this.profiles = profiles;
        this.daily = new DailyChallenge(clock);
        this.onPicked = onPicked;
    }

    /**
     * The content every run is built from.
     *
     * @return the content
     */
    public GameContent content() {
        return runs.content();
    }

    /**
     * Today's pick, written onto the profile if it was not there yet (E27).
     *
     * @return the pick
     */
    public DailyChallenge.Pick pick() {
        return settle(profile());
    }

    @Override
    public Run newRun(long seed) {
        PlayerProfile profile = profile();
        DailyChallenge.Pick pick = settle(profile);
        RunConfig config = RunLoadout.configFor(profile, runs.content(), pick.seed(),
                        RunMode.DAILY)
                .toBuilder()
                .worldId(pick.worldId())
                .tierId(pick.tierId())
                .forcedModifiers(pick.modifierIds())
                .build();
        return runs.newRun(config);
    }

    /**
     * The profile the daily is picked for: the session's, or a fresh default one when there is
     * none (nothing is written in that case, because nothing is saved).
     *
     * @return a profile, never {@code null}
     */
    private PlayerProfile profile() {
        PlayerProfile profile = profiles == null ? null : profiles.get();
        return profile == null ? PlayerProfile.fresh(0).normalize() : profile;
    }

    /**
     * Settles the day on a profile and reports the pick.
     *
     * @param profile the profile to read and write
     * @return the pick
     */
    private DailyChallenge.Pick settle(PlayerProfile profile) {
        DailyChallenge.Pick pick = daily.today(profile, runs.content());
        if (onPicked != null) {
            onPicked.run();
        }
        return pick;
    }
}
