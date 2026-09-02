package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.ability.AbilityInstance;
import io.github.michelbr84.flapforge.ability.AbilityManager;
import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.event.GameEvent;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.RewardSummary;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import io.github.michelbr84.flapforge.gameplay.run.StreakTracker;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.input.Keys;
import io.github.michelbr84.flapforge.input.RawInput;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.ProgressionManager;
import io.github.michelbr84.flapforge.progression.ProgressionOutcome;
import io.github.michelbr84.flapforge.progression.ProgressionRules;
import io.github.michelbr84.flapforge.render.AssetResolver;
import io.github.michelbr84.flapforge.render.GameRenderer;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The screen a run is played on (M1). It owns one {@link Run} and one {@link GameRenderer} and
 * does nothing else: every rule lives in the simulation, every pixel in the renderers.
 *
 * <h2>Input (D2)</h2>
 * A flap is a press edge of {@link InputAction#FLAP} or of the left mouse button — never a held
 * key, which is upstream's {@code keyFlag}. Hold-to-flap is an accessibility setting realised
 * inside the simulation ({@code RunInput.autoFlapHeld}); it is read from
 * {@code settings.holdToFlap} when the screen is built from a {@link GameContext}, and defaults
 * to {@code false} otherwise.
 *
 * <h2>Abilities (D9, M5)</h2>
 * The {@code ABILITY} action — {@code X}, {@code Shift} or the right mouse button — is forwarded
 * to the run as {@code RunInput.ability}; the simulation decides whether it activates anything.
 * When the press produced no {@code AbilityActivated} fact the screen answers instead of staying
 * silent: the HUD badge blinks red and a toast says why — nothing equipped, on cooldown, out of
 * charges, or stripped by the run's rules, in which case it names the rule. The toast is
 * rate-limited to one every {@value #REFUSAL_QUIET_TICKS} ticks, because the ability key is
 * exactly the key a player mashes when a gate is closing.
 *
 * <h2>Drafts (D11, D27, M6)</h2>
 * When a tick leaves the run in {@code CHOOSING_MODIFIER} the screen pushes a
 * {@link ModifierChoiceOverlay} and stops ticking: the simulation was already frozen by
 * {@code ModifierDirector}, and the overlay drives the frozen ticks through
 * {@link #tickDraft(int)} until the resume countdown hands the run back. The HUD's build strip and
 * the streak-bonus readout are refreshed from the run after every tick, so a card taken mid-flight
 * is on screen on the next frame, and a {@code SynergyActivated} fact raises a toast beside the
 * chip it added.
 *
 * <h2>Pausing (D2)</h2>
 * While the run is live — {@code FLYING} and the {@code BREATHER} a draft waits in, both of which
 * tick the world — losing the window focus, being iconified or pressing {@code Esc} pushes a
 * {@link PauseOverlay}; resuming needs an explicit key or click and zeroes the loop accumulator so
 * no time accrued while paused is replayed as a burst of ticks. The frozen draft phases pause
 * nothing, because nothing is moving: {@link ModifierChoiceOverlay} owns those ticks and reads
 * {@code Esc} as "skip this draft".
 *
 * <p>The one focus loss that must <em>not</em> pause is the one the {@code F11} handshake causes:
 * entering or leaving borderless fullscreen disposes and re-shows the frame, so the toolkit
 * delivers a {@code FocusLost} a few ticks later that the player never asked for. The
 * {@link ScreenManager} marks a window of {@value ScreenManager#FULLSCREEN_GRACE_TICKS} ticks
 * after every fullscreen request ({@link ScreenManager#isFullscreenHandshake()}); a focus loss
 * inside that window is ignored. An {@code Esc} press or an iconify is always honoured, since
 * neither can be produced by the handshake.
 *
 * <h2>Game over (D29, D14)</h2>
 * The tick that moves the run to {@code FINISHED} writes the run into the profile — exactly once,
 * through {@link ProgressionManager#apply} — publishes what it paid on the bus, queues a save and
 * only then pushes a {@link GameOverOverlay} carrying the outcome. Doing it in that order is what
 * makes the instant retry safe: the rewards are already banked when the retry key is read. Retry
 * starts a new run with the next seed from the {@link SeedSequence} and the same configuration;
 * quitting pops back to the menu.
 */
public final class GameScreen implements Screen {

    /** Ticks between two refusal toasts, so a mashed ability key cannot bury the playfield. */
    public static final int REFUSAL_QUIET_TICKS = 90;

    private final ScreenManager screens;
    private final GameContext context;
    private final SeededRunSource runFactory;
    private final SeedSequence seeds;
    private final GameRenderer renderer;
    private final Strings strings;
    private final ToastLayer toasts;

    private Run run;
    private long seed;
    private String seedText;
    private String shownLanguage;
    private boolean holdToFlap;
    private boolean gameOverShown;
    private int runsStarted;
    private int refusalQuietTicks;
    private String abilityRefusal = "";
    private ProgressionOutcome lastOutcome = ProgressionOutcome.EMPTY;
    private int shownModifiers = -1;
    private int shownSynergies = -1;
    private long shownStreakBonus = -1;

    /**
     * Creates a screen playing classic runs with clock-derived seeds.
     *
     * @param screens the screen stack
     */
    public GameScreen(ScreenManager screens) {
        this(screens, null, new ClassicRunFactory(), SeedSequence.random());
    }

    /**
     * Creates a screen.
     *
     * @param screens the screen stack
     * @param runFactory builds each run (the integrator swaps in the content-backed one)
     * @param seeds the seed source
     */
    public GameScreen(ScreenManager screens, SeededRunSource runFactory, SeedSequence seeds) {
        this(screens, null, runFactory, seeds);
    }

    /**
     * Creates a screen for a wired application: the run honours {@code settings.holdToFlap} and
     * the run's facts are published on the presentation bus (D16), which is where the audio
     * manager and the toast layer pick them up.
     *
     * @param context the application services
     * @param runFactory builds each run
     * @param seeds the seed source
     */
    public GameScreen(GameContext context, SeededRunSource runFactory, SeedSequence seeds) {
        this(Objects.requireNonNull(context, "context").screens(), context, runFactory, seeds);
    }

    private GameScreen(ScreenManager screens, GameContext context, SeededRunSource runFactory,
            SeedSequence seeds) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.runFactory = Objects.requireNonNull(runFactory, "runFactory");
        this.seeds = Objects.requireNonNull(seeds, "seeds");
        this.strings = context != null && context.strings() != null
                ? context.strings() : Strings.active();
        this.toasts = context != null ? context.toasts() : null;
        this.renderer = new GameRenderer(WorldPalette.GREEN_FIELDS,
                strings.get(StringKey.GAME_READY_HINT));
        this.renderer.setStreakLabel(strings.get(StringKey.HUD_STREAK));
        this.renderer.setCoinLabel(strings.get(StringKey.HUD_COINS));
        this.renderer.setStreakStep(streakStep());
        this.renderer.setAbilityStateLabels(strings.get(StringKey.HUD_ABILITY_READY),
                strings.get(StringKey.HUD_ABILITY_COOLDOWN));
        this.renderer.setShieldLabel(strings.get(StringKey.HUD_SHIELD_CHARGES));
        this.shownLanguage = strings.language();
        this.holdToFlap = context != null && context.settings().holdToFlap;
        startRun();
    }

    /**
     * The run being played.
     *
     * @return the run, never {@code null}
     */
    public Run run() {
        return run;
    }

    /**
     * A run that has not ended yet holds off the 60-second autosave (D15): the profile is written
     * at run end anyway, and a disk write in the middle of a flap is exactly the hitch the rule
     * exists to avoid. {@code BOSS} and {@code CHOOSING_MODIFIER} are the same case and arrive
     * with the phases that name them.
     *
     * @return {@code true} while the run is still being played
     */
    @Override
    public boolean blocksAutosave() {
        return run != null && !run.isFinished();
    }

    /**
     * Seed of the run being played.
     *
     * @return the seed
     */
    public long seed() {
        return seed;
    }

    /**
     * How many runs this screen has started (1 after construction).
     *
     * @return the count
     */
    public int runsStarted() {
        return runsStarted;
    }

    /**
     * The renderer, for tests inspecting the animation state.
     *
     * @return the renderer
     */
    public GameRenderer renderer() {
        return renderer;
    }

    /**
     * Whether hold-to-flap is engaged (accessibility, D2; a settings flag from M2 on).
     *
     * @return {@code true} when a held flap key issues synthetic flaps
     */
    public boolean isHoldToFlap() {
        return holdToFlap;
    }

    /**
     * Turns hold-to-flap on or off.
     *
     * @param value the new state
     */
    public void setHoldToFlap(boolean value) {
        this.holdToFlap = value;
    }

    /** Starts a fresh run with the next seed (D29: instant retry, new seed, same config). */
    public void restart() {
        startRun();
    }

    private void startRun() {
        seed = seeds.next();
        seedText = seeds.isExplicit() ? strings.format(StringKey.HUD_SEED, seed) : null;
        run = runFactory.newRun(seed);
        renderer.reset();
        // A manifest entry may override the procedural bird per bird and per world (D18); with the
        // shipped empty manifest this resolves to nothing and the art stays procedural.
        renderer.applyAssets(AssetResolver.active(), run.config().birdId(),
                run.config().worldId());
        renderer.setAbilityName(activeAbilityName());
        refusalQuietTicks = 0;
        abilityRefusal = "";
        gameOverShown = false;
        lastOutcome = ProgressionOutcome.EMPTY;
        invalidateBuild();
        runsStarted++;
        publish(new GameEvent.RunStarted(run.config().birdId(), run.config().worldId(),
                run.config().tierId(), seed));
    }

    private void publish(GameEvent event) {
        if (context != null) {
            context.publish(event);
        }
    }

    /**
     * The streak length that pays a reward step, so the HUD can mark it (D26, E32.a). Without
     * content — a bare screen stack in a test — the tracker's own default stands in, which is the
     * same number {@code economy.json} ships.
     *
     * @return {@code economy.rewards.streak.step}
     */
    private int streakStep() {
        if (context != null && context.content() != null && context.content().economy() != null) {
            return context.content().economy().rewards().streak().step();
        }
        return StreakTracker.DEFAULT_STEP;
    }

    /** Re-reads the texts the HUD draws (a live language switch, D25). */
    private void refreshTexts() {
        renderer.setReadyHint(strings.get(StringKey.GAME_READY_HINT));
        renderer.setStreakLabel(strings.get(StringKey.HUD_STREAK));
        renderer.setCoinLabel(strings.get(StringKey.HUD_COINS));
        renderer.setAbilityStateLabels(strings.get(StringKey.HUD_ABILITY_READY),
                strings.get(StringKey.HUD_ABILITY_COOLDOWN));
        renderer.setShieldLabel(strings.get(StringKey.HUD_SHIELD_CHARGES));
        renderer.setAbilityName(activeAbilityName());
        seedText = seeds.isExplicit() ? strings.format(StringKey.HUD_SEED, seed) : null;
        shownLanguage = strings.language();
        invalidateBuild();
    }

    /**
     * Forces the next {@link #refreshBuild()} to rebuild the HUD's build strip, whatever the run
     * holds. Needed wherever the HUD's own state was thrown away or its language changed: a new
     * run ({@code HudRenderer.reset}), re-entering the screen ({@code resetAll}) and a live
     * language switch all leave the strip empty or in the wrong words while the run still carries
     * its forced modifiers.
     */
    private void invalidateBuild() {
        shownModifiers = -1;
        shownSynergies = -1;
        shownStreakBonus = -1;
        refreshBuild();
    }

    @Override
    public void onEnter() {
        screens.setLetterboxRgb(renderer.palette().letterbox());
        renderer.resetAll();
        invalidateBuild();
        if (context != null) {
            holdToFlap = context.settings().holdToFlap;
        }
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    @Override
    public void tick(InputFrame input) {
        if (run.isFinished()) {
            // The game-over overlay is on its way onto the stack; freeze until it takes over.
            return;
        }
        if (handleInterrupts(input)) {
            return;
        }
        RunInput intent = readIntent(input);
        TickReport report = run.tick(intent);
        boolean flapped = report.has(TickFact.Flapped.class);
        boolean crashed = report.has(TickFact.Crashed.class);
        if (refusalQuietTicks > 0) {
            refusalQuietTicks--;
        }
        if (intent.ability() && !report.has(TickFact.AbilityActivated.class) && isLive()) {
            refuseAbility();
        }
        renderer.tick(run, flapped, crashed);
        if (toasts != null) {
            toasts.tick();
        }
        publishFacts(report, flapped, crashed);
        refreshBuild();
        if (run.phase() == RunPhase.CHOOSING_MODIFIER) {
            // The director froze the simulation on this tick; the overlay takes over the frozen
            // ticks from here (M6, D11) and pops itself when the run is flying again.
            screens.push(new ModifierChoiceOverlay(screens, strings, run, this::tickDraft));
            return;
        }
        if (run.isFinished() && !gameOverShown) {
            gameOverShown = true;
            RunResult result = run.result();
            publish(new GameEvent.RunEnded(result.gatesPassed(),
                    (int) Math.round(result.stats().points()),
                    result.stats().ticksAlive(), result.stats().objectiveMet()));
            ProgressionOutcome outcome = applyProgression(result);
            screens.push(new GameOverOverlay(screens, result, outcome, this::restart, renderer,
                    strings).withProfile(context == null ? null : context.profile(),
                    context == null ? null : context.progressionRules()));
        }
    }

    /**
     * One frozen tick of an open draft (M6, D11), driven by {@link ModifierChoiceOverlay} because
     * the {@link ScreenManager} only ticks the top of the stack.
     *
     * <p>It is deliberately the same path as a flying tick minus the world: the run is ticked
     * once, its facts are published on the bus (so a taken card and an activated synergy are heard
     * by the audio manager and the toast layer like everything else), the toasts age and the HUD
     * build is refreshed. What it does <em>not</em> do is advance the renderers' world animations —
     * {@code tickFrozen} moves only the clouds, exactly as the game-over strip does — because the
     * simulation itself has not moved: the draft costs the run no tick, no flap and no obstacle.
     *
     * @param choice the card index the player took, {@link RunInput#NO_CHOICE} while the overlay
     *     is still open, or {@link RunInput#SKIP} to close it with nothing
     */
    void tickDraft(int choice) {
        if (run.isFinished()) {
            return;
        }
        TickReport report = run.tick(new RunInput(false, false, choice, false));
        renderer.tickFrozen();
        if (toasts != null) {
            toasts.tick();
        }
        publishFacts(report, false, false);
        refreshBuild();
    }

    /**
     * Brings the HUD's build strip and streak-bonus readout in line with the run (M6, D26, D27).
     *
     * <p>Called after every tick and cheap on the common one: the strings are rebuilt only when
     * the number of taken modifiers, the number of active synergies or the coins a streak step
     * pays actually changed, which during a run happens at most once per draft. That is what keeps
     * a steady frame allocation-free while still letting a drafted card show up immediately.
     */
    private void refreshBuild() {
        ModifierDirector draft = run.simulation().modifiers();
        Map<String, Integer> stacks = draft.stacks();
        List<String> synergies = draft.activeSynergies();
        // Counted in takes, not in distinct cards: a second stack of something the run already
        // holds changes a chip's label without changing how many chips there are.
        int takes = run.stats().modifiersTaken().size();
        long perStep = streakCoins() + run.stats().modifierStreakCoins();
        if (takes == shownModifiers && synergies.size() == shownSynergies
                && perStep == shownStreakBonus) {
            return;
        }
        shownModifiers = takes;
        shownSynergies = synergies.size();
        shownStreakBonus = perStep;
        List<String> chips = new ArrayList<>(stacks.size());
        for (Map.Entry<String, Integer> entry : stacks.entrySet()) {
            String name = ProgressionText.name(strings, ContentKind.MODIFIER, entry.getKey());
            chips.add(entry.getValue() > 1
                    ? strings.format(StringKey.HUD_MODIFIER_STACK, name, entry.getValue())
                    : name);
        }
        List<String> bonuses = new ArrayList<>(synergies.size());
        for (int i = 0; i < synergies.size(); i++) {
            bonuses.add(ProgressionText.name(strings, ContentKind.SYNERGY, synergies.get(i)));
        }
        renderer.setBuild(chips, bonuses);
        renderer.setStreakBonusText(perStep > 0
                ? strings.format(StringKey.HUD_STREAK_BONUS, perStep) : "");
    }

    /**
     * What one clean-gate streak step pays before the run's own modifiers add to it
     * ({@code economy.rewards.streak.coins}, E32.a).
     *
     * @return the coins, 0 in a session with no content
     */
    private long streakCoins() {
        if (context != null && context.content() != null && context.content().economy() != null) {
            return context.content().economy().rewards().streak().coins();
        }
        return 0;
    }

    /**
     * Turns the tick's facts into presentation events (D16, E31.b): gameplay never imports the
     * bus, so this is the one seam where a simulation fact becomes something the audio manager,
     * the toast layer or the debug overlay can hear.
     *
     * <p>Facts are published in the order the simulation produced them, so a shield absorb is
     * heard before the crash it prevented. Coins and the streak fire from M3 on, the four ability
     * facts from M5, synergies from M6; rule shifts are M7 — but the mapping is written once,
     * here, so a milestone that adds the fact does not have to remember to add its sound.
     *
     * <p>Four facts stay deliberately unmapped: the draft trio ({@code ModifierOffered},
     * {@code ModifierChosen}, {@code ModifierSkipped}), which the modifier overlay owns and
     * renders itself, and the boss trio, whose event carries the boss id that lives in the world
     * definition from M8.
     */
    private void publishFacts(TickReport report, boolean flapped, boolean crashed) {
        if (context == null) {
            return;
        }
        if (flapped) {
            publish(new GameEvent.Flapped(false));
        }
        List<TickFact> facts = report.facts();
        for (int i = 0; i < facts.size(); i++) {
            TickFact fact = facts.get(i);
            if (fact instanceof TickFact.GatePassed gate) {
                publish(new GameEvent.GatePassed(run.stats().gatesPassed(), gate.clean()));
            } else if (fact instanceof TickFact.NearMiss) {
                publish(new GameEvent.NearMiss(run.stats().gatesPassed()));
            } else if (fact instanceof TickFact.CoinCollected coin) {
                publish(new GameEvent.CoinCollected(coin.value(),
                        run.stats().coinsCollected()));
            } else if (fact instanceof TickFact.StreakChanged streak) {
                publish(new GameEvent.StreakChanged(streak.streak(),
                        run.stats().streakSteps()));
            } else if (fact instanceof TickFact.ShieldAbsorbed) {
                publish(new GameEvent.ShieldAbsorbed(
                        remaining(StatId.SHIELD_CHARGES, run.stats().shieldAbsorbs())));
            } else if (fact instanceof TickFact.Revived) {
                publish(new GameEvent.Revived(
                        remaining(StatId.REVIVES, run.stats().revives())));
            } else if (fact instanceof TickFact.AbilityActivated used) {
                publish(new GameEvent.AbilityActivated(used.abilityId(),
                        levelOf(used.abilityId())));
            } else if (fact instanceof TickFact.AbilityReady ready) {
                publish(new GameEvent.AbilityReady(ready.abilityId()));
            } else if (fact instanceof TickFact.SynergyActivated synergy) {
                publish(new GameEvent.SynergyActivated(synergy.id()));
                announceSynergy(synergy.id());
            } else if (fact instanceof TickFact.RuleShift) {
                publish(new GameEvent.RuleShift(activeRuleFlags()));
            }
        }
        if (crashed) {
            publish(new GameEvent.Crashed(causeOf(report), run.stats().gatesPassed()));
        }
    }

    /**
     * Says out loud that a build came together (D27): the HUD grows a chip and the toast names the
     * set bonus, because a chip appearing in the corner is not an announcement.
     *
     * <p>The toast layer renders under the draft overlay, which is exactly when synergies
     * activate: the card that completed the set was taken one frozen tick earlier.
     *
     * @param synergyId the set bonus that just activated
     */
    private void announceSynergy(String synergyId) {
        if (toasts == null) {
            return;
        }
        toasts.push(strings.format(StringKey.TOAST_SYNERGY,
                ProgressionText.name(strings, ContentKind.SYNERGY, synergyId)));
    }

    /**
     * The owned level of an equipped ability, for the activation event.
     *
     * @param abilityId the ability
     * @return the level, or 1 when the id is not an equipped ability (the shield reports its
     *     regenerated charge under its own id and is not always an equipped ability at all — it
     *     can be a bare {@code SHIELD_CHARGES} from an upgrade node)
     */
    private int levelOf(String abilityId) {
        AbilityInstance instance = run.simulation().abilities().instance(abilityId);
        return instance == null ? 1 : instance.level();
    }

    /**
     * Writes the finished run into the profile (D14) and announces what it paid (D29).
     *
     * <p>Called from the one tick that moves the run to {@code FINISHED}, before the overlay is
     * pushed, so a retry can never lose the rewards. {@link ProgressionManager#apply} is itself
     * guarded against a second call with the same result; the {@code gameOverShown} flag above
     * means it is never reached twice anyway.
     *
     * @param result the finished run
     * @return what changed, or {@code null} when the session has no profile to write into
     */
    private ProgressionOutcome applyProgression(RunResult result) {
        if (context == null || !context.canProgress()) {
            return null;
        }
        PlayerProfile profile = context.profile();
        ProgressionManager progression = context.progression();
        ProgressionRules rules = context.progressionRules();
        ProgressionOutcome outcome = progression.apply(profile, result, rules, multipliers());
        lastOutcome = outcome;
        publishProgress(profile, outcome);
        context.saveProfile();
        return outcome;
    }

    /**
     * The multipliers the run was played under (E32.a): the two stats off the run's own sheet,
     * the tier's reward multiplier off its setup, and the daily multiplier off the economy — the
     * formula applies the last one only to a run in {@code DAILY} mode.
     *
     * @return the multipliers
     */
    private ProgressionRules.RewardMultipliers multipliers() {
        double daily = 1;
        if (context.content() != null && context.content().economy() != null) {
            daily = context.content().economy().daily().rewardMult();
        }
        return new ProgressionRules.RewardMultipliers(
                run.simulation().stats().resolve(StatId.COIN_MULT),
                run.simulation().stats().resolve(StatId.XP_MULT),
                run.setup().tier().rewardMult(), daily);
    }

    /**
     * Turns one progression pass into bus events and the toasts that go with them (D14, E31.b):
     * {@code progression} may not import the bus, so this is the seam where a credited coin
     * becomes something the audio manager and the toast layer can hear.
     *
     * @param profile the profile the pass wrote into (it holds the new totals)
     * @param outcome what the pass changed
     */
    private void publishProgress(PlayerProfile profile, ProgressionOutcome outcome) {
        RewardSummary rewards = outcome.rewardSummary();
        Map<String, Long> credited = new LinkedHashMap<>();
        String currency = context.progressionRules().primaryCurrency();
        if (rewards.coins() != 0) {
            credited.merge(currency, rewards.coins(), Long::sum);
        }
        for (Map.Entry<String, Long> grant : outcome.levelRewardsGranted().entrySet()) {
            credited.merge(grant.getKey(), grant.getValue(), Long::sum);
        }
        for (Map.Entry<String, Long> change : credited.entrySet()) {
            Long total = profile.wallet.get(change.getKey());
            publish(new GameEvent.CurrencyChanged(change.getKey(), change.getValue(),
                    total == null ? 0 : total));
        }
        if (rewards.xp() != 0) {
            publish(new GameEvent.XpGained(rewards.xp(), profile.xp));
        }
        for (Integer level : outcome.levelUps()) {
            publish(new GameEvent.LevelUp(level));
            if (toasts != null) {
                toasts.push(strings.format(StringKey.TOAST_LEVEL_UP, level));
            }
        }
        for (String id : outcome.achievementsUnlocked()) {
            publish(new GameEvent.AchievementUnlocked(id));
        }
        for (String id : outcome.unlocksGranted()) {
            publish(new GameEvent.UnlockGranted(id));
        }
        String challengeId = run.config().challengeId();
        if (outcome.challengeFirstCompleted() && challengeId != null) {
            publish(new GameEvent.ChallengeCompleted(challengeId, true));
        }
        if (outcome.dailyRecorded()) {
            publish(new GameEvent.DailyRecorded(profile.daily.date, run.stats().gatesPassed()));
        }
    }

    /**
     * What the last finished run paid.
     *
     * @return the outcome, {@link ProgressionOutcome#EMPTY} while a run is in flight or when the
     *     session has no profile
     */
    public ProgressionOutcome lastOutcome() {
        return lastOutcome;
    }

    /**
     * Charges of a consumable stat left after this run has spent some of them.
     *
     * @param stat the stat holding the granted amount
     * @param used how many the run has consumed
     * @return the remainder, never negative
     */
    private int remaining(StatId stat, int used) {
        int granted = (int) Math.round(run.simulation().stats().resolve(stat));
        return Math.max(0, granted - used);
    }

    /**
     * The rule flags in force, as the names the event carries.
     *
     * @return the names, in the enum's order
     */
    private List<String> activeRuleFlags() {
        Set<RuleFlag> flags = run.simulation().rules().flags();
        List<String> names = new ArrayList<>(flags.size());
        for (RuleFlag flag : flags) {
            names.add(flag.name());
        }
        return names;
    }

    private static String causeOf(TickReport report) {
        List<TickFact> facts = report.facts();
        for (int i = 0; i < facts.size(); i++) {
            if (facts.get(i) instanceof TickFact.Crashed crash) {
                return crash.cause().name();
            }
        }
        return "";
    }

    private RunInput readIntent(InputFrame input) {
        boolean flap = input.isJustPressed(InputAction.FLAP)
                || input.isMouseJustPressed(Keys.BUTTON_LEFT);
        boolean ability = input.isJustPressed(InputAction.ABILITY)
                || input.isMouseJustPressed(Keys.BUTTON_RIGHT);
        boolean auto = holdToFlap && input.isHeld(InputAction.FLAP);
        return new RunInput(flap, ability, RunInput.NO_CHOICE, auto);
    }

    /**
     * The name of the equipped active ability, translated for the HUD badge (D9, D17).
     *
     * <p>It is read from the run rather than from the profile on purpose: the rules of a run strip
     * what they forbid when it starts ({@code AbilityManager.create}), so a badge built from the
     * selection would promise an ability the player cannot use. A stripped or empty slot returns
     * the empty string, which is what hides the badge.
     *
     * @return the name, or the empty string when the run has no active ability
     */
    private String activeAbilityName() {
        AbilityInstance active = run.simulation().abilities().active();
        return active == null ? ""
                : ProgressionText.name(strings, ContentKind.ABILITY, active.id());
    }

    /**
     * The cue for an ability press that did nothing (M5): the HUD badge blinks red and, at most
     * once every {@value #REFUSAL_QUIET_TICKS} ticks, a toast says why.
     *
     * <p>The blink is the immediate answer and costs nothing; the toast is rate-limited because
     * the ability key is exactly the key a player mashes when a gate is closing, and three
     * identical toasts would cover the gate they were mashing at.
     */
    private void refuseAbility() {
        renderer.hud().flashAbilityRefused();
        abilityRefusal = abilityRefusalText();
        if (toasts == null || refusalQuietTicks > 0) {
            return;
        }
        refusalQuietTicks = REFUSAL_QUIET_TICKS;
        toasts.push(abilityRefusal, Toast.Kind.WARNING);
    }

    /**
     * Why the last ability press did nothing, in the words the toast used.
     *
     * @return the sentence, empty until a press was refused in the run being played
     */
    public String lastAbilityRefusal() {
        return abilityRefusal;
    }

    /**
     * Why the ability press did nothing, in words (D9).
     *
     * @return the translated sentence
     */
    private String abilityRefusalText() {
        AbilityManager abilities = run.simulation().abilities();
        AbilityInstance active = abilities.active();
        if (active != null) {
            return active.maxCharges() > 0 && active.charges() == 0
                    ? strings.get(StringKey.TOAST_ABILITY_NO_CHARGE)
                    : strings.get(StringKey.TOAST_ABILITY_COOLDOWN);
        }
        for (String id : abilities.strippedIds()) {
            AbilityDef def = equippedDef(id);
            if (def == null || def.kind() != AbilityKind.ACTIVE) {
                continue;
            }
            RuleFlag flag = ProgressionText.strippedBy(def, run.simulation().rules());
            if (flag != null) {
                return strings.format(StringKey.TOAST_ABILITY_BLOCKED,
                        ProgressionText.ruleName(strings, flag));
            }
        }
        return strings.get(StringKey.TOAST_ABILITY_NONE);
    }

    /**
     * The definition of an ability the run was built with.
     *
     * @param id the ability id
     * @return the definition, or {@code null} when the run does not carry it
     */
    private AbilityDef equippedDef(String id) {
        List<AbilityDef> loadout = run.setup().abilities();
        for (int i = 0; i < loadout.size(); i++) {
            if (loadout.get(i).id().equals(id)) {
                return loadout.get(i);
            }
        }
        return null;
    }

    /**
     * Handles the events that take the screen out of play before the run is ticked.
     *
     * @return {@code true} when the run must not be ticked this tick
     */
    private boolean handleInterrupts(InputFrame input) {
        boolean escape = input.isJustPressed(InputAction.PAUSE);
        boolean lostAttention = false;
        List<RawInput.SystemEvent> events = input.systemEvents();
        for (int i = 0; i < events.size(); i++) {
            RawInput.SystemEvent e = events.get(i);
            if (e instanceof RawInput.Iconified ic) {
                lostAttention |= ic.iconified();
            } else if (e instanceof RawInput.FocusLost && !screens.isFullscreenHandshake()) {
                lostAttention = true;
            }
        }
        if (run.phase() == RunPhase.READY && escape) {
            UiCues.back();
            screens.pop();
            return true;
        }
        if (isLive() && (escape || lostAttention)) {
            screens.push(new PauseOverlay(screens, strings));
            return true;
        }
        return false;
    }

    /**
     * Whether the run is being flown right now, which is what {@code Esc}, a lost focus and a
     * refused ability all key off.
     *
     * <p>{@code BREATHER} is the second live phase M6 added: the simulation runs exactly as in
     * {@code FLYING} while the draft waits for clear air, which measures 241 ticks at the median
     * — 4 s — of every schedule entry. Leaving it out would make 9.9 % of a drafting run
     * un-pausable and lethal to an alt-tab, which is precisely what D2 forbids.
     *
     * @return {@code true} in {@code FLYING} and {@code BREATHER}
     */
    private boolean isLive() {
        return run.phase() == RunPhase.FLYING || run.phase() == RunPhase.BREATHER;
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        renderer.render(g, alpha, run, seedText, screens.isDebugOverlayVisible());
        if (toasts != null) {
            toasts.render(g);
        }
    }
}
