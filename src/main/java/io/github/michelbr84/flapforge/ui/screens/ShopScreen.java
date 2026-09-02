package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.input.InputAction;
import io.github.michelbr84.flapforge.input.InputFrame;
import io.github.michelbr84.flapforge.progression.PlayerProfile;
import io.github.michelbr84.flapforge.progression.PurchaseResult;
import io.github.michelbr84.flapforge.progression.PurchaseStatus;
import io.github.michelbr84.flapforge.progression.UnlockEvaluator;
import io.github.michelbr84.flapforge.progression.UnlockManager;
import io.github.michelbr84.flapforge.progression.UpgradeManager;
import io.github.michelbr84.flapforge.progression.Wallet;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.Button;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.TabBar;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The shop (D13, M4): everything the content lets a player buy with coins, in one place.
 *
 * <p>The list is derived, never authored. An unlockable belongs here exactly when its condition
 * tree carries a {@code purchase} branch and the profile does not own it yet — that is what
 * {@link UnlockEvaluator#priceOf} answers, and the same rule is what makes a bird "150 coins" in
 * the selection screen. The four tabs group what comes out of that rule: birds and their colours,
 * abilities, worlds and the tiers and challenges around them, and the features and upgrade trees.
 * Inside a tab the cheapest offer comes first, ties keeping content order, so the tab a beginner
 * opens starts with something they can afford.
 *
 * <p>Buying goes through {@link UnlockManager#purchase} — check, debit, grant, propagate, save, all
 * or nothing — and raises a toast either way: the name of what was bought, or the reason it was
 * refused. A refusal changes nothing, which is why the card can stay exactly where it is.
 *
 * <p>The abilities tab sells two different things (D9, E3). A locked ability is an unlock like any
 * other, bought whole for the price of its {@code purchase} branch; an ability the player already
 * owns is sold <em>by the level</em> instead, one level at a time, through
 * {@link UpgradeManager#buyAbilityLevel}. Level 1 comes with the unlock, so the two are mutually
 * exclusive and one card id serves both. An ability at the last level the content ships, or at
 * {@code profile.abilityLevelCap} — the E3 cap, base 2, raised only by the single
 * {@code ability_cap} grant in the forge tree — keeps its card and loses its price: "Level 3/3"
 * and "Cap reached" are what the player opened the tab to find out.
 *
 * <p>Some of what is for sale cannot be used yet: {@link GameContent#playable} reports which kinds
 * have systems behind them today (E19), and an offer whose kind does not says so on its card
 * ("Arrives in M5"). It is still bought and still counts for the unlock graph — a world bought now
 * is a world already owned when M7 lands.
 */
public final class ShopScreen implements Screen {

    /** Side margin. */
    public static final int MARGIN = 12;
    /** Width of the content column. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * MARGIN;
    /** Top of the tab bar. */
    public static final int TABS_TOP = 46;
    /** Height of the tab bar. */
    public static final int TABS_H = 28;
    /** Top of the offer list. */
    public static final int OFFERS_TOP = 84;
    /** Height of one offer card. */
    public static final int OFFER_H = 42;
    /** Gap between offer cards. */
    public static final int OFFER_GAP = 5;
    /** How many offers fit on the screen at once. */
    public static final int MAX_OFFERS = 8;
    /** Lowest top of the detail panel; a short tab lifts it under the offers it explains. */
    public static final int DETAIL_TOP = 442;
    /** Height of the detail panel. */
    public static final int DETAIL_H = 126;
    /** Top of the Back button. */
    public static final int FOOTER_TOP = Playfield.HEIGHT - 52;
    /** Height of the Back button. */
    public static final int FOOTER_H = 40;

    /** Tab id: birds and their colours. */
    public static final String TAB_BIRDS = "birds";
    /** Tab id: abilities. */
    public static final String TAB_ABILITIES = "abilities";
    /** Tab id: worlds, tiers and challenges. */
    public static final String TAB_WORLDS = "worlds";
    /** Tab id: upgrade trees and features. */
    public static final String TAB_FEATURES = "features";

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int TITLE_BASELINE = 34;
    private static final int WALLET_W = 130;

    /**
     * Which milestone each not-yet-playable kind arrives in (E19). Features are not here: they
     * differ per id and are answered by {@link GameContent#featureMilestone}.
     */
    private static final Map<ContentKind, String> MILESTONES = Map.of(
            ContentKind.ABILITY, "M5",
            ContentKind.WORLD, "M7",
            ContentKind.CHALLENGE, "M8",
            ContentKind.ACHIEVEMENT, "M8");

    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final UnlockManager unlocks;
    private final UpgradeManager upgrades;
    private final UnlockEvaluator evaluator;
    private final ToastLayer toasts;
    private final FocusRing ring = new FocusRing();
    private final TabBar tabs = new TabBar();
    private final CardGrid offers = new CardGrid();
    private final CurrencyDisplay wallet = new CurrencyDisplay();
    private final Tooltip tooltip = new Tooltip();
    private final Button back;
    private final List<Offer> shown = new ArrayList<>();
    private final List<String> detailLines = new ArrayList<>();
    private String currentId;
    private String shownLanguage;
    private String emptyText = "";
    private double detailTop = DETAIL_TOP;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public ShopScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context").screens(),
                context.strings() != null ? context.strings() : Strings.active(),
                context.content(), context.profile(),
                context.canProgress()
                        ? new UnlockManager(context.progression(), context::saveProfile) : null,
                context.canProgress()
                        ? new UpgradeManager(context.progression(), context::saveProfile) : null,
                context.toasts());
    }

    /**
     * Creates the screen.
     *
     * @param screens the screen stack
     * @param strings the string table
     * @param content the loaded content
     * @param profile the profile to charge
     * @param unlocks the purchase path, or {@code null} for a screen that cannot buy
     * @param toasts the toast queue, or {@code null} for one of its own
     */
    public ShopScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, UnlockManager unlocks, ToastLayer toasts) {
        this(screens, strings, content, profile, unlocks, null, toasts);
    }

    /**
     * Creates the screen with both purchase paths.
     *
     * @param screens the screen stack
     * @param strings the string table
     * @param content the loaded content
     * @param profile the profile to charge
     * @param unlocks the unlock purchase path, or {@code null} for a screen that cannot buy
     * @param upgrades the ability-level purchase path (E3), or {@code null} for a screen that
     *     shows the levels without selling them
     * @param toasts the toast queue, or {@code null} for one of its own
     */
    public ShopScreen(ScreenManager screens, Strings strings, GameContent content,
            PlayerProfile profile, UnlockManager unlocks, UpgradeManager upgrades,
            ToastLayer toasts) {
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.unlocks = unlocks;
        this.upgrades = upgrades;
        this.evaluator = UnlockEvaluator.of(content);
        this.toasts = toasts == null ? new ToastLayer() : toasts;

        tabs.setBounds(MARGIN, TABS_TOP, CONTENT_W, TABS_H);
        tabs.add(TAB_BIRDS, "");
        tabs.add(TAB_ABILITIES, "");
        tabs.add(TAB_WORLDS, "");
        tabs.add(TAB_FEATURES, "");
        tabs.setOnChange(index -> rebuild());
        offers.setColumns(1);
        offers.setCellHeight(OFFER_H);
        offers.setGap(0, OFFER_GAP);
        offers.setBounds(MARGIN, OFFERS_TOP, CONTENT_W,
                CardGrid.heightFor(MAX_OFFERS, 1, OFFER_H, OFFER_GAP));
        back = new Button("", screens::pop);
        back.setFontSize(16);
        back.setBounds(MARGIN, FOOTER_TOP, CONTENT_W, FOOTER_H);
        wallet.setBounds(Playfield.WIDTH - WALLET_W - 14.0, 14, WALLET_W, 26);
        wallet.setAlign(Align.RIGHT);
        wallet.setAmountNow(coins());
        shownLanguage = strings.language();
        refreshTexts();
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The tab bar.
     *
     * @return the bar
     */
    public TabBar tabBar() {
        return tabs;
    }

    /**
     * The offer cards of the open tab.
     *
     * @return the grid
     */
    public CardGrid offerGrid() {
        return offers;
    }

    /**
     * The offers of the open tab, cheapest first.
     *
     * @return an unmodifiable snapshot
     */
    public List<Offer> offers() {
        return List.copyOf(shown);
    }

    /**
     * One offer of the open tab.
     *
     * @param unlockId the namespaced id
     * @return the offer, or {@code null} when the tab does not show it
     */
    public Offer offer(String unlockId) {
        for (Offer offer : shown) {
            if (offer.id().equals(unlockId)) {
                return offer;
            }
        }
        return null;
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
     * The wallet readout.
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return wallet;
    }

    /**
     * The tooltip.
     *
     * @return the tooltip
     */
    public Tooltip tooltip() {
        return tooltip;
    }

    /**
     * The focus ring.
     *
     * @return the ring
     */
    public FocusRing focusRing() {
        return ring;
    }

    /**
     * The lines of the detail panel.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> detailLines() {
        return List.copyOf(detailLines);
    }

    /**
     * The message shown when the open tab has nothing left to sell.
     *
     * @return the sentence, empty when the tab has offers
     */
    public String emptyText() {
        return emptyText;
    }

    /**
     * The coins the profile holds.
     *
     * @return the balance
     */
    private long coins() {
        return Wallet.of(profile).balance(PlayerProfile.CURRENCY_COINS);
    }

    // ------------------------------------------------------------------ building

    /** Re-reads every label from the string table (a language switch, D25). */
    public void refreshTexts() {
        wallet.setFormat(strings.get(StringKey.HUD_COINS));
        back.setText(strings.get(StringKey.COMMON_BACK));
        tabs.tabs().get(0).setLabel(strings.get(StringKey.SHOP_TAB_BIRDS));
        tabs.tabs().get(1).setLabel(strings.get(StringKey.SHOP_TAB_ABILITIES));
        tabs.tabs().get(2).setLabel(strings.get(StringKey.SHOP_TAB_WORLDS));
        tabs.tabs().get(3).setLabel(strings.get(StringKey.SHOP_TAB_FEATURES));
        shownLanguage = strings.language();
        rebuild();
    }

    /** Rebuilds the offer cards of the open tab and the focus ring around them. */
    public void rebuild() {
        shown.clear();
        offers.clear();
        ring.clear();
        ring.add(tabs);
        String tab = tabs.selectedId();
        for (Offer offer : allOffers()) {
            if (tabOf(offer.kind()).equals(tab)) {
                shown.add(offer);
            }
        }
        // Cheapest first; List.sort is stable, so equal prices keep content order (D13). An
        // offer with nothing left to sell — a maxed or capped ability level — has no price to
        // compare, so it goes to the end rather than to the front with a cost of zero.
        shown.sort((a, b) -> a.available() == b.available()
                ? Long.compare(a.cost(), b.cost()) : Boolean.compare(b.available(), a.available()));
        for (Offer offer : shown) {
            CardGrid.Card card = new CardGrid.Card(offer.id(), "", null);
            card.setOnAction(() -> buy(offer.id()));
            offers.add(card);
        }
        double gridHeight = CardGrid.heightFor(offers.size(), 1, OFFER_H, OFFER_GAP);
        offers.setBounds(MARGIN, OFFERS_TOP, CONTENT_W, gridHeight);
        offers.layout();
        detailTop = Math.min(DETAIL_TOP, OFFERS_TOP + gridHeight + 14);
        offers.registerFocusables(ring);
        ring.add(back);
        currentId = shown.isEmpty() ? null : shown.get(0).id();
        refreshState();
    }

    /**
     * Every unlockable that is for sale and not owned yet, in content order.
     *
     * @return the offers
     */
    private List<Offer> allOffers() {
        long balance = coins();
        List<Offer> out = new ArrayList<>();
        for (Map.Entry<String, UnlockConditionDef> entry : evaluator.conditions().entrySet()) {
            String id = entry.getKey();
            long price = UnlockEvaluator.priceOf(entry.getValue());
            if (price < 0 || profile.isUnlocked(id)) {
                continue;
            }
            ContentKind kind = evaluator.kindOf(id);
            out.add(new Offer(id, kind, price, balance >= price,
                    ProgressionText.unlockableName(strings, content, id), 0, true));
        }
        out.addAll(abilityLevelOffers(balance));
        return out;
    }

    /**
     * The ability levels for sale (D9, E3): one entry per ability the profile has unlocked.
     *
     * <p>Level 1 comes with the unlock, so an unlocked ability is never an unlock offer any more
     * and always a level offer instead — including when there is nothing left to buy, because
     * "Level 3/3" and "Cap reached" are exactly what the player opened the tab to find out. Those
     * two are marked unavailable and sort to the end.
     *
     * @param balance the coin balance
     * @return the offers, in content order
     */
    private List<Offer> abilityLevelOffers(long balance) {
        List<Offer> out = new ArrayList<>();
        if (!content.has(GameContent.ABILITIES) || !content.playable(ContentKind.ABILITY)) {
            return out;
        }
        int cap = UpgradeManager.abilityLevelCap(profile, content);
        for (AbilityDef def : content.abilities()) {
            int owned = UpgradeManager.abilityLevelOwned(profile, def);
            if (owned <= 0) {
                continue;
            }
            int next = owned + 1;
            boolean available = next <= def.levels().size() && next <= cap;
            long price = available ? def.levels().get(next - 1).cost() : 0;
            out.add(new Offer(def.unlockableId(), ContentKind.ABILITY, price,
                    available && balance >= price,
                    ProgressionText.name(strings, ContentKind.ABILITY, def.id()),
                    available ? next : 0, available));
        }
        return out;
    }

    /**
     * The tab an offer belongs to.
     *
     * @param kind the kind of the unlockable, may be {@code null}
     * @return the tab id
     */
    private static String tabOf(ContentKind kind) {
        if (kind == null) {
            return TAB_FEATURES;
        }
        switch (kind) {
            case BIRD:
            case COSMETIC:
                return TAB_BIRDS;
            case ABILITY:
                return TAB_ABILITIES;
            case WORLD:
            case TIER:
            case CHALLENGE:
                return TAB_WORLDS;
            default:
                return TAB_FEATURES;
        }
    }

    /** Refreshes every card, the detail panel and the wallet from the profile. */
    public void refreshState() {
        long balance = coins();
        emptyText = shown.isEmpty() ? strings.get(StringKey.SHOP_EMPTY) : "";
        for (int i = 0; i < shown.size(); i++) {
            Offer offer = shown.get(i);
            CardGrid.Card card = offers.cards().get(i);
            boolean affordable = offer.available() && balance >= offer.cost();
            card.setTitle(offer.name());
            card.setSubtitle(subtitleOf(offer));
            card.setBadge(offer.available() ? Long.toString(offer.cost()) : closedBadge(offer),
                    offer.available());
            card.setDimmed(!affordable);
            card.setTooltip(tooltipFor(offer, affordable));
        }
        wallet.setAmount(balance);
        buildDetail();
    }

    /**
     * The second line of an offer card: what it is, plus the milestone note when the thing cannot
     * be used yet (E19).
     *
     * @param offer the offer
     * @return the line
     */
    private String subtitleOf(Offer offer) {
        if (offer.isAbilityLevel()) {
            AbilityDef def = abilityOf(offer);
            return ProgressionText.abilityLevel(strings, def, ownedLevel(def)) + " - "
                    + strings.format(StringKey.SHOP_ABILITY_CAP,
                            UpgradeManager.abilityLevelCap(profile, content));
        }
        String milestone = milestoneOf(offer);
        String kindName = offer.kind() == null ? "" : kindLabel(offer.kind());
        return milestone == null ? kindName
                : kindName + " - " + strings.format(StringKey.COMMON_SOON, milestone);
    }

    /**
     * The badge of an offer with nothing left to sell: every level owned, or the E3 cap reached.
     *
     * @param offer the offer
     * @return the word
     */
    private String closedBadge(Offer offer) {
        AbilityDef def = abilityOf(offer);
        return def != null && ownedLevel(def) >= def.levels().size()
                ? strings.get(StringKey.SHOP_ABILITY_MAXED)
                : strings.get(StringKey.SHOP_ABILITY_CAPPED);
    }

    /**
     * The ability an offer is about.
     *
     * @param offer the offer
     * @return the definition, or {@code null} when the offer is not about an ability
     */
    private AbilityDef abilityOf(Offer offer) {
        if (offer.kind() != ContentKind.ABILITY || !content.has(GameContent.ABILITIES)) {
            return null;
        }
        String id = offer.id().substring(ContentKind.ABILITY.namespace().length());
        return content.abilities().contains(id) ? content.abilities().get(id) : null;
    }

    /**
     * The level the profile owns an ability at.
     *
     * @param def the ability, may be {@code null}
     * @return the level, {@code 0} when the ability is unknown or locked
     */
    private int ownedLevel(AbilityDef def) {
        return def == null ? 0 : UpgradeManager.abilityLevelOwned(profile, def);
    }

    /**
     * The milestone an offer's kind arrives in, when it is not playable yet.
     *
     * @param offer the offer
     * @return the milestone name, or {@code null} when the offer works today
     */
    private String milestoneOf(Offer offer) {
        if (offer.kind() == null) {
            return null;
        }
        String bareId = offer.id().substring(offer.kind().namespace().length());
        if (content.playable(offer.kind(), bareId)) {
            return null;
        }
        if (offer.kind() == ContentKind.FEATURE) {
            // Features differ per id: the modifier draft works from M6 and Seeded mode lands in
            // M9, so the note has to come from the feature, not from the kind.
            return GameContent.featureMilestone(bareId);
        }
        return MILESTONES.get(offer.kind());
    }

    /**
     * The name of a kind, as the shop labels one line.
     *
     * @param kind the kind
     * @return the translated label
     */
    private String kindLabel(ContentKind kind) {
        switch (kind) {
            case BIRD:
                return strings.get(StringKey.SHOP_TAB_BIRDS);
            case COSMETIC:
                return strings.get(StringKey.BIRDS_PALETTES);
            case ABILITY:
                return strings.get(StringKey.SHOP_TAB_ABILITIES);
            case WORLD:
                return strings.get(StringKey.SHOP_TAB_WORLDS);
            case TIER:
                return strings.get(StringKey.BIRDS_TIER);
            case TREE:
                return strings.get(StringKey.UPGRADES_TITLE);
            default:
                return strings.get(StringKey.SHOP_TAB_FEATURES);
        }
    }

    /**
     * The tooltip of an offer: what it is, what it costs and whether it can be paid for.
     *
     * @param offer the offer
     * @param affordable whether the wallet holds the price
     * @return the text
     */
    private String tooltipFor(Offer offer, boolean affordable) {
        StringBuilder out = new StringBuilder(descriptionOf(offer));
        if (offer.isAbilityLevel()) {
            AbilityDef def = abilityOf(offer);
            out.append(" - ")
                    .append(strings.format(StringKey.SHOP_ABILITY_NEXT_LEVEL, offer.level()))
                    .append(": ")
                    .append(ProgressionText.abilityEffects(strings, def, offer.level()));
        } else if (!offer.available()) {
            out.append(" - ").append(closedBadge(offer));
        }
        if (offer.available()) {
            out.append(" - ").append(ProgressionText.price(strings, offer.cost()));
        }
        if (!affordable && offer.available()) {
            out.append(" (").append(strings.get(StringKey.SHOP_CANNOT_AFFORD)).append(')');
        }
        String milestone = milestoneOf(offer);
        if (milestone != null) {
            out.append(" - ").append(strings.format(StringKey.COMMON_SOON, milestone));
        }
        return out.toString();
    }

    /**
     * The description of what an offer sells.
     *
     * @param offer the offer
     * @return the translated description, empty when the kind has none
     */
    private String descriptionOf(Offer offer) {
        if (offer.kind() == null) {
            return offer.name();
        }
        AbilityDef ability = abilityOf(offer);
        if (ability != null) {
            // An ability's description carries the numbers of one level (M5), so it must never be
            // read raw: a locked one is described at level 1, the level its unlock grants.
            return ProgressionText.abilityDescription(strings, ability,
                    Math.max(1, ownedLevel(ability)));
        }
        String id = offer.id().substring(offer.kind().namespace().length());
        if (offer.kind() == ContentKind.COSMETIC) {
            id = id.replace(':', '.');
        }
        return ProgressionText.description(strings, offer.kind(), id);
    }

    /** Fills the detail panel from the focused offer. */
    private void buildDetail() {
        detailLines.clear();
        if (currentId == null) {
            if (!emptyText.isEmpty()) {
                detailLines.add(emptyText);
            }
            return;
        }
        Offer offer = offer(currentId);
        if (offer == null) {
            return;
        }
        detailLines.add(offer.name());
        detailLines.add(descriptionOf(offer));
        if (offer.kind() == ContentKind.ABILITY) {
            AbilityDef def = abilityOf(offer);
            if (def != null && ownedLevel(def) > 0) {
                detailLines.add(ProgressionText.abilityLevel(strings, def, ownedLevel(def)));
            }
            detailLines.add(strings.format(StringKey.SHOP_ABILITY_CAP,
                    UpgradeManager.abilityLevelCap(profile, content)));
            if (offer.isAbilityLevel()) {
                detailLines.add(strings.format(StringKey.SHOP_ABILITY_NEXT_LEVEL, offer.level())
                        + ": " + ProgressionText.abilityEffects(strings, def, offer.level()));
            }
        }
        if (offer.available()) {
            detailLines.add(ProgressionText.price(strings, offer.cost()));
            if (coins() < offer.cost()) {
                detailLines.add(strings.get(StringKey.SHOP_CANNOT_AFFORD));
            }
        } else {
            detailLines.add(closedBadge(offer));
        }
        String milestone = milestoneOf(offer);
        if (milestone != null) {
            detailLines.add(strings.format(StringKey.COMMON_SOON, milestone));
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * Buys one offer and says what happened.
     *
     * @param unlockId the namespaced id
     */
    private void buy(String unlockId) {
        currentId = unlockId;
        Offer offer = offer(unlockId);
        if (offer != null && offer.isAbilityLevel()) {
            buyAbilityLevel(offer);
            return;
        }
        if (offer != null && !offer.available()) {
            // Nothing left to sell: the card already says why, and a toast would only repeat it.
            refreshState();
            return;
        }
        if (unlocks == null) {
            refreshState();
            return;
        }
        PurchaseResult result = unlocks.purchase(profile, unlockId, content);
        if (result.ok()) {
            toasts.push(strings.format(StringKey.TOAST_PURCHASED,
                    ProgressionText.unlockableName(strings, content, unlockId)), Toast.Kind.INFO);
            rebuild();
            return;
        }
        toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED, refusal(result.status())),
                Toast.Kind.WARNING);
        refreshState();
    }

    /**
     * Buys the next level of an ability through {@link UpgradeManager#buyAbilityLevel} (E3).
     *
     * @param offer the level offer
     */
    private void buyAbilityLevel(Offer offer) {
        if (upgrades == null) {
            refreshState();
            return;
        }
        AbilityDef def = abilityOf(offer);
        PurchaseResult result = upgrades.buyAbilityLevel(profile,
                def == null ? offer.id() : def.id(), content);
        if (result.ok()) {
            toasts.push(strings.format(StringKey.TOAST_ABILITY_LEVEL, offer.name(),
                    result.level()), Toast.Kind.INFO);
            rebuild();
            return;
        }
        toasts.push(strings.format(StringKey.TOAST_PURCHASE_FAILED, refusal(result.status())),
                Toast.Kind.WARNING);
        refreshState();
    }

    /**
     * Why a purchase was refused, in words.
     *
     * @param status the refusal
     * @return the sentence
     */
    private String refusal(PurchaseStatus status) {
        if (status == PurchaseStatus.INSUFFICIENT_FUNDS) {
            return strings.get(StringKey.SHOP_CANNOT_AFFORD);
        }
        if (status == PurchaseStatus.ALREADY_OWNED) {
            return strings.get(StringKey.COMMON_OWNED);
        }
        if (status == PurchaseStatus.LEVEL_CAPPED) {
            return strings.get(StringKey.SHOP_ABILITY_CAPPED);
        }
        if (status == PurchaseStatus.MAX_LEVEL) {
            return strings.get(StringKey.SHOP_ABILITY_MAXED);
        }
        return strings.get(StringKey.COMMON_LOCKED);
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(tabs);
        screens.setLetterboxRgb(PALETTE.letterbox());
        tooltip.hide();
        wallet.setAmountNow(coins());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            rebuild();
        }
    }

    @Override
    public void tick(InputFrame input) {
        toasts.tick();
        wallet.tick();
        InputFrame frame = input;
        if (tabs.tick(input)) {
            frame = input.withoutPresses(EnumSet.of(InputAction.LEFT, InputAction.RIGHT));
        }
        UiNode before = ring.focused();
        ring.handle(frame);
        UiNode focused = ring.focused();
        if (focused != before && focused instanceof CardGrid.Card card) {
            currentId = card.id();
            buildDetail();
        }
        UiNode under = ring.nodeAt(input.mouseX(), input.mouseY());
        UiNode target = under != null ? under : ring.focused();
        tooltip.update(target, target instanceof CardGrid.Card card ? card.tooltip() : "");
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        }
        if (input.isJustPressed(InputAction.BACK)) {
            UiCues.back();
            screens.pop();
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        g.setFont(Fonts.bold(26));
        TextPainter.drawOutlined(g, strings.get(StringKey.SHOP_TITLE), MARGIN, TITLE_BASELINE,
                Align.LEFT, ProceduralArt.TEXT_LIGHT, ProceduralArt.letterboxColor(PALETTE), 2);
        wallet.render(g);
        tabs.render(g);
        offers.render(g);
        if (!emptyText.isEmpty()) {
            g.setFont(Fonts.regular(14));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawCentered(g, emptyText, Playfield.WIDTH / 2.0, OFFERS_TOP + 40.0);
        }

        int top = (int) Math.round(detailTop);
        ProceduralArt.panel(g, MARGIN - 4, top, CONTENT_W + 8, DETAIL_H);
        double baseline = top + 18.0;
        for (int i = 0; i < detailLines.size(); i++) {
            g.setFont(i == 0 ? Fonts.bold(14) : Fonts.regular(12));
            g.setColor(i == 0 ? ProceduralArt.TEXT_LIGHT : ProceduralArt.TEXT_MUTED);
            TextPainter.draw(g, detailLines.get(i), MARGIN, baseline);
            baseline += i == 0 ? 18 : 15;
        }

        back.render(g);
        tooltip.render(g);
        toasts.render(g);
    }

    /**
     * One line of the shop: an unlockable for sale, or the next level of an ability the player
     * already owns (E3).
     *
     * @param id the namespaced unlockable id; for an ability level, the ability's own
     *     {@code ability:<id>} — an ability is either locked (an unlock offer) or owned (a level
     *     offer), never both, so the id stays unambiguous
     * @param kind what kind of thing it is, or {@code null} when the content does not say
     * @param cost the price in coins, {@code 0} when there is nothing left to buy
     * @param affordable whether the wallet held the price when the tab was built
     * @param name the translated name
     * @param level the ability level this offer buys, {@code 0} for an unlock offer and for an
     *     ability with nothing left to buy
     * @param available whether the offer can be bought at all — {@code false} for an ability at
     *     its last level or at {@code profile.abilityLevelCap}
     */
    public record Offer(String id, ContentKind kind, long cost, boolean affordable, String name,
            int level, boolean available) {

        /**
         * Whether this offer buys an ability level rather than an unlock.
         *
         * @return {@code true} for a level offer
         */
        public boolean isAbilityLevel() {
            return level > 0;
        }
    }
}
