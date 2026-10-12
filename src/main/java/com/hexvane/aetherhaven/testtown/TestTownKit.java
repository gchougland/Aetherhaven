package com.hexvane.aetherhaven.testtown;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;

/** The tools and supplies handed to the player who asks for the test town. Runs on the thread of the world they are in. */
public final class TestTownKit {
    private TestTownKit() {}

    /** @return one line per thing that could not be given, empty when everything arrived */
    public static List<String> give(AetherhavenPlugin plugin, TestTownSpec.KitSpec kit, Ref<EntityStore> ref, Store<EntityStore> store) {
        List<String> problems = new ArrayList<>();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return List.of("kit: the player is not in a world");
        for (TestTownSpec.StackSpec entry : kit.items()) {
            Item item = Item.getAssetMap().getAsset(entry.item());
            if (item == null) { problems.add("kit item " + entry.item() + ": this game has no item with that id"); continue; }
            int left = entry.count(), stackSize = Math.max(1, item.getMaxStack());
            while (left > 0) {
                int amount = Math.min(left, stackSize);
                if (!Player.giveItem(new ItemStack(entry.item(), amount), ref, store).succeeded()) {
                    problems.add("kit item " + entry.item() + ": the inventory is full");
                    break;
                }
                left -= amount;
            }
        }
        for (String construction : kit.plotTokens()) {
            var definition = plugin.getConstructionCatalog().get(construction);
            if (definition == null) { problems.add("kit plot token " + construction + ": no building has that id"); continue; }
            if (!Player.giveItem(PlotTokenInventory.createTokenStack(construction, 1, definition.getDisplayName()), ref, store).succeeded())
                problems.add("kit plot token " + construction + ": the inventory is full");
        }
        for (String prop : kit.props()) {
            var definition = plugin.getPropCatalog().get(prop);
            if (definition == null) { problems.add("kit prop " + prop + ": no prop has that id"); continue; }
            if (!Player.giveItem(PropItemMetadata.createStack(definition, 1), ref, store).succeeded())
                problems.add("kit prop " + prop + ": the inventory is full");
        }
        return problems;
    }

    /** Switches the player to creative mode and returns the mode they had, or null when nothing changed. */
    public static GameMode creative(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getGameMode() == GameMode.Creative) return null;
        GameMode before = player.getGameMode();
        Player.setGameMode(ref, GameMode.Creative, store);
        return before;
    }
}
