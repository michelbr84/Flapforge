package io.github.michelbr84.flapforge.content.defs;

import java.util.List;

/**
 * The root of {@code upgrades.json} (§4): the tree list and every node, both in file order.
 *
 * @param trees the upgrade trees
 * @param nodes the nodes of every tree
 */
public record UpgradesDef(List<TreeDef> trees, List<UpgradeDef> nodes) {

    /**
     * Copies both lists.
     */
    public UpgradesDef {
        trees = List.copyOf(trees);
        nodes = List.copyOf(nodes);
    }
}
