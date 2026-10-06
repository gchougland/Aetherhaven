package com.hexvane.aetherhaven.production;

import java.util.LinkedHashMap;
import java.util.Map;

/** Saved per-slot cooldowns and exact ownership of authored cells. No world or entity references. */
public final class MiningProductionState {
    private Map<Integer, MiningCycle> cycles = new LinkedHashMap<>();
    private Map<Integer, String> ownedBlocks = new LinkedHashMap<>();
    private int nextSlot;
    private int nextNode;

    public MiningCycle cycle(int slot) {
        if (slot < 0 || slot >= WorkplaceProductionUpgrades.MAX_SLOT_COUNT) throw new IllegalArgumentException("slot");
        if (cycles == null) cycles = new LinkedHashMap<>();
        return cycles.computeIfAbsent(slot, ignored -> new MiningCycle());
    }
    public String ownedBlock(int node) { return ownedBlocks == null ? null : ownedBlocks.get(node); }
    public void own(int node, String block) {
        if (ownedBlocks == null) ownedBlocks = new LinkedHashMap<>();
        ownedBlocks.put(node, block);
    }
    public int firstSlot(int slots) { return Math.floorMod(nextSlot, slots); }
    public int node(int nodes) { return Math.floorMod(nextNode, nodes); }
    public void completed(int slot) { nextSlot = slot + 1; nextNode++; }
}
