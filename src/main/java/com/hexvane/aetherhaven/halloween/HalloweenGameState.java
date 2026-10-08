package com.hexvane.aetherhaven.halloween;

import java.util.*;

/** Town-save state. No live world, entity or inventory references. */
public final class HalloweenGameState {
    public static final int CANDIES = 5, GOLD = 5;
    public record Spot(UUID plot, int x, int y, int z, String buildingPosition) {}
    public record Game(UUID id, UUID player, Spot spot, boolean found) {}
    private Map<UUID, Long> rewardedDays = new LinkedHashMap<>();
    private Game active;
    private List<Spot> cleanup = new ArrayList<>();
    public Game active() { return active; }
    public List<Spot> cleanup() { return List.copyOf(cleanup); }
    public boolean rewarded(UUID player, long day) {
        return rewardedDays.containsKey(player) && rewardedDays.get(player) >= day;
    }
    public boolean canStart(UUID player, long day) { return active == null && cleanup.size() < 32 && !rewarded(player, day); }
    public Game start(UUID player, long day, Spot spot) {
        if (player == null || spot == null || !canStart(player, day)) return null;
        return active = new Game(UUID.randomUUID(), player, spot, false);
    }
    public boolean pickup(UUID player, UUID game) {
        if (!matches(player, game) || active.found()) return false;
        active = new Game(active.id(), active.player(), active.spot(), true); return true;
    }
    public boolean matches(UUID player, UUID game) {
        return active != null && active.player().equals(player) && active.id().equals(game);
    }
    public boolean reward(UUID player, UUID game, long day) {
        if (!matches(player, game) || !active.found() || rewarded(player, day)) return false;
        rewardedDays.put(player, day); active = null; return true;
    }
    public void cancel() {
        if (active != null && !active.found() && !cleanup.contains(active.spot())) cleanup.add(active.spot());
        active = null;
    }
    public void cleaned(Spot spot) { cleanup.remove(spot); }

    /** Retain obligations when a town is deleted, without retaining its resident or active game. */
    public void absorbRetired(HalloweenGameState retired) {
        retired.rewardedDays.forEach((player, day) -> rewardedDays.merge(player, day, Math::max));
        retired.cancel();
        for (var spot : retired.cleanup) if (!cleanup.contains(spot)) cleanup.add(spot);
    }
}
