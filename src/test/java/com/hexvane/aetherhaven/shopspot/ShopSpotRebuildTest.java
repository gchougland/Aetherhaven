package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** P12: rebuilding a player shop must keep everything players have for sale. Saved registry state only. */
@Tag("construction")
class ShopSpotRebuildTest {
    private static final Gson GSON = new Gson();
    private static final UUID TOWN = UUID.randomUUID(), PLOT = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID(), OTHER_SELLER = UUID.randomUUID();

    private static ShopSpotRecord stall(int x, boolean playerControlled) {
        var r = new ShopSpotRecord();
        r.setSpotId(UUID.randomUUID());
        r.setWorldName("default");
        r.setBlockPosition(new Vector3i(x, 64, 0));
        r.setTownId(TOWN);
        r.setPlotId(PLOT);
        r.setPlayerControlled(playerControlled);
        return r;
    }

    private static ShopSpotRecord listing(int x, UUID seller, String item, int stock) {
        var r = stall(x, true);
        r.setItemId(item);
        r.setStock(stock);
        r.setSellerUuid(seller);
        r.setSellerName("Seller");
        return r;
    }

    /** The registry never reads its world except while loading a file, which these tests do not do. */
    private static ShopSpotRegistry registry(ShopSpotRecord... rows) {
        var registry = new ShopSpotRegistry(null);
        for (var row : rows) registry.put(row);
        return registry;
    }

    /**
     * What ShopSpotExtractor does to the saved rows for each stall block the paste produced: the pasted block is a
     * prefab template again, so only the saved row can say what was for sale.
     */
    private static void pasteStall(ShopSpotRegistry registry, Vector3i block, boolean templatePlayerControlled) {
        var existing = registry.getAtBlock(block.x, block.y, block.z);
        if (ShopSpotRebuild.keepsRow(existing, TOWN, PLOT, true, templatePlayerControlled)) return;
        if (existing != null) registry.removeKeepingListing(existing);
        var fresh = stall(block.x, templatePlayerControlled);
        registry.put(fresh);
    }

    @Test void rebuildingAPlayerShopKeepsEveryListingOnItsStall() {
        var ring = listing(1, SELLER, "Aetherhaven_Ring_Gold", 1);
        ring.setJewelryMetaJson("{\"rarity\":\"RARE\"}");
        var planks = listing(2, OTHER_SELLER, "Wood_Oak_Planks", 40);
        var empty = stall(3, true);
        var registry = registry(ring, planks, empty);
        String before = GSON.toJson(ShopSpotWorldFile.fromRegistry(registry));

        // Reconstruct pastes the same prefab where it stood: all three stalls come back as templates.
        for (var row : List.of(ring, planks, empty)) pasteStall(registry, row.getBlockPosition(), true);
        assertTrue(ShopSpotRebuild.orphans(registry, PLOT, r -> true).isEmpty());

        assertEquals(before, GSON.toJson(ShopSpotWorldFile.fromRegistry(registry)), "saved stalls changed");
        assertEquals(2, registry.listPlayerListingsOnPlot(PLOT).size());
        assertSame(ring, registry.getAtBlock(1, 64, 0));
        assertEquals("{\"rarity\":\"RARE\"}", ring.getJewelryMetaJson());
        assertEquals(40, planks.getStock());
        assertEquals(OTHER_SELLER, planks.getSellerUuid());
        assertFalse(registry.hasHeldReturns());
    }

    @Test void aStallTheNewBuildingNoLongerHasGivesItsListingBackInsteadOfDeletingIt() {
        var kept = listing(1, SELLER, "Wood_Oak_Planks", 20);
        var removed = listing(2, OTHER_SELLER, "Ore_Iron", 15);
        var npc = stall(3, false);
        npc.setItemId("Food_Bread");
        npc.setStock(5);
        var registry = registry(kept, removed, npc);

        pasteStall(registry, kept.getBlockPosition(), true);
        // Cells 2 and 3 are plain floor in the new prefab.
        var gone = ShopSpotRebuild.orphans(registry, PLOT, r -> r == kept);
        assertEquals(2, gone.size());
        for (var row : gone) registry.removeKeepingListing(row);

        assertEquals(List.of(kept), registry.listForPlot(PLOT));
        assertEquals(List.of(removed), registry.heldReturns(), "only items a player owns are held");
        assertEquals(List.of(removed), registry.heldReturnsFor(OTHER_SELLER));
        assertTrue(registry.heldReturnsFor(SELLER).isEmpty());
        assertNull(registry.getAtBlock(2, 64, 0), "a held listing is never offered for sale");

        // Held listings survive a save and load until their seller has them.
        var file = GSON.fromJson(GSON.toJson(ShopSpotWorldFile.fromRegistry(registry)), ShopSpotWorldFile.class);
        assertEquals(1, ShopSpotWorldFile.toRecords(file).size());
        var held = ShopSpotWorldFile.toHeldReturns(file);
        assertEquals(1, held.size());
        assertEquals("Ore_Iron", held.getFirst().getItemId());
        assertEquals(15, held.getFirst().getStock());
        assertEquals(OTHER_SELLER, held.getFirst().getSellerUuid());

        assertTrue(registry.releaseHeldReturn(removed));
        assertFalse(registry.releaseHeldReturn(removed), "a listing is handed back once");
        assertFalse(registry.hasHeldReturns());
    }

    @Test void aStallThatChangedKindOrBelongsToAnotherBuildingIsNotReused() {
        var mine = listing(1, SELLER, "Wood_Oak_Planks", 20);
        assertTrue(ShopSpotRebuild.keepsRow(mine, TOWN, PLOT, true, true));
        assertTrue(ShopSpotRebuild.keepsRow(mine, TOWN, PLOT, false, false), "an unconfigured block keeps the row");
        assertFalse(ShopSpotRebuild.keepsRow(mine, TOWN, PLOT, true, false), "prefab now makes this an NPC stall");
        assertFalse(ShopSpotRebuild.keepsRow(mine, TOWN, UUID.randomUUID(), true, true));
        assertFalse(ShopSpotRebuild.keepsRow(mine, UUID.randomUUID(), PLOT, true, true));
        assertFalse(ShopSpotRebuild.keepsRow(null, TOWN, PLOT, true, true));

        var registry = registry(mine);
        pasteStall(registry, mine.getBlockPosition(), false);
        assertEquals(List.of(mine), registry.heldReturnsFor(SELLER));
        assertFalse(registry.getAtBlock(1, 64, 0).isPlayerControlled());
        assertFalse(registry.removeKeepingListing(mine), "holding the same listing twice would hand it back twice");
        assertEquals(1, registry.heldReturns().size());
    }

    /** The safe block only names its town; the gold is saved per seller on the town, which a rebuild never rewrites. */
    @Test void shopSafeEarningsLiveOnTheTownAndSurviveARebuiltSafeBlock() {
        var town = new com.hexvane.aetherhaven.town.TownRecord();
        town.addPlayerShopSafeGold(SELLER, 120);
        town.addPlayerShopSafeGold(OTHER_SELLER, 35);
        var reloaded = GSON.fromJson(GSON.toJson(town), com.hexvane.aetherhaven.town.TownRecord.class);
        assertEquals(120, reloaded.getPlayerShopSafeGold(SELLER));
        assertEquals(35, reloaded.getPlayerShopSafeGold(OTHER_SELLER));
        assertEquals(120, reloaded.withdrawPlayerShopSafeGold(SELLER, 120));
        assertEquals(0, reloaded.getPlayerShopSafeGold(SELLER));
    }

    @Test void olderSaveFilesWithoutHeldReturnsStillLoad() {
        var file = GSON.fromJson("{\"spots\":[{\"spotId\":\"" + UUID.randomUUID() + "\",\"worldName\":\"default\"}]}",
            ShopSpotWorldFile.class);
        assertEquals(1, ShopSpotWorldFile.toRecords(file).size());
        assertTrue(ShopSpotWorldFile.toHeldReturns(file).isEmpty());
    }
}
