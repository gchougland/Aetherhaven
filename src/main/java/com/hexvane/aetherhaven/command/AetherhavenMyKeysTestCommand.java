package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.rts.RtsCommandPlayerComponent;
import com.hexvane.aetherhaven.ui.ToolKeyPromptOverrides;
import com.hexvane.aetherhaven.ui.ToolKeyPrompts;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Prints what the server holds for the key list of the player who runs it, while they hold a tool. Read only.
 *
 * <p>The game lists a tool's inputs from the player's own interaction overrides ({@link ToolKeyPromptOverrides}).
 * When a line is missing in the client, this shows which link is broken: the item the engine picks for the input, the
 * entry that was wanted, the entry that is there now and whose it is, and whether the player's own client is sent
 * the entries at all. Every line also goes to the server log.
 */
final class AetherhavenMyKeysTestCommand extends AbstractPlayerCommand {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    AetherhavenMyKeysTestCommand() {
        super("mykeys", "Print what the server holds for your key list while you hold a tool");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        for (String line : describe(store, ref, player.getUsername())) {
            LOG.atInfo().log("Tool key state: %s", line);
            ctx.sendMessage(Message.raw(line));
        }
    }

    @Nonnull
    static List<String> describe(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String name) {
        List<String> lines = new ArrayList<>();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        RtsCommandPlayerComponent session = store.getComponent(ref, RtsCommandPlayerComponent.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Utility utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        InventoryComponent.Tool tools = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        lines.add(name
            + ": game mode " + (playerComponent != null ? playerComponent.getGameMode() : "?")
            + ", command post session " + (session == null ? "none" : session.isActive() ? "active" : "not active")
            + ", hotbar slot " + (hotbar != null ? hotbar.getActiveSlot() : -1)
            + " holds " + id(hotbar != null ? hotbar.getActiveItem() : null)
            + ", other hand " + id(utility != null ? utility.getActiveItem() : null)
            + ", creative tools item in use " + (tools != null && tools.isUsingToolsItem())
            + ", carrying a block " + (store.getComponent(ref, CarriedBlock.getComponentType()) != null)
            + ", in hand for the engine " + id(InventoryComponent.getItemInHand(store, ref)));

        Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
        Map<InteractionType, String> now = interactions != null ? interactions.getInteractions() : Map.of();
        Map<InteractionType, String> wanted = ToolKeyPromptOverrides.desired(ref, store);
        InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        for (InteractionType type : ToolKeyPromptOverrides.CLIENT_OVERRIDE_LIST) {
            Item engineItem = manager == null ? null : InteractionContext.forInteraction(manager, ref, type, store).getOriginalItemType();
            String itemRoot = engineItem == null ? null : engineItem.getInteractions().get(type);
            String entry = now.get(type);
            if (itemRoot == null && entry == null && !wanted.containsKey(type)) {
                continue;
            }
            lines.add(type
                + ": engine item " + (engineItem == null ? "none" : engineItem.getId())
                + (engineItem != null && ToolKeyPrompts.items().containsKey(engineItem.getId()) ? " (tool)" : " (not a tool)")
                + ", its root " + root(itemRoot)
                + ", wanted " + (wanted.get(type) == null ? "nothing" : wanted.get(type))
                + ", override now " + (entry == null ? "none" : root(entry)
                    + (ToolKeyPromptOverrides.isPromptRoot(entry) ? " (ours)" : " (someone else's)")));
        }

        EntityTrackerSystems.Visible visible = store.getComponent(ref, EntityTrackerSystems.Visible.getComponentType());
        lines.add("Overrides: component " + (interactions == null ? "absent" : "present, override all " + interactions.isOverrideAll())
            + ", edits still pending " + ToolKeyPromptOverrides.pending(ref, store).size()
            + ", listed by a held weapon itself: " + ToolKeyPromptOverrides.HELD_ITEM_LISTED
            + ", sent to your own client: " + (visible == null ? "no tracker component" : visible.visibleTo.containsKey(ref)
                + " (" + visible.visibleTo.size() + " viewers)"));
        return lines;
    }

    @Nonnull
    private static String id(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? "nothing" : stack.getItemId();
    }

    /** A root id with its prompt key, so a root that lost its prompt shows up. */
    @Nonnull
    private static String root(@Nullable String rootId) {
        if (rootId == null) {
            return "none";
        }
        RootInteraction asset = RootInteraction.getAssetMap().getAsset(rootId);
        if (asset == null) {
            return rootId + " [not loaded]";
        }
        String prompt = asset.getHudInputBindingEntry();
        return rootId + " [" + (prompt == null ? "no prompt" : prompt.substring(prompt.lastIndexOf('.', prompt.lastIndexOf('.') - 1) + 1)) + "]";
    }
}
