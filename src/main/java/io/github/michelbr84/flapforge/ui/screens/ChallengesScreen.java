package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.ListView;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The seven challenges (D17, M8), reachable from the main menu.
 *
 * <p>One list, in file order, and a detail block for the selection: the name and description the
 * content ships, the world the run takes place in (E6 — the world is <em>labelled</em> here, it is
 * never an unlock requirement), the tier, the special rules in words (the flags via
 * {@link ProgressionText#ruleName}, the starting modifiers, a fixed corridor, a boss), the
 * objective, the player's record and the rewards the first completion pays. A challenge the
 * profile has not unlocked shows its unlock condition instead of a Play prompt.
 *
 * <p>Play builds a {@link ChallengeRunSource} — the challenge's own world, tier, curve, rules,
 * forced pattern and boss, played with the profile's current loadout — and pushes a
 * {@link GameScreen} over this one. Whether the objective was met is told after the run, on the
 * game-over strip and the summary.
 *
 * <p>A session without a profile (a bare screen stack in a test) shows the list with every
 * challenge locked and no run source, which keeps the screen renderable everywhere the menu can
 * show it.
 */
public final class ChallengesScreen implements Screen {

    /** Top of the challenge list. */
    public static final int LIST_TOP = 56;
    /** Height of the challenge list. */
    public static final int LIST_H = 132;
    /** Top of the detail block. */
    public static final int DETAIL_TOP = 202;
    /** Height of one detail row. */
    public static final int ROW_H = 18;
    /** Top of the footer buttons. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 56;
    /** Height of the footer buttons. */
    public static final int FOOTER_BUTTON_H = 42;
    /** Left edge of the content. */
    public static final int CONTENT_X = 24;
    /** Width of the content. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * CONTENT_X;

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 40;
    private static final int NAME_BASELINE = DETAIL_TOP + 16;

    private final ScreenManager screens;
    private final GameContext context;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final FocusRing ring = new FocusRing();
    private final ListView list;
    private final Button play;
    private final Button back;
    private final List<ChallengeDef> challenges;
    private String shownLanguage;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public ChallengesScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(), context,
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile());
    }

    /**
     * Creates a stand-alone screen (tests and tools): it can describe every challenge but cannot
     * start a run, because it has no application services behind it.
     *
     * @param screens the screen stack
     * @param strings the string table its labels come from
     * @param content the loaded content
     * @param profile the profile to read, or {@code null} for one that has unlocked nothing
     */
    public ChallengesScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile) {
        this(screens, null, strings, content, profile);
    }

    private ChallengesScreen(ScreenManager screens, GameContext context, Strings strings,
            GameContent content, PlayerProfile profile) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.context = context;
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = profile;
        this.challenges = List.copyOf(content.challenges().all());
        this.list = new ListView(strings.get(StringKey.CHALLENGES_TITLE), listOptions(), 0);
        this.list.setFontSize(14);
        this.list.setBounds(CONTENT_X, LIST_TOP, CONTENT_W, LIST_H);
        this.list.setOnChange(index -> refreshPlay());
        this.play = new Button("", this::startChallenge);
        this.play.setFontSize(16);
        this.play.setBounds(CONTENT_X, FOOTER_TOP, CONTENT_W / 2.0 - 6, FOOTER_BUTTON_H);
        this.back = new Button(strings.get(StringKey.COMMON_BACK), screens::pop);
        this.back.setFontSize(16);
        this.back.setBounds(CONTENT_X + CONTENT_W / 2.0 + 6, FOOTER_TOP,
                CONTENT_W / 2.0 - 6, FOOTER_BUTTON_H);
        ring.add(list);
        ring.add(play);
        ring.add(back);
        refreshTexts();
    }

    private List<String> listOptions() {
        List<String> out = new ArrayList<>(challenges.size());
        for (int i = 0; i < challenges.size(); i++) {
            ChallengeDef def = challenges.get(i);
            String name = ProgressionText.name(strings, ContentKind.CHALLENGE, def.id());
            out.add(isUnlocked(def) ? name
                    : strings.format(StringKey.CHALLENGES_LOCKED_ENTRY, name));
        }
        return out;
    }

    private boolean isUnlocked(ChallengeDef def) {
        return profile != null && profile.isUnlocked(def.unlockableId());
    }

    /**
     * The challenge the list currently points at.
     *
     * @return the definition
     */
    public ChallengeDef selected() {
        int index = Math.max(0, Math.min(list.selectedIndex(), challenges.size() - 1));
        return challenges.get(index);
    }

    /**
     * The run source Play would start, built for the current selection with the live profile —
     * the challenge's own configuration with the loadout the player has selected and bought.
     *
     * @return the source, or {@code null} when the session cannot play (no context, no profile,
     *     or a locked challenge)
     */
    public ChallengeRunSource playSource() {
        if (context == null || profile == null || !isUnlocked(selected())) {
            return null;
        }
        return new ChallengeRunSource(content, context::profile, selected().id());
    }

    private void startChallenge() {
        ChallengeRunSource source = playSource();
        if (source == null) {
            return;
        }
        UiCues.select();
        screens.push(new GameScreen(context, source, SeedSequence.random()));
    }

    private void refreshPlay() {
        play.setText(strings.get(isUnlocked(selected())
                ? StringKey.CHALLENGES_PLAY : StringKey.CHALLENGES_LOCKED_TITLE));
    }

    /**
     * The detail rows the screen draws for the selection ("label value" lines, screenshots and
     * assertions), name and description first, unlock line last when locked.
     *
     * @return the lines in display order
     */
    public List<String> detailTexts() {
        ChallengeDef def = selected();
        List<String> out = new ArrayList<>(9);
        out.add(ProgressionText.name(strings, ContentKind.CHALLENGE, def.id()));
        out.add(ProgressionText.description(strings, ContentKind.CHALLENGE, def.id()));
        out.add(strings.format(StringKey.CHALLENGES_WORLD,
                ProgressionText.name(strings, ContentKind.WORLD, def.world())));
        out.add(strings.format(StringKey.CHALLENGES_TIER,
                ProgressionText.name(strings, ContentKind.TIER, def.tier())));
        out.add(strings.format(StringKey.CHALLENGES_RULES, rulesLine(def)));
        out.add(strings.format(StringKey.CHALLENGES_OBJECTIVE, objectiveText(def)));
        out.add(recordLine(def));
        out.add(strings.format(StringKey.CHALLENGES_REWARDS, rewardsLine(def)));
        if (!isUnlocked(def)) {
            out.add(strings.format(StringKey.CHALLENGES_LOCKED,
                    ProgressionText.unlockText(strings, content, def.unlock(), profile)));
        }
        return out;
    }

    /**
     * The challenge's special rules in words: its flags, its starting modifiers, a fixed
     * corridor and its boss, in that order; the standard-rules line when none apply.
     *
     * @param def the challenge
     * @return the line
     */
    private String rulesLine(ChallengeDef def) {
        List<String> parts = new ArrayList<>(4);
        for (int i = 0; i < def.flags().size(); i++) {
            parts.add(ProgressionText.ruleName(strings, def.flags().get(i)));
        }
        for (int i = 0; i < def.forcedModifiers().size(); i++) {
            parts.add(strings.format(StringKey.CHALLENGES_RULE_MODIFIER,
                    ProgressionText.name(strings, ContentKind.MODIFIER,
                            def.forcedModifiers().get(i))));
        }
        if (def.forcedPattern() != null) {
            parts.add(strings.get(StringKey.CHALLENGES_RULE_PATTERN));
        }
        if (def.boss() != null) {
            parts.add(strings.format(StringKey.CHALLENGES_RULE_BOSS, def.boss().atGate()));
        }
        if (parts.isEmpty()) {
            return strings.get(StringKey.CHALLENGES_RULES_NONE);
        }
        return join(parts);
    }

    /**
     * The objective in words with its number substituted ("Survive 30 gates").
     *
     * @param def the challenge
     * @return the text
     */
    private String objectiveText(ChallengeDef def) {
        long value = def.objective().value();
        switch (def.objective().type()) {
            case SURVIVE_GATES:
                return strings.format(StringKey.OBJECTIVE_SURVIVE_GATES, value);
            case SURVIVE_TICKS:
                return strings.format(StringKey.OBJECTIVE_SURVIVE_TICKS, value);
            case COLLECT_COINS:
                return strings.format(StringKey.OBJECTIVE_COLLECT_COINS, value);
            case REACH_POINTS:
                return strings.format(StringKey.OBJECTIVE_REACH_POINTS, value);
            case BOSS_CLEARED:
            default:
                return strings.get(StringKey.OBJECTIVE_BOSS_CLEARED);
        }
    }

    private String recordLine(ChallengeDef def) {
        PlayerProfile.ChallengeRecord record = profile == null
                ? null : profile.challenges.get(def.id());
        if (record == null || (!record.completed && record.attempts == 0)) {
            return strings.get(StringKey.CHALLENGES_RECORD_NONE);
        }
        String text = strings.format(StringKey.CHALLENGES_RECORD, record.bestGates,
                record.attempts);
        return record.completed
                ? strings.format(StringKey.CHALLENGES_COMPLETED_ENTRY, text) : text;
    }

    private String rewardsLine(ChallengeDef def) {
        List<String> parts = new ArrayList<>(3);
        if (def.rewardsOrNone().coins() > 0) {
            parts.add(strings.format(StringKey.CHALLENGES_REWARD_COINS,
                    def.rewardsOrNone().coins()));
        }
        List<String> unlocks = def.rewardsOrNone().unlocks();
        for (int i = 0; i < unlocks.size(); i++) {
            parts.add(ProgressionText.unlockableName(strings, content, unlocks.get(i)));
        }
        return parts.isEmpty() ? strings.get(StringKey.COMMON_NONE) : join(parts);
    }

    /** Comma-joined parts, with the plain comma neither string table translates. */
    private String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out.toString();
    }

    /**
     * The challenge list.
     *
     * @return the list
     */
    public ListView list() {
        return list;
    }

    /**
     * The Play button.
     *
     * @return the button
     */
    public Button playButton() {
        return play;
    }

    /**
     * The Back button.
     *
     * @return the button
     */
    public Button backButton() {
        return back;
    }

    /**
     * The focus ring (tests inspecting focus).
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /** Re-reads every visible label from the string table (a language switch, D25). */
    public void refreshTexts() {
        int selected = list.selectedIndex();
        list.setLabel(strings.get(StringKey.CHALLENGES_TITLE));
        list.setOptions(listOptions());
        list.selectQuietly(selected);
        refreshPlay();
        back.setText(strings.get(StringKey.COMMON_BACK));
        shownLanguage = strings.language();
    }

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(list);
        screens.setLetterboxRgb(PALETTE.letterbox());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
    }

    @Override
    public void tick(InputFrame input) {
        ring.handle(input);
        list.tick(input);
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.CHALLENGES_TITLE), CONTENT_X,
                TITLE_BASELINE, Align.LEFT, ProceduralArt.TEXT_LIGHT,
                ProceduralArt.letterboxColor(PALETTE), 2);

        list.render(g);
        renderDetail(g);
        play.render(g);
        back.render(g);
        ring.render(g);
    }

    private void renderDetail(Graphics2D g) {
        List<String> lines = detailTexts();
        double y = NAME_BASELINE;
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                g.setFont(Fonts.bold(17));
                g.setColor(ProceduralArt.accentColor(PALETTE));
            } else {
                g.setFont(Fonts.regular(13));
                g.setColor(i == 1 ? ProceduralArt.TEXT_MUTED : ProceduralArt.TEXT_LIGHT);
            }
            TextPainter.draw(g, lines.get(i), CONTENT_X, y);
            y += i == 0 ? ROW_H + 4 : ROW_H;
        }
    }
}
