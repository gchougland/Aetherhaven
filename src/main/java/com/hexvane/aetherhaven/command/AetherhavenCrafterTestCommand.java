package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.crafter.PatternBookService;
import com.hexvane.aetherhaven.crafter.WorkshopParts;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import java.util.concurrent.CompletableFuture;

/** Prices every installed prop in its four materials from its real design. Changes nothing. */
final class AetherhavenCrafterTestCommand extends AbstractAsyncCommand {
    AetherhavenCrafterTestCommand() {
        super("crafter", "Check the Pattern Book materials and the price of every prop");
        setPermissionGroups("hytale:WorldEditor");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        var plugin = AetherhavenPlugin.get();
        int passed = 0, failed = 0;
        for (var kind : WorkshopParts.Kind.values()) {
            var item = Item.getAssetMap().getAsset(kind.item());
            if (item == null || item.getIcon() == null || item.getIcon().isBlank()) { failed++; report(ctx, kind.label() + ": item or icon missing."); }
            else passed++;
        }
        PatternBookService.invalidate();
        for (var prop : plugin.getPropCatalog().list()) {
            var weights = PatternBookService.weights(prop);
            var cost = PatternBookService.cost(prop);
            int total = cost.values().stream().mapToInt(Integer::intValue).sum();
            boolean sound = total == WorkshopParts.total(prop.getGoldPrice())
                && cost.values().stream().allMatch(count -> count >= WorkshopParts.STEP && count % WorkshopParts.STEP == 0);
            if (sound) passed++; else failed++;
            report(ctx, prop.getId() + " (" + prop.getGoldPrice() + " gold): " + cost + ", "
                + Math.round(WorkshopParts.workSeconds(prop.getGoldPrice())) + "s, seen " + (weights.isEmpty() ? "nothing recognisable" : weights.toString())
                + (sound ? "" : " INVALID"));
        }
        // Payment fixture: a detached pack and warehouse, so no town or player is touched.
        String sticks = WorkshopParts.Kind.WOOD.item();
        var pack = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 4);
        var stores = new com.hexvane.aetherhaven.warehouse.TownWarehouse();
        pack.addItemStack(new com.hypixel.hytale.server.core.inventory.ItemStack(sticks, 3));
        stores.acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(sticks, 7));
        boolean payment = com.hexvane.aetherhaven.crafter.PatternBookPayment.available(pack, stores, sticks) == 10
            && com.hexvane.aetherhaven.crafter.PatternBookPayment.pay(pack, stores, java.util.Map.of(sticks, 8))
            && com.hexvane.aetherhaven.inventory.InventoryMaterials.count(pack, sticks) == 0 && stores.craftingStock().count(sticks) == 2
            && !com.hexvane.aetherhaven.crafter.PatternBookPayment.pay(pack, stores, java.util.Map.of(sticks, 5))
            && stores.craftingStock().count(sticks) == 2
            && !com.hexvane.aetherhaven.crafter.PatternBookPayment.pay(pack, null, java.util.Map.of(sticks, 1));
        if (payment) passed++; else { failed++; report(ctx, "Pack and warehouse payment: FAILED."); }
        // Upgrade payments: the shortfall is brought from the warehouse to the pack, or nothing moves at all.
        boolean topUp = !com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(pack, stores, java.util.Map.of(sticks, 3))
            && stores.craftingStock().count(sticks) == 2 && com.hexvane.aetherhaven.inventory.InventoryMaterials.count(pack, sticks) == 0;
        pack.addItemStack(new com.hypixel.hytale.server.core.inventory.ItemStack(sticks, 1));
        topUp &= com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(pack, stores, java.util.Map.of(sticks, 3))
            && com.hexvane.aetherhaven.inventory.InventoryMaterials.count(pack, sticks) == 3 && stores.craftingStock().count(sticks) == 0
            && com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(pack, null, java.util.Map.of(sticks, 3))
            && !com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(pack, null, java.util.Map.of(sticks, 4));
        // A full pack must not swallow or lose warehouse stock.
        var full = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        full.addItemStack(new com.hypixel.hytale.server.core.inventory.ItemStack(WorkshopParts.Kind.STONE.item(), 1));
        stores.acceptCompletedOutput(new com.hypixel.hytale.server.core.inventory.ItemStack(sticks, 4));
        topUp &= !com.hexvane.aetherhaven.crafter.PatternBookPayment.topUp(full, stores, java.util.Map.of(sticks, 4))
            && stores.craftingStock().count(sticks) == 4 && com.hexvane.aetherhaven.inventory.InventoryMaterials.count(full, sticks) == 0;
        if (topUp) passed++; else { failed++; report(ctx, "Warehouse top up for upgrades: FAILED."); }
        // Salvaging and smelting read the game's own recipes, so they can only be checked on a running server.

        var recipes = java.util.List.copyOf(plugin.getApprenticeRecipes().all());
        long salvage = recipes.stream().filter(r -> r.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SALVAGING).count();
        boolean salvaging = salvage >= 100
            && recipes.stream().filter(r -> r.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SALVAGING)
                .allMatch(r -> r.name().startsWith("Salvage ") && !r.sourceRecipe().startsWith("Salvage_Aetherhaven_") && r.source() != null);
        if (salvaging) passed++; else { failed++; report(ctx, "Salvaging workplace recipes: FAILED (" + salvage + " found)."); }
        // The Builder's Workbench is a structural bench. Its recipes must reach the Block Workshop and nothing else.
        long blocks = recipes.stream().filter(r -> r.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.BLOCK_WORKSHOP).count();
        boolean workshop = blocks >= 100
            && recipes.stream().filter(r -> r.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.BLOCK_WORKSHOP)
                .allMatch(r -> com.hexvane.aetherhaven.workplace.WorkplaceEquipment.BUILDERS_BENCH.equals(r.bench()) && r.source() != null)
            && recipes.stream().filter(r -> com.hexvane.aetherhaven.workplace.WorkplaceEquipment.BUILDERS_BENCH.equals(r.bench()))
                .allMatch(r -> r.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.BLOCK_WORKSHOP);
        if (workshop) passed++; else { failed++; report(ctx, "Block Workshop recipes: FAILED (" + blocks + " found)."); }
        var smelting = new java.util.TreeMap<String, Integer>();
        for (var recipe : recipes) if (recipe.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SMELTING)
            for (String metal : java.util.List.of("Copper", "Iron", "Silver", "Gold", "Thorium", "Cobalt", "Adamantite"))
                if (recipe.sourceRecipe().contains("_Bar_" + metal + "_")) smelting.put(metal, recipe.skillLevel());
        boolean ladder = java.util.Map.of("Copper", 1, "Iron", 3, "Silver", 4, "Gold", 4, "Thorium", 5, "Cobalt", 7, "Adamantite", 9).equals(smelting);
        if (ladder) passed++; else { failed++; report(ctx, "Smelting skill ladder: FAILED " + smelting + "."); }
        report(ctx, "Workplace recipes: " + salvage + " salvaging, " + blocks + " block workshop, smelting skills " + smelting + ".");
        // The feeding trough has no recipe in the base game, so the mod supplies one at the Workbench.
        var trough = com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe.getAssetMap().getAsset("Bench_Trough_Recipe");
        boolean troughCraftable = trough != null && trough.getPrimaryOutput() != null && "Bench_Trough".equals(trough.getPrimaryOutput().getItemId())
            && trough.getBenchRequirement() != null && trough.getBenchRequirement().length == 1 && "Workbench".equals(trough.getBenchRequirement()[0].id)
            && recipes.stream().anyMatch(r -> "Bench_Trough_Recipe".equals(r.sourceRecipe()) && r.type() == com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.CRAFTING);
        if (troughCraftable) passed++; else { failed++; report(ctx, "Feeding trough recipe: FAILED."); }
        // Mineshaft supports are hardwood, including the two joining pieces.
        var items = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap();
        boolean hardwood = items.getAsset(com.hexvane.aetherhaven.workplace.ExcavationFittings.BEAM) != null
            && "Wood_Hardwood_Beam".equals(com.hexvane.aetherhaven.workplace.ExcavationFittings.BEAM);
        for (String joint : java.util.List.of(com.hexvane.aetherhaven.workplace.ExcavationFittings.CORNER, com.hexvane.aetherhaven.workplace.ExcavationFittings.CEILING)) {
            var item = items.getAsset(joint);
            hardwood &= item != null && item.getTranslationKey() != null && item.getTranslationKey().contains("Wood_Hardwood_Beam");
        }
        if (hardwood) passed++; else { failed++; report(ctx, "Hardwood mineshaft supports: FAILED."); }
        report(ctx, "Pattern Book asset check: " + passed + " passed, " + failed + " failed. No world or inventory changed.");
        return CompletableFuture.completedFuture(null);
    }
    private static void report(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(text);
    }
}
