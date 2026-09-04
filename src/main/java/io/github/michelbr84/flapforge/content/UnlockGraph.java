package io.github.michelbr84.flapforge.content;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AchievementDef;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.ChallengeDef;
import io.github.michelbr84.flapforge.content.defs.EconomyDef;
import io.github.michelbr84.flapforge.content.defs.FeatureDef;
import io.github.michelbr84.flapforge.content.defs.GrantDef;
import io.github.michelbr84.flapforge.content.defs.LevelRewardDef;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.TreeDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.content.defs.UpgradeDef;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The unlock graph (D13): every unlockable id, every condition that opens it and every reward or
 * grant that hands it over, as one directed graph.
 *
 * <p>It exists to prove four things about the shipped content, all of them checked by
 * {@link #errors()} and all of them ways a player could otherwise get stuck:
 * <ol>
 *   <li><b>no cycle</b> — nothing may require, however indirectly, something it unlocks;</li>
 *   <li><b>reachability</b> — every unlockable is reachable from the default set (E18), which is
 *       derived here from the data: the ids whose condition is {@code default};</li>
 *   <li><b>a cumulative path</b> — every <em>non-cosmetic</em> unlockable can be reached using
 *       only cumulative conditions ({@code total_gates}, {@code runs}, {@code level},
 *       {@code coins_earned_total}, {@code purchase}), so a player who is not good enough to
 *       clear a boss or a challenge can still get everything by playing more. Cosmetics are
 *       exempt on purpose: a trophy palette is allowed to need the boss;</li>
 *   <li><b>every currency has a source</b> — a currency nothing pays out cannot buy anything.</li>
 * </ol>
 *
 * <p>Edges point <em>from</em> what has to happen first <em>to</em> what it opens. A condition of
 * type {@code challenge} / {@code achievement} / {@code world_cleared} contributes one, and so
 * does every grant: a level reward, a boss reward, a challenge reward, an achievement reward and
 * an {@code UNLOCK} upgrade grant (E31.f — the counter grants are not unlocks and are not edges).
 * Conditions that only read the player's own numbers ({@code runs}, {@code purchase},
 * {@code prestige}, {@code counter} …) are leaves: they need nothing else unlocked first.
 *
 * <p>A reference into a content file that was not supplied is assumed satisfiable rather than
 * broken (E19 staging): an M1-shaped fixture has no {@code challenges.json}, and blaming its bird
 * palettes for it would make every milestone's data fail its own validator.
 */
public final class UnlockGraph {

    /** The condition types a player reaches by playing more, never by playing better (D13). */
    public static final Set<UnlockType> CUMULATIVE = Collections.unmodifiableSet(EnumSet.of(
            UnlockType.TOTAL_GATES, UnlockType.RUNS, UnlockType.LEVEL,
            UnlockType.COINS_EARNED_TOTAL, UnlockType.PURCHASE));

    /** Synthetic node the {@code economy.xp.levelRewards} grants hang from. */
    public static final String LEVEL_REWARDS = "economy:level_rewards";

    /** What {@link #cheapestCumulativePath(String)} returns when there is no such path. */
    public static final long UNREACHABLE_COST = Long.MAX_VALUE;

    /** One unlockable (or one synthetic source) and how it is obtained. */
    public static final class Node {

        private final String id;
        private final ContentKind kind;
        private final UnlockConditionDef condition;
        private final String at;
        private final boolean unlockable;
        private final boolean cosmetic;

        Node(String id, ContentKind kind, UnlockConditionDef condition, String at,
                boolean unlockable, boolean cosmetic) {
            this.id = id;
            this.kind = kind;
            this.condition = condition;
            this.at = at;
            this.unlockable = unlockable;
            this.cosmetic = cosmetic;
        }

        /**
         * The namespaced id.
         *
         * @return the id
         */
        public String id() {
            return id;
        }

        /**
         * The kind of content.
         *
         * @return the kind, or {@code null} for a synthetic node
         */
        public ContentKind kind() {
            return kind;
        }

        /**
         * The condition that opens it.
         *
         * @return the condition, or {@code null} when the node is a leaf source (an achievement,
         *     or a synthetic one)
         */
        public UnlockConditionDef condition() {
            return condition;
        }

        /**
         * Where the node is authored.
         *
         * @return a {@code file#/pointer} location
         */
        public String at() {
            return at;
        }

        /**
         * Whether the id can appear in {@code profile.unlocked} and is therefore subject to the
         * reachability rule.
         *
         * @return {@code true} for a real unlockable
         */
        public boolean unlockable() {
            return unlockable;
        }

        /**
         * Whether the node is a cosmetic, and therefore exempt from the cumulative-path rule.
         *
         * @return {@code true} for a palette
         */
        public boolean cosmetic() {
            return cosmetic;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** One "this opens that" relation. */
    public record Edge(String from, String to, String reason, boolean cumulative, long cost) {

        /**
         * Checks the required fields.
         *
         * @throws NullPointerException when an end or the reason is missing
         */
        public Edge {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** How an unlockable is reached with cumulative conditions only, and what it costs. */
    public record Path(long cost, List<String> steps) {

        /** No cumulative path exists. */
        public static final Path NONE = new Path(UNREACHABLE_COST, List.of());

        /**
         * Copies the step list.
         */
        public Path {
            steps = List.copyOf(steps);
        }

        /**
         * Whether a path was found.
         *
         * @return {@code true} when the target is reachable cumulatively
         */
        public boolean exists() {
            return cost != UNREACHABLE_COST;
        }
    }

    private final GameContent content;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, List<Edge>> incoming = new LinkedHashMap<>();
    private final Set<String> defaults = new LinkedHashSet<>();
    private final Set<String> reachable = new LinkedHashSet<>();
    private final Map<String, Path> cumulative = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();

    private UnlockGraph(GameContent content) {
        this.content = content;
        collectNodes();
        collectEdges();
        findCycles();
        computeReachable();
        computeCumulative();
        checkCurrencies();
    }

    /**
     * Builds the graph of some content.
     *
     * @param content the content
     * @return the graph
     */
    public static UnlockGraph of(GameContent content) {
        return new UnlockGraph(Objects.requireNonNull(content, "content"));
    }

    /**
     * Everything wrong with the graph, in discovery order.
     *
     * @return an unmodifiable list of {@code file#/pointer: message} lines
     */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Every node, in content order.
     *
     * @return an unmodifiable map keyed by id
     */
    public Map<String, Node> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * Every edge, in discovery order.
     *
     * @return an unmodifiable list
     */
    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * The ids a fresh profile owns, derived from the data (E18).
     *
     * @return an unmodifiable set
     */
    public Set<String> defaults() {
        return Collections.unmodifiableSet(defaults);
    }

    /**
     * Whether an id can be reached at all from the default set.
     *
     * @param id the namespaced id
     * @return {@code true} when some chain of conditions and rewards leads to it
     */
    public boolean isReachable(String id) {
        return reachable.contains(id);
    }

    /**
     * The cheapest way to reach an id using cumulative conditions only.
     *
     * @param id the namespaced id
     * @return the path, or {@link Path#NONE} when there is none
     */
    public Path cheapestCumulativePath(String id) {
        Path path = cumulative.get(id);
        return path == null ? Path.NONE : path;
    }

    // ------------------------------------------------------------------ build

    private void collectNodes() {
        List<BirdDef> birds = content.birds().all();
        for (int i = 0; i < birds.size(); i++) {
            BirdDef bird = birds.get(i);
            String at = "birds.json#/" + i;
            add(bird.unlockableId(), ContentKind.BIRD, bird.unlock(), at, true, false);
            for (int p = 0; p < bird.palettes().size(); p++) {
                PaletteDef palette = bird.palettes().get(p);
                add(bird.cosmeticId(palette.id()), ContentKind.COSMETIC, palette.unlock(),
                        at + "/palettes/" + p, true, true);
            }
        }
        List<AbilityDef> abilities = content.abilities().all();
        for (int i = 0; i < abilities.size(); i++) {
            AbilityDef def = abilities.get(i);
            add(def.unlockableId(), ContentKind.ABILITY, def.unlock(),
                    "abilities.json#/abilities/" + i, true, false);
        }
        List<ModifierDef> modifiers = content.modifiers().all();
        for (int i = 0; i < modifiers.size(); i++) {
            ModifierDef def = modifiers.get(i);
            add(def.unlockableId(), ContentKind.MODIFIER, def.unlock(),
                    "modifiers.json#/modifiers/" + i, true, false);
        }
        List<TreeDef> trees = content.trees().all();
        for (int i = 0; i < trees.size(); i++) {
            TreeDef def = trees.get(i);
            add(def.unlockableId(), ContentKind.TREE, def.unlock(), "upgrades.json#/trees/" + i,
                    true, false);
        }
        List<TierDef> tiers = content.tiers().all();
        for (int i = 0; i < tiers.size(); i++) {
            TierDef def = tiers.get(i);
            add(def.unlockableId(), ContentKind.TIER, def.unlock(),
                    "difficulty.json#/tiers/" + i, true, false);
        }
        List<WorldDef> worlds = content.worlds().all();
        for (int i = 0; i < worlds.size(); i++) {
            WorldDef def = worlds.get(i);
            add(def.unlockableId(), ContentKind.WORLD, def.unlock(), "worlds.json#/worlds/" + i,
                    true, false);
        }
        List<ChallengeDef> challenges = content.challenges().all();
        for (int i = 0; i < challenges.size(); i++) {
            ChallengeDef def = challenges.get(i);
            add(def.unlockableId(), ContentKind.CHALLENGE, def.unlock(),
                    "challenges.json#/challenges/" + i, true, false);
        }
        EconomyDef economy = content.economy();
        if (economy != null) {
            List<FeatureDef> features = economy.features();
            for (int i = 0; i < features.size(); i++) {
                FeatureDef def = features.get(i);
                add(def.unlockableId(), ContentKind.FEATURE, def.unlock(),
                        "economy.json#/features/" + i, true, false);
            }
        }
        // Achievements are sources, not unlockables (D13 has no achievement: namespace): they can
        // be required and they can pay, but nothing has to be able to "buy" one, and their
        // conditions are counters, which are leaves.
        List<AchievementDef> achievements = content.achievements().all();
        for (int i = 0; i < achievements.size(); i++) {
            AchievementDef def = achievements.get(i);
            add(def.unlockableId(), ContentKind.ACHIEVEMENT, null,
                    "achievements.json#/achievements/" + i, false, false);
        }
        if (economy != null && !economy.xp().levelRewards().isEmpty()) {
            add(LEVEL_REWARDS, null, null, "economy.json#/xp/levelRewards", false, false);
        }
    }

    private void add(String id, ContentKind kind, UnlockConditionDef condition, String at,
            boolean unlockable, boolean cosmetic) {
        nodes.putIfAbsent(id, new Node(id, kind, condition, at, unlockable, cosmetic));
        if (condition != null && condition.type() == UnlockType.DEFAULT) {
            defaults.add(id);
        }
    }

    private void collectEdges() {
        for (Node node : nodes.values()) {
            if (node.condition() != null) {
                conditionEdges(node, node.condition());
            }
        }
        EconomyDef economy = content.economy();
        if (economy != null) {
            for (Map.Entry<String, LevelRewardDef> entry : economy.xp().levelRewards().entrySet()) {
                for (String id : entry.getValue().unlocks()) {
                    edge(LEVEL_REWARDS, id, "level " + entry.getKey() + " reward", true, 0);
                }
            }
        }
        for (WorldDef world : content.worlds()) {
            if (world.boss() != null && world.boss().reward() != null) {
                for (String id : world.boss().reward().unlocks()) {
                    edge(world.unlockableId(), id, "boss reward", false, 0);
                }
            }
        }
        for (ChallengeDef challenge : content.challenges()) {
            for (String id : challenge.rewardsOrNone().unlocks()) {
                edge(challenge.unlockableId(), id, "challenge reward", false, 0);
            }
        }
        for (AchievementDef achievement : content.achievements()) {
            for (String id : achievement.rewardOrNone().unlocks()) {
                edge(achievement.unlockableId(), id, "achievement reward", false, 0);
            }
        }
        for (UpgradeDef node : content.upgrades()) {
            for (GrantDef grant : node.grants()) {
                if (grant.isUnlock()) {
                    edge(TreeDef.NAMESPACE + node.tree(), grant.id(),
                            "upgrade node " + node.id(), true, nodeEntryCost(node));
                }
            }
        }
    }

    /**
     * What reaching a node's first level actually costs: its own {@code costs[0]} plus one level
     * of every prerequisite, transitively.
     *
     * <p>{@code hard_tier_1} costs 400 but cannot be bought without {@code coin_purse_1} at 80,
     * so the cheapest path to {@code tier:hard} through the node is 480, not 400. The
     * reachability guarantee does not depend on it — a prerequisite needs only coins — but the
     * cheapest-path table this class prints does, and M9's MetaSim thresholds (E25) read the same
     * numbers.
     *
     * @param node the node whose grant is an edge
     * @return the coins the whole prerequisite chain costs
     */
    private long nodeEntryCost(UpgradeDef node) {
        Set<String> counted = new LinkedHashSet<>();
        return entryCost(node, counted);
    }

    private long entryCost(UpgradeDef node, Set<String> counted) {
        if (!counted.add(node.id())) {
            return 0;
        }
        long cost = node.costs().isEmpty() ? 0 : node.costs().get(0);
        for (String prereq : node.prereqs()) {
            // The prerequisite DAG is proved acyclic by checkPrereqDag; an id it does not know is
            // reported there rather than here.
            if (content.upgrades().contains(prereq)) {
                cost += entryCost(content.upgrades().get(prereq), counted);
            }
        }
        return cost;
    }

    private void conditionEdges(Node node, UnlockConditionDef condition) {
        String required = requiredId(condition);
        if (required != null) {
            edge(required, node.id(), "condition " + label(condition), false, 0);
        }
        for (UnlockConditionDef child : condition.conditions()) {
            conditionEdges(node, child);
        }
    }

    private void edge(String from, String to, String reason, boolean isCumulative, long cost) {
        Edge e = new Edge(from, to, reason, isCumulative, cost);
        edges.add(e);
        incoming.computeIfAbsent(to, k -> new ArrayList<>()).add(e);
    }

    /**
     * The unlockable a condition depends on, if any.
     *
     * @param condition the condition
     * @return the namespaced id, or {@code null} when the condition reads player numbers only or
     *     points into a file that was not supplied (E19)
     */
    private String requiredId(UnlockConditionDef condition) {
        switch (condition.type()) {
            case CHALLENGE:
                return content.has(GameContent.CHALLENGES) && condition.id() != null
                        ? ChallengeDef.NAMESPACE + condition.id() : null;
            case ACHIEVEMENT:
                return content.has(GameContent.ACHIEVEMENTS) && condition.id() != null
                        ? AchievementDef.NAMESPACE + condition.id() : null;
            case WORLD_CLEARED:
                return content.has(GameContent.WORLDS) && condition.id() != null
                        ? WorldDef.NAMESPACE + condition.id() : null;
            default:
                return null;
        }
    }

    private static String label(UnlockConditionDef condition) {
        String type = condition.type().name().toLowerCase(java.util.Locale.ROOT);
        if (condition.id() != null) {
            return type + " " + condition.id();
        }
        if (condition.type() == UnlockType.PURCHASE) {
            return type + " " + (long) condition.amount();
        }
        return type + " " + trim(condition.value());
    }

    private static String trim(double value) {
        return value == StrictMath.rint(value) ? Long.toString((long) value)
                : Double.toString(value);
    }

    // ------------------------------------------------------------------ rules

    private void findCycles() {
        Set<String> done = new LinkedHashSet<>();
        Set<String> stack = new LinkedHashSet<>();
        Set<String> reported = new LinkedHashSet<>();
        for (String id : nodes.keySet()) {
            visit(id, done, stack, reported);
        }
    }

    private void visit(String id, Set<String> done, Set<String> stack, Set<String> reported) {
        if (done.contains(id)) {
            return;
        }
        if (!stack.add(id)) {
            List<String> cycle = new ArrayList<>(stack);
            int start = cycle.indexOf(id);
            List<String> loop = new ArrayList<>(cycle.subList(start, cycle.size()));
            loop.add(id);
            // The same loop is walked into once per entry point; report it once.
            if (reported.add(new java.util.TreeSet<>(loop).toString())) {
                errors.add(atOf(id) + ": unlock cycle " + String.join(" -> ", loop)
                        + " (nothing can require what it unlocks)");
            }
            return;
        }
        for (Edge in : incoming.getOrDefault(id, List.of())) {
            visit(in.from(), done, stack, reported);
        }
        stack.remove(id);
        done.add(id);
    }

    private void computeReachable() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node node : nodes.values()) {
                if (reachable.contains(node.id())) {
                    continue;
                }
                if (isSatisfiable(node)) {
                    reachable.add(node.id());
                    changed = true;
                }
            }
        }
        for (Node node : nodes.values()) {
            if (node.unlockable() && !reachable.contains(node.id())) {
                errors.add(node.at() + ": '" + node.id() + "' cannot be reached from the default"
                        + " set " + defaults + " — no condition, reward or grant opens it");
            }
        }
    }

    private boolean isSatisfiable(Node node) {
        if (node.condition() == null) {
            // Achievements and the synthetic level-reward source depend on player numbers only.
            return true;
        }
        if (satisfiable(node.condition())) {
            return true;
        }
        for (Edge in : incoming.getOrDefault(node.id(), List.of())) {
            if (reachable.contains(in.from())) {
                return true;
            }
        }
        return false;
    }

    private boolean satisfiable(UnlockConditionDef condition) {
        switch (condition.type()) {
            case ALL_OF: {
                if (condition.conditions().isEmpty()) {
                    return true;
                }
                for (UnlockConditionDef child : condition.conditions()) {
                    if (!satisfiable(child)) {
                        return false;
                    }
                }
                return true;
            }
            case ANY_OF: {
                for (UnlockConditionDef child : condition.conditions()) {
                    if (satisfiable(child)) {
                        return true;
                    }
                }
                return false;
            }
            default: {
                String required = requiredId(condition);
                return required == null || reachable.contains(required);
            }
        }
    }

    private void computeCumulative() {
        for (String id : defaults) {
            cumulative.put(id, new Path(0, List.of(id + " (default)")));
        }
        for (Node node : nodes.values()) {
            if (node.condition() == null) {
                cumulative.putIfAbsent(node.id(), new Path(0, List.of(node.id() + " (no unlock"
                        + " condition: it is earned by playing)")));
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node node : nodes.values()) {
                Path best = bestCumulative(node);
                Path known = cumulative.get(node.id());
                if (best.exists() && (known == null || best.cost() < known.cost())) {
                    cumulative.put(node.id(), best);
                    changed = true;
                }
            }
        }
        for (Node node : nodes.values()) {
            if (node.unlockable() && !node.cosmetic() && !cheapestCumulativePath(node.id())
                    .exists()) {
                errors.add(node.at() + ": '" + node.id() + "' has no path using only cumulative"
                        + " conditions (" + CUMULATIVE + ") — a player who cannot clear the"
                        + " content it depends on would never get it");
            }
        }
    }

    private Path bestCumulative(Node node) {
        Path best = Path.NONE;
        if (node.condition() != null) {
            Path own = cumulativeOf(node.condition());
            if (own.exists()) {
                List<String> steps = new ArrayList<>(own.steps());
                steps.add(node.id());
                best = new Path(own.cost(), steps);
            }
        }
        for (Edge in : incoming.getOrDefault(node.id(), List.of())) {
            if (!in.cumulative()) {
                continue;
            }
            Path from = cumulative.get(in.from());
            if (from == null || !from.exists()) {
                continue;
            }
            long cost = from.cost() + in.cost();
            if (cost < best.cost()) {
                List<String> steps = new ArrayList<>(from.steps());
                steps.add(in.reason() + (in.cost() > 0 ? " (" + in.cost() + " coins)" : ""));
                steps.add(node.id());
                best = new Path(cost, steps);
            }
        }
        return best;
    }

    /**
     * The cheapest way to satisfy a condition with cumulative types only.
     *
     * @param condition the condition
     * @return the path, or {@link Path#NONE}
     */
    private Path cumulativeOf(UnlockConditionDef condition) {
        switch (condition.type()) {
            case DEFAULT:
                return new Path(0, List.of("default"));
            case ANY_OF: {
                Path best = Path.NONE;
                for (UnlockConditionDef child : condition.conditions()) {
                    Path path = cumulativeOf(child);
                    if (path.exists() && path.cost() < best.cost()) {
                        best = path;
                    }
                }
                return best;
            }
            case ALL_OF: {
                long cost = 0;
                List<String> steps = new ArrayList<>();
                for (UnlockConditionDef child : condition.conditions()) {
                    Path path = cumulativeOf(child);
                    if (!path.exists()) {
                        return Path.NONE;
                    }
                    cost += path.cost();
                    steps.addAll(path.steps());
                }
                return new Path(cost, steps);
            }
            case PURCHASE:
                return new Path((long) condition.amount(), List.of(label(condition)));
            default:
                return CUMULATIVE.contains(condition.type())
                        ? new Path(0, List.of(label(condition))) : Path.NONE;
        }
    }

    /**
     * D13's "every currency has a source": a declared currency nothing ever pays out is a currency
     * the player can never spend.
     *
     * <p>The paying currency is derived from the data rather than assumed: every reward block —
     * the run terms, the level rewards, the boss, challenge and achievement rewards — pays the
     * first declared currency ({@link EconomyDef#primaryCurrency}), because {@code RewardDef} and
     * {@code LevelRewardDef} carry an amount and no currency. So a second currency can be
     * declared but not yet earned, and that is exactly what this reports.
     */
    private void checkCurrencies() {
        EconomyDef economy = content.economy();
        if (economy == null) {
            return;
        }
        Set<String> paid = payingCurrencies(economy);
        List<String> currencies = economy.currencies();
        for (int i = 0; i < currencies.size(); i++) {
            String currency = currencies.get(i);
            if (paid.contains(currency)) {
                continue;
            }
            if (currency.equals(economy.primaryCurrency())) {
                errors.add("economy.json#/rewards: no reward pays '" + currency
                        + "', so it could never be earned");
            } else {
                errors.add("economy.json#/currencies/" + i + ": nothing pays out '" + currency
                        + "' — every reward block in economy.json, and every boss, challenge and"
                        + " achievement reward, pays '" + economy.primaryCurrency() + "'");
            }
        }
    }

    /**
     * The currencies some reward block actually pays out.
     *
     * @param economy the economy
     * @return the currency ids with a source, in declaration order
     */
    private Set<String> payingCurrencies(EconomyDef economy) {
        Set<String> paid = new LinkedHashSet<>();
        String primary = economy.primaryCurrency();
        if (primary != null && paysAnything(economy)) {
            paid.add(primary);
        }
        return paid;
    }

    private boolean paysAnything(EconomyDef economy) {
        if (economy.rewards().participation() > 0 || economy.rewards().firstRunBonus() > 0
                || economy.rewards().coinsPerGate() > 0 || economy.rewards().coinsPerPoint() > 0
                || economy.rewards().streak().coins() > 0 || economy.rewards().bossBonus() > 0
                || economy.rewards().challengeBonus() > 0) {
            return true;
        }
        for (LevelRewardDef reward : economy.xp().levelRewards().values()) {
            if (reward.coins() > 0) {
                return true;
            }
        }
        for (WorldDef world : content.worlds()) {
            if (world.boss() != null && world.boss().reward() != null
                    && world.boss().reward().coins() > 0) {
                return true;
            }
        }
        for (ChallengeDef challenge : content.challenges()) {
            if (challenge.rewardsOrNone().coins() > 0) {
                return true;
            }
        }
        for (AchievementDef achievement : content.achievements()) {
            if (achievement.rewardOrNone().coins() > 0) {
                return true;
            }
        }
        return false;
    }

    private String atOf(String id) {
        Node node = nodes.get(id);
        return node == null ? "content" : node.at();
    }

    // ----------------------------------------------------------------- output

    /**
     * The graph as an indented tree: the default set first, then what each id opens, and finally
     * the cheapest cumulative path of every unlockable. This is what {@code contentCheck} prints.
     *
     * @return the report, newline separated
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("Default set (E18):\n");
        for (String id : defaults) {
            sb.append("  ").append(id).append('\n');
        }
        sb.append("\nUnlock tree (each id, then what it opens):\n");
        Set<String> printed = new LinkedHashSet<>();
        for (String id : defaults) {
            renderNode(sb, id, 1, printed);
        }
        for (String id : nodes.keySet()) {
            if (!printed.contains(id)) {
                renderNode(sb, id, 1, printed);
            }
        }
        sb.append("\nCheapest cumulative path per unlockable:\n");
        for (Node node : nodes.values()) {
            if (!node.unlockable()) {
                continue;
            }
            Path path = cheapestCumulativePath(node.id());
            sb.append("  ").append(node.id()).append(": ");
            if (path.exists()) {
                sb.append(path.cost()).append(" coins  ")
                        .append(String.join(" -> ", path.steps()));
            } else {
                sb.append(node.cosmetic() ? "none (cosmetic, exempt)" : "NONE");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void renderNode(StringBuilder sb, String id, int depth, Set<String> printed) {
        sb.append("  ".repeat(depth)).append(id);
        Node node = nodes.get(id);
        if (node != null && node.condition() != null) {
            sb.append("  [").append(describe(node.condition())).append(']');
        }
        if (!printed.add(id)) {
            sb.append("  (see above)\n");
            return;
        }
        sb.append('\n');
        for (Edge edge : edges) {
            if (edge.from().equals(id)) {
                sb.append("  ".repeat(depth + 1)).append("-> ").append(edge.to()).append("  (")
                        .append(edge.reason()).append(")\n");
                if (!printed.contains(edge.to())) {
                    renderNode(sb, edge.to(), depth + 2, printed);
                }
            }
        }
    }

    /**
     * A condition as one line of text.
     *
     * @param condition the condition
     * @return the text, for example {@code any_of[runs 3, purchase 150]}
     */
    public static String describe(UnlockConditionDef condition) {
        if (condition.type() == UnlockType.ALL_OF || condition.type() == UnlockType.ANY_OF) {
            List<String> parts = new ArrayList<>(condition.conditions().size());
            for (UnlockConditionDef child : condition.conditions()) {
                parts.add(describe(child));
            }
            return condition.type().name().toLowerCase(java.util.Locale.ROOT) + "["
                    + String.join(", ", parts) + "]";
        }
        if (condition.type() == UnlockType.COUNTER) {
            return "counter " + condition.counter() + " >= " + trim(condition.value());
        }
        return label(condition);
    }
}
