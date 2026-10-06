package com.hexvane.aetherhaven.production;

import java.util.*;

/** Mod-era production contract, independent of marketplace download revisions. */
public record BuildingProductionLayout(String modRules, String mode, List<Node> nodes) {
    public static final String MINING_NODES = "mining_nodes";
    public static final BuildingProductionLayout LEGACY = new BuildingProductionLayout("3.x", "legacy", List.of());
    public static final int MAX_AUTHORED_NODES = 64;

    /** One replaceable cell. Worker approaches are calculated, never authored as a second position. */
    public record Node(int x, int y, int z, String expectedBlockId) {}

    public BuildingProductionLayout {
        nodes = nodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    public boolean legacy() { return "3.x".equals(modRules) && "legacy".equals(mode) && nodes.isEmpty(); }

    /** Structural validation only. Loaded block ownership, footprint and approach checks are separate. */
    public String validationError() {
        if (legacy()) return null;
        if (!"4.0".equals(modRules) || !MINING_NODES.equals(mode)) return "Unsupported production rules.";
        if (nodes.isEmpty() || nodes.size() > MAX_AUTHORED_NODES) return "Choose between 1 and 64 mining cells.";
        var cells = new HashSet<List<Integer>>();
        for (var node : nodes) {
            if (node == null || node.expectedBlockId() == null || node.expectedBlockId().isBlank())
                return "Each mining cell needs its original block.";
            if (!cells.add(List.of(node.x(), node.y(), node.z()))) return "Mining cells cannot share a position.";
        }
        return null;
    }
}
