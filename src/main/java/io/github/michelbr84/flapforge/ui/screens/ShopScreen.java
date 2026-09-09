package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.app.GameContext;
import io.github.michelbr84.flapforge.content.ContentKind;
import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
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
import io.github.michelbr84.flapforge.render.ParticleSystem;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.FocusRing;
import io.github.michelbr84.flapforge.ui.Screen;
import io.github.michelbr84.flapforge.ui.ScreenManager;
import io.github.michelbr84.flapforge.ui.UiCues;
import io.github.michelbr84.flapforge.ui.UiNode;
import io.github.michelbr84.flapforge.ui.component.CardGrid;
import io.github.michelbr84.flapforge.ui.component.CtaButton;
import io.github.michelbr84.flapforge.ui.component.CurrencyDisplay;
import io.github.michelbr84.flapforge.ui.component.HubHeader;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import io.github.michelbr84.flapforge.ui.component.NavBar;
import io.github.michelbr84.flapforge.ui.component.SectionNav;
import io.github.michelbr84.flapforge.ui.component.TabBar;
import io.github.michelbr84.flapforge.ui.component.Toast;
import io.github.michelbr84.flapforge.ui.component.ToastLayer;
import io.github.michelbr84.flapforge.ui.component.Tooltip;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The shop (D13, M4; rebuilt in the hub's language in M12): everything coins can buy, in one
 * place, as a catalogue rather than a list of leftovers.
 *
 * <p>The catalogue is derived, never authored. Something belongs here exactly when its condition
 * tree carries a {@code purchase} branch — that is what {@link UnlockEvaluator#priceOf} answers,
 * and the same rule is what makes a bird "150 coins" on the selection screen. What the player
 * starts with is a {@code default} unlock and was never for sale, so it never appears; what the
 * player has <em>bought</em> stays, badged as owned, because a shop that hides what you own
 * cannot tell you what a collection is missing. The four tabs group what the rule produces:
 * birds and their colours, abilities, worlds, and the features and upgrade trees.
 *
 * <p>Each entry says one of four things, and says it once:
 *
 * <ul>
 *   <li><b>a price</b> on a wooden tag — for sale, and the wallet covers it;</li>
 *   <li>the same price under a veil — for sale, the wallet is short. There is no padlock: in this
 *       game everything for sale is <em>also</em> earnable, so nothing here is ever shut. The
 *       second line carries the free road instead ("Play 3 runs"), which is the honest answer to
 *       "why is this not mine yet";</li>
 *   <li><b>a check and "Owned"</b> — bought, and therefore priceless;</li>
 *   <li><b>a level and a price</b> — an ability the player owns, sold one level at a time (D9,
 *       E3) through {@link UpgradeManager#buyAbilityLevel}, or the word "Cap reached" /
 *       "Fully upgraded" when there is no level left to sell.</li>
 * </ul>
 *
 * <p>Level 1 of an ability comes with its unlock, so an owned ability leaves the unlock pass and
 * returns as a level offer — one id, one card, never two.
 *
 * <p>The price is on the card and nowhere else: the detail plaque under the grid carries what the
 * card has no room for — the description, the free road, what the next level would do — and the
 * gold call to action names the verb of the tab (Buy bird, Unlock world, Upgrade ability) without
 * repeating the number. That is the one rule the old screen broke in three places.
 *
 * <p>The grid scrolls by moving its cards, not the canvas, so every node stays in screen
 * coordinates and one focus ring can run from the tabs through the cards to the call to action
 * and down into the navigation. A card scrolled out of the band is clipped away and stops
 * answering the pointer ({@link ShopCard#setViewport}).
 *
 * <p>Some of what is for sale may not be usable yet: {@link GameContent#playable} reports which
 * kinds have systems behind them today (E19), and an offer whose kind does not says so. Every
 * shipped id is buyable and playable today, so the table is empty and stays for the next feature
 * that lands ahead of its system.
 */
public final class ShopScreen implements Screen {

    /** Side margin. */
    public static final int MARGIN = 12;
    /** Width of the content column. */
    public static final int CONTENT_W = Playfield.WIDTH - 2 * MARGIN;
    /** Top of the category bar. */
    public static final int TABS_TOP = 52;
    /** Height of the category bar. */
    public static final int TABS_H = 32;
    /** First visible row of the card grid. */
    public static final int GRID_TOP = 90;
    /** Last visible row of the card grid. */
    public static final int GRID_BOTTOM = 446;
    /** Columns of cards. */
    public static final int COLUMNS = 2;
    /** Height of one card. */
    public static final int CARD_H = 104;
    /** Horizontal gap between cards. */
    public static final int GAP_X = 10;
    /** Vertical gap between cards. */
    public static final int GAP_Y = 10;
    /** Width of one card. */
    public static final int CARD_W = (CONTENT_W - GAP_X) / COLUMNS;
    /** Top of the detail plaque. */
    public static final int DETAIL_TOP = 452;
    /** Height of the detail plaque: room for the name and three lines under it. */
    public static final int DETAIL_H = 72;
    /** Top of the call to action. */
    public static final int CTA_TOP = 530;
    /** Width of the call to action. */
    public static final int CTA_W = 340;
    /** Height of the call to action. */
    public static final int CTA_H = 44;
    /** Logical pixels one wheel notch scrolls. */
    public static final int WHEEL_STEP = 38;

    /** Tab id: birds and their colours. */
    public static final String TAB_BIRDS = "birds";
    /** Tab id: abilities. */
    public static final String TAB_ABILITIES = "abilities";
    /** Tab id: worlds, tiers and challenges. */
    public static final String TAB_WORLDS = "worlds";
    /** Tab id: upgrade trees and features. */
    public static final String TAB_FEATURES = "features";

    private static final WorldPalette PALETTE = WorldPalette.GREEN_FIELDS;
    private static final int SCROLLBAR_X = Playfield.WIDTH - 8;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_MIN_H = 20;
    private static final Color SCROLLBAR = new Color(0xF4, 0xF8, 0xF8, 0x50);
    private static final Color WARN = new Color(0xE8, 0x5A, 0x4A);
    private static final int DETAIL_LINE_H = 14;
    private static final int DETAIL_FIRST_BASELINE = 18;

    /**
     * Which milestone each not-yet-playable kind arrives in (E19). Features are not here: they
     * differ per id and are answered by {@link GameContent#featureMilestone}. Abilities (M5),
     * worlds (M7), challenges and achievements (M8) have all left the table; it stays so a
     * later build that stages a kind again has its slot.
     */
    private static final Map<ContentKind, String> MILESTONES = Map.of();

    private final GameContext context;
    private final ScreenManager screens;
    private final Strings strings;
    private final GameContent content;
    private final PlayerProfile profile;
    private final UnlockManager unlocks;
    private final UpgradeManager upgrades;
    private final UnlockEvaluator evaluator;
    private final ToastLayer toasts;
    private final FocusRing ring = new FocusRing();
    private final HubHeader header;
    private final TabBar tabs = new TabBar();
    private final CardGrid offers = new CardGrid();
    private final NavBar nav = new NavBar();
    private final CtaButton cta;
    private final Tooltip tooltip = new Tooltip();
    private final List<Offer> shown = new ArrayList<>();
    private final List<String> detailLines = new ArrayList<>();
    private final Ellipse2D.Double coinScratch = new Ellipse2D.Double();
    private final IconPainter coinIcon = AbilityIcons.coin(coinScratch);
    private String currentId;
    private String shownLanguage;
    private String emptyText = "";
    private double scroll;
    private long ticks;
    private boolean reduceShown;

    /**
     * Creates the screen for a wired application.
     *
     * @param context the application services
     */
    public ShopScreen(GameContext context) {
        this(Objects.requireNonNull(context, "context"), context.screens(),
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
        this(null, screens, strings, content, profile, unlocks, null, toasts);
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
        this(null, screens, strings, content, profile, unlocks, upgrades, toasts);
    }

    private ShopScreen(GameContext context, ScreenManager screens, Strings strings,
            GameContent content, PlayerProfile profile, UnlockManager unlocks,
            UpgradeManager upgrades, ToastLayer toasts) {
        this.context = context;
        this.screens = Objects.requireNonNull(screens, "screens");
        this.strings = Objects.requireNonNull(strings, "strings");
        this.content = Objects.requireNonNull(content, "content");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.unlocks = unlocks;
        this.upgrades = upgrades;
        this.evaluator = UnlockEvaluator.of(content);
        this.toasts = toasts == null ? new ToastLayer() : toasts;

        // The wallet lives in the header and nowhere else; from inside the shop the chip has no
        // shop to open, so it is a readout rather than a control the focus ring stops on.
        header = new HubHeader(null);
        header.setOutline(ProceduralArt.letterboxColor(PALETTE));

        tabs.setBounds(MARGIN, TABS_TOP, CONTENT_W, TABS_H);
        tabs.setAccented(true);
        for (String id : List.of(TAB_BIRDS, TAB_ABILITIES, TAB_WORLDS, TAB_FEATURES)) {
            tabs.add(id, "").setIcon(ShopArt.tab(id));
        }
        tabs.setOnChange(index -> {
            scroll = 0;
            rebuild();
        });
        offers.setColumns(COLUMNS);
        offers.setCellHeight(CARD_H);
        offers.setGap(GAP_X, GAP_Y);

        cta = new CtaButton("", this::activateCta);
        cta.setBounds((Playfield.WIDTH - CTA_W) / 2.0, CTA_TOP, CTA_W, CTA_H);

        SectionNav.build(nav, SectionNav.SHOP, new SectionNav.Routes(this::focusFirstOffer,
                this::openBirds, this::play, this::openForge, this::openGoals));
        nav.button(SectionNav.BIRDS).setEnabled(context != null);
        nav.button(SectionNav.PLAY).setEnabled(context != null);
        nav.button(SectionNav.FORGE).setEnabled(context != null);
        nav.button(SectionNav.GOALS).setEnabled(context != null);

        shownLanguage = strings.language();
        refreshTexts();
    }

    // ------------------------------------------------------------------ accessors

    /**
     * The category bar.
     *
     * @return the bar
     */
    public TabBar tabBar() {
        return tabs;
    }

    /**
     * The cards of the open tab.
     *
     * @return the grid
     */
    public CardGrid offerGrid() {
        return offers;
    }

    /**
     * The catalogue of the open tab, what is for sale first.
     *
     * @return an unmodifiable snapshot
     */
    public List<Offer> offers() {
        return List.copyOf(shown);
    }

    /**
     * One entry of the open tab.
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
     * The header band, with the screen's title and the wallet.
     *
     * @return the header
     */
    public HubHeader header() {
        return header;
    }

    /**
     * The bottom navigation, with Shop on the gold plate.
     *
     * @return the bar
     */
    public NavBar nav() {
        return nav;
    }

    /**
     * The one call to action, whose verb is the open tab's.
     *
     * @return the button
     */
    public CtaButton ctaButton() {
        return cta;
    }

    /**
     * The wallet readout.
     *
     * @return the display
     */
    public CurrencyDisplay walletDisplay() {
        return header.display();
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
     * The lines of the detail plaque.
     *
     * @return an unmodifiable snapshot
     */
    public List<String> detailLines() {
        return List.copyOf(detailLines);
    }

    /**
     * The message shown when the open tab has nothing left to sell.
     *
     * @return the sentence, empty while the tab still has something for sale
     */
    public String emptyText() {
        return emptyText;
    }

    /**
     * The id of the entry the detail plaque and the call to action are about.
     *
     * @return the namespaced id, or {@code null} when the tab is empty
     */
    public String currentId() {
        return currentId;
    }

    /**
     * How far the grid is scrolled.
     *
     * @return the offset in logical pixels
     */
    public double scroll() {
        return scroll;
    }

    /**
     * How far the grid can be scrolled.
     *
     * @return the largest offset, {@code 0} when the tab fits
     */
    public double maxScroll() {
        return Math.max(0, CardGrid.heightFor(offers.size(), COLUMNS, CARD_H, GAP_Y)
                - (GRID_BOTTOM - GRID_TOP));
    }

    /**
     * Scrolls an entry into the visible band and gives it the focus.
     *
     * <p>A card outside the band is clipped away and does not answer the pointer, so this is how
     * a caller — and a test that clicks — reaches one.
     *
     * @param unlockId the namespaced id
     * @return {@code true} when the tab shows that entry
     */
    public boolean revealOffer(String unlockId) {
        CardGrid.Card card = offers.card(unlockId);
        if (card == null) {
            return false;
        }
        ring.focus(card);
        currentId = card.id();
        scrollFocusIntoView();
        refreshState();
        return true;
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
        header.setTitle(strings.get(StringKey.SHOP_TITLE));
        header.display().setFormat(strings.get(StringKey.HUD_COINS));
        tabs.tabs().get(0).setLabel(strings.get(StringKey.SHOP_TAB_BIRDS));
        tabs.tabs().get(1).setLabel(strings.get(StringKey.SHOP_TAB_ABILITIES));
        tabs.tabs().get(2).setLabel(strings.get(StringKey.SHOP_TAB_WORLDS));
        tabs.tabs().get(3).setLabel(strings.get(StringKey.SHOP_TAB_FEATURES));
        nav.button(SectionNav.SHOP).setText(strings.get(StringKey.MENU_SHOP));
        nav.button(SectionNav.BIRDS).setText(strings.get(StringKey.MENU_BIRDS));
        nav.button(SectionNav.PLAY).setText(strings.get(StringKey.MENU_PLAY));
        nav.button(SectionNav.FORGE).setText(strings.get(StringKey.MENU_NAV_FORGE));
        nav.button(SectionNav.GOALS).setText(strings.get(StringKey.MENU_NAV_GOALS));
        shownLanguage = strings.language();
        rebuild();
    }

    /** Rebuilds the cards of the open tab and the focus ring around them. */
    public void rebuild() {
        String keep = currentId;
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
        // What is for sale first, cheapest first; List.sort is stable, so equal prices keep
        // content order (D13). Everything with nothing left to sell — what the profile owns, an
        // ability at its cap — sorts to the end, where a collection belongs.
        shown.sort((a, b) -> a.available() == b.available()
                ? Long.compare(a.cost(), b.cost()) : Boolean.compare(b.available(), a.available()));
        for (Offer offer : shown) {
            ShopCard card = new ShopCard(offer.id(), "", null);
            card.setOnAction(() -> buy(offer.id()));
            card.setArt(ShopArt.of(content, offer.kind(), offer.id()));
            card.setViewport(GRID_TOP, GRID_BOTTOM);
            offers.add(card);
        }
        applyScroll();
        offers.registerFocusables(ring);
        ring.add(cta);
        nav.registerFocusables(ring);
        currentId = keep != null && offers.card(keep) != null ? keep
                : shown.isEmpty() ? null : shown.get(0).id();
        refreshState();
    }

    /**
     * Every unlockable that carries a price, owned or not, in content order.
     *
     * <p>An ability the profile owns is sold by the level instead of whole, so it is left out
     * here and comes back from {@link #abilityLevelOffers}: one id is never two cards.
     *
     * @return the entries
     */
    private List<Offer> allOffers() {
        long balance = coins();
        List<Offer> levels = abilityLevelOffers(balance);
        Set<String> levelIds = new HashSet<>();
        for (Offer level : levels) {
            levelIds.add(level.id());
        }
        List<Offer> out = new ArrayList<>();
        for (Map.Entry<String, UnlockConditionDef> entry : evaluator.conditions().entrySet()) {
            String id = entry.getKey();
            long price = UnlockEvaluator.priceOf(entry.getValue());
            if (price < 0 || levelIds.contains(id)) {
                continue;
            }
            ContentKind kind = evaluator.kindOf(id);
            boolean owned = profile.isUnlocked(id);
            out.add(new Offer(id, kind, price, !owned && balance >= price,
                    ProgressionText.unlockableName(strings, content, id), 0, !owned));
        }
        out.addAll(levels);
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

    /** Refreshes every card, the detail plaque, the call to action and the wallet. */
    public void refreshState() {
        long balance = coins();
        boolean anythingForSale = false;
        for (int i = 0; i < shown.size(); i++) {
            Offer offer = shown.get(i);
            ShopCard card = (ShopCard) offers.cards().get(i);
            boolean affordable = offer.available() && balance >= offer.cost();
            anythingForSale |= offer.available();
            card.setTitle(offer.name());
            card.setSubtitle(subtitleOf(offer));
            card.setOwned(isOwnedOutright(offer));
            card.setBadge(offer.available() ? Long.toString(offer.cost()) : closedBadge(offer),
                    offer.available());
            card.setDimmed(!affordable && offer.available());
            card.setSelected(offer.id().equals(currentId));
            card.setTooltip(tooltipFor(offer, affordable));
        }
        emptyText = anythingForSale ? "" : strings.get(StringKey.SHOP_EMPTY);
        header.display().setAmount(balance);
        buildDetail();
        refreshCta();
    }

    /**
     * Whether an entry is something the profile has bought outright, as opposed to an ability
     * being sold by the level.
     *
     * <p>An unlock entry is only ever unavailable because it is owned — nothing else takes one
     * off the market — which is why this needs no extra flag on the record.
     *
     * @param offer the entry
     * @return {@code true} when the card should wear the check instead of a price
     */
    private static boolean isOwnedOutright(Offer offer) {
        return !offer.available() && offer.kind() != ContentKind.ABILITY;
    }

    /**
     * The second line of a card: the free road while the thing is not owned, what it is once it
     * is, and the level and cap for an ability.
     *
     * @param offer the entry
     * @return the line
     */
    private String subtitleOf(Offer offer) {
        if (offer.kind() == ContentKind.ABILITY && abilityOf(offer) != null
                && ownedLevel(abilityOf(offer)) > 0) {
            AbilityDef def = abilityOf(offer);
            return ProgressionText.abilityLevel(strings, def, ownedLevel(def)) + " - "
                    + strings.format(StringKey.SHOP_ABILITY_CAP,
                            UpgradeManager.abilityLevelCap(profile, content));
        }
        String milestone = milestoneOf(offer);
        if (milestone != null) {
            return strings.format(StringKey.COMMON_SOON, milestone);
        }
        if (offer.available()) {
            String earn = earnText(offer);
            if (!earn.isEmpty()) {
                return earn;
            }
        }
        return offer.kind() == null ? "" : kindLabel(offer.kind());
    }

    /**
     * The road that costs no coins, in words: the branch of the condition tree the profile is
     * closest to finishing, ignoring the branch that is simply "pay for it".
     *
     * @param offer the entry
     * @return the phrase, empty when paying is the only way in
     */
    private String earnText(Offer offer) {
        UnlockConditionDef condition = evaluator.conditionOf(offer.id());
        UnlockConditionDef branch = evaluator.nearestBranch(condition, profile, true);
        if (branch == null || branch.type() == UnlockType.PURCHASE) {
            return "";
        }
        return ProgressionText.unlockText(strings, content, branch, profile);
    }

    /**
     * The badge of an entry with nothing left to sell: owned, every level owned, or the E3 cap.
     *
     * @param offer the entry
     * @return the word
     */
    private String closedBadge(Offer offer) {
        AbilityDef def = abilityOf(offer);
        if (def == null) {
            return strings.get(StringKey.COMMON_OWNED);
        }
        return ownedLevel(def) >= def.levels().size()
                ? strings.get(StringKey.SHOP_ABILITY_MAXED)
                : strings.get(StringKey.SHOP_ABILITY_CAPPED);
    }

    /**
     * The ability an offer is about.
     *
     * @param offer the entry
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
     * @param offer the entry
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
     * The tooltip of an entry: what it is, what it costs and whether it can be paid for.
     *
     * @param offer the entry
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
     * The description of what an entry sells.
     *
     * @param offer the entry
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

    /**
     * Fills the detail plaque from the focused entry.
     *
     * <p>The price is deliberately absent: it is on the card, and the plaque exists for what the
     * card has no room for.
     */
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
        if (offer.isAbilityLevel()) {
            detailLines.add(strings.format(StringKey.SHOP_NEXT_LEVEL_EFFECT,
                    ProgressionText.abilityEffects(strings, abilityOf(offer), offer.level())));
        } else if (!offer.available()) {
            detailLines.add(closedBadge(offer));
        } else {
            String earn = earnText(offer);
            if (!earn.isEmpty()) {
                detailLines.add(strings.format(StringKey.SHOP_EARN, earn));
            }
        }
        if (offer.available() && coins() < offer.cost()) {
            detailLines.add(strings.get(StringKey.SHOP_CANNOT_AFFORD));
        }
        String milestone = milestoneOf(offer);
        if (milestone != null) {
            detailLines.add(strings.format(StringKey.COMMON_SOON, milestone));
        }
    }

    /** Points the call to action at the focused entry, with the verb of the open tab. */
    private void refreshCta() {
        Offer offer = currentId == null ? null : offer(currentId);
        if (offer == null) {
            cta.setText(strings.get(StringKey.SHOP_EMPTY));
            cta.setSubtitle("");
            cta.setIcon(null);
            cta.setEnabled(false);
            return;
        }
        cta.setSubtitle(offer.name());
        if (!offer.available()) {
            cta.setText(closedBadge(offer));
            cta.setIcon(AbilityIcons.CHECK);
            cta.setEnabled(false);
            return;
        }
        cta.setText(strings.get(verbOf(offer)));
        cta.setIcon(coinIcon);
        cta.setEnabled(true);
    }

    /**
     * The verb of one entry: what pressing the call to action would do, in the words of its tab.
     *
     * @param offer the entry
     * @return the string key of the verb
     */
    private static StringKey verbOf(Offer offer) {
        if (offer.isAbilityLevel()) {
            return StringKey.SHOP_CTA_ABILITY_UPGRADE;
        }
        if (offer.kind() == null) {
            return StringKey.SHOP_CTA_FEATURE;
        }
        switch (offer.kind()) {
            case BIRD:
                return StringKey.SHOP_CTA_BIRD;
            case COSMETIC:
                return StringKey.COMMON_BUY;
            case ABILITY:
                return StringKey.SHOP_CTA_ABILITY;
            case WORLD:
            case TIER:
            case CHALLENGE:
                return StringKey.SHOP_CTA_WORLD;
            default:
                return StringKey.SHOP_CTA_FEATURE;
        }
    }

    // ------------------------------------------------------------------ scrolling

    /** Places the grid at the current scroll offset, clamped to what the tab needs. */
    private void applyScroll() {
        double contentHeight = CardGrid.heightFor(offers.size(), COLUMNS, CARD_H, GAP_Y);
        scroll = MathUtil.clamp(scroll, 0,
                Math.max(0, contentHeight - (GRID_BOTTOM - GRID_TOP)));
        offers.setBounds(MARGIN, GRID_TOP - scroll, CONTENT_W, contentHeight);
        offers.layout();
    }

    /**
     * Scrolls by a delta and re-places the cards.
     *
     * @param delta logical pixels, positive to move the content up
     */
    private void scrollBy(double delta) {
        scroll += delta;
        applyScroll();
    }

    /** Brings the focused card fully inside the band, if it is a card at all. */
    private void scrollFocusIntoView() {
        if (!(ring.focused() instanceof ShopCard card)) {
            return;
        }
        if (card.y() < GRID_TOP) {
            scrollBy(card.y() - GRID_TOP);
        } else if (card.y() + card.height() > GRID_BOTTOM) {
            scrollBy(card.y() + card.height() - GRID_BOTTOM);
        }
    }

    // ------------------------------------------------------------------ actions

    /**
     * Buys one entry and says what happened.
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

    /** What the one call to action does: buy the entry the plaque is describing. */
    private void activateCta() {
        if (currentId != null) {
            buy(currentId);
        }
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

    // ------------------------------------------------------------------ navigation

    /** Puts the focus on the first card (the Shop item of the navigation: you are here). */
    private void focusFirstOffer() {
        if (!shown.isEmpty()) {
            revealOffer(shown.get(0).id());
        }
    }

    /** Opens the bird selection, replacing this section rather than stacking on top of it. */
    private void openBirds() {
        if (context != null) {
            screens.replace(new BirdSelectionScreen(context));
        }
    }

    /** Opens the upgrade trees, replacing this section. */
    private void openForge() {
        if (context != null) {
            screens.replace(new UpgradeTreeScreen(context));
        }
    }

    /** Opens the goals, replacing this section. */
    private void openGoals() {
        if (context != null) {
            screens.replace(new GoalsScreen(context));
        }
    }

    /**
     * Starts the run the hub would start. The mode picker lives on the Birds screen, so the shop
     * plays the standard run in the profile's own world and tier.
     */
    private void play() {
        if (context == null) {
            return;
        }
        SeededRunSource source = new ContentRunFactory(content, RunMode.STANDARD, () -> profile);
        screens.push(new GameScreen(context, source, SeedSequence.random()));
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onEnter() {
        ring.resetTransition();
        ring.focus(tabs);
        screens.setLetterboxRgb(PALETTE.letterbox());
        tooltip.hide();
        header.display().setAmountNow(coins());
        if (!strings.language().equals(shownLanguage)) {
            refreshTexts();
        } else {
            rebuild();
        }
    }

    @Override
    public void tick(InputFrame input) {
        ticks++;
        toasts.tick();
        header.tick();
        applyReduceFlashing(ParticleSystem.defaultReduceFlashing());
        cta.setTicks(ticks);
        InputFrame frame = input;
        if (tabs.tick(input)) {
            frame = input.withoutPresses(EnumSet.of(InputAction.LEFT, InputAction.RIGHT));
        }
        UiNode before = ring.focused();
        ring.handle(frame);
        UiNode focused = ring.focused();
        if (focused != before && focused instanceof CardGrid.Card card) {
            currentId = card.id();
            scrollFocusIntoView();
            refreshState();
        }
        if (input.wheel() != 0 && input.mouseY() >= GRID_TOP && input.mouseY() <= GRID_BOTTOM) {
            scrollBy(-input.wheel() * (double) WHEEL_STEP);
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

    /**
     * Fans a change of the accessibility setting out to the one thing on this screen that pulses.
     *
     * @param reduce whether luminance pulses are capped
     */
    private void applyReduceFlashing(boolean reduce) {
        if (reduce == reduceShown) {
            return;
        }
        reduceShown = reduce;
        cta.setReduceFlashing(reduce);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Graphics2D g, double alpha) {
        ProceduralArt.prepare(g);
        ProceduralArt.fillBackground(g, PALETTE);
        header.render(g);
        tabs.render(g);

        Shape unclipped = g.getClip();
        g.clipRect(0, GRID_TOP, Playfield.WIDTH, GRID_BOTTOM - GRID_TOP);
        offers.render(g);
        g.setClip(unclipped);
        renderScrollbar(g);
        if (!emptyText.isEmpty() && shown.isEmpty()) {
            g.setFont(Fonts.regular(14));
            g.setColor(ProceduralArt.TEXT_MUTED);
            TextPainter.drawCentered(g, emptyText, Playfield.WIDTH / 2.0, GRID_TOP + 40.0);
        }

        ProceduralArt.plaque(g, MARGIN - 4, DETAIL_TOP, CONTENT_W + 8, DETAIL_H,
                ProceduralArt.ButtonState.NORMAL);
        double baseline = DETAIL_TOP + (double) DETAIL_FIRST_BASELINE;
        for (int i = 0; i < detailLines.size(); i++) {
            String line = detailLines.get(i);
            g.setFont(i == 0 ? Fonts.bold(13) : Fonts.regular(11));
            g.setColor(i == 0 ? ProceduralArt.TEXT_LIGHT
                    : line.equals(strings.get(StringKey.SHOP_CANNOT_AFFORD)) ? WARN
                            : ProceduralArt.TEXT_MUTED);
            Shape textClip = g.getClip();
            g.clipRect(MARGIN, DETAIL_TOP, CONTENT_W, DETAIL_H);
            TextPainter.draw(g, TextPainter.ellipsise(g, line, CONTENT_W - 8), MARGIN + 4.0,
                    baseline);
            g.setClip(textClip);
            baseline += i == 0 ? 16 : DETAIL_LINE_H;
        }

        cta.render(g);
        nav.render(g);
        tooltip.render(g);
        toasts.render(g, HubHeader.HEIGHT);
    }

    /**
     * Draws the thumb that says the grid has more below, and only then.
     *
     * @param g the context
     */
    private void renderScrollbar(Graphics2D g) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackH = GRID_BOTTOM - GRID_TOP;
        double contentHeight = CardGrid.heightFor(offers.size(), COLUMNS, CARD_H, GAP_Y);
        int thumbH = (int) Math.max(SCROLLBAR_MIN_H, trackH * (trackH / contentHeight));
        int thumbY = (int) Math.round(GRID_TOP + (trackH - thumbH) * (scroll / max));
        g.setColor(SCROLLBAR);
        g.fillRoundRect(SCROLLBAR_X, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W, SCROLLBAR_W);
    }

    /**
     * One entry of the shop: an unlockable that carries a price — owned or not — or the next
     * level of an ability the player already owns (E3).
     *
     * @param id the namespaced unlockable id; for an ability level, the ability's own
     *     {@code ability:<id>} — an ability is either locked (an unlock entry) or owned (a level
     *     entry), never both, so the id stays unambiguous
     * @param kind what kind of thing it is, or {@code null} when the content does not say
     * @param cost the price in coins, {@code 0} when there is nothing left to buy
     * @param affordable whether the wallet held the price when the tab was built
     * @param name the translated name
     * @param level the ability level this entry buys, {@code 0} for an unlock entry and for an
     *     ability with nothing left to buy
     * @param available whether there is anything left to buy — {@code false} for something the
     *     profile owns, and for an ability at its last level or at {@code profile.abilityLevelCap}
     */
    public record Offer(String id, ContentKind kind, long cost, boolean affordable, String name,
            int level, boolean available) {

        /**
         * Whether this entry buys an ability level rather than an unlock.
         *
         * @return {@code true} for a level entry
         */
        public boolean isAbilityLevel() {
            return level > 0;
        }
    }
}
