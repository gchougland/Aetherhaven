package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.ui.ToolKeyPromptOverrides;
import com.hexvane.aetherhaven.ui.ToolKeyPrompts;
import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.InteractionSimulationHandler;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.UnarmedInteractions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Check, against the assets the running server really loaded, that the game will list every tool input with the
 * player's own key.
 *
 * <p>A player's keys are known only to their client. The client lists an input of a held item in one of two ways (see
 * {@link ToolKeyPromptOverrides}): one input from the held item alone, the others from the player's own interaction
 * overrides. This command confirms three things. Each tool root carries its prompt, in the packet sent to clients,
 * with English text. Each prompt falls under one of the two ways. And on a stand-in entity that holds each tool in
 * turn, the overrides become exactly the tool's own roots, leave someone else's override alone, and are gone again
 * when the tool is put away, when a spectator style override is on, and before the entity is saved.
 *
 * <p>It cannot see a client, so it cannot confirm what a player sees. The stand-in is not a player: it is added to the
 * default world and removed again inside one world task, before any tick.
 */
final class AetherhavenToolKeysTestCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String LANGUAGE = "en-US";

    AetherhavenToolKeysTestCommand() {
        super("toolkeys", "Check that the game lists every tool input with the player's key");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        Set<InteractionType> inputs = EnumSet.noneOf(InteractionType.class);
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            if (slot.hasKeyPrompt()) {
                inputs.add(slot.interactionType());
            }
        }
        I18nModule i18n = I18nModule.get();
        List<String> failures = new ArrayList<>();
        List<String> loaded = new ArrayList<>();
        int prompts = 0;
        int besideAbilitySlots = 0;
        int throughOverrides = 0;
        int notLoaded = 0;
        for (Map.Entry<String, ToolKeyPrompts> tool : ToolKeyPrompts.items().entrySet()) {
            Item item = Item.getAssetMap().getAsset(tool.getKey());
            if (item == null) {
                // A tool from an optional pack that this server does not run.
                notLoaded++;
                continue;
            }
            loaded.add(tool.getKey());
            Map<InteractionType, String> mirrored =
                ToolKeyPromptOverrides.mirrored(item.getInteractions(), ToolKeyPromptOverrides::isPromptRoot);
            for (InteractionType type : inputs) {
                String rootId = item.getInteractions().get(type);
                if (rootId == null) {
                    continue;
                }
                String expected = tool.getValue().promptKey(type);
                String text = i18n == null ? null : i18n.getMessage(LANGUAGE, expected);
                RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
                if (root == null) {
                    failures.add(tool.getKey() + " " + type + ": root interaction " + rootId + " is not loaded");
                    continue;
                }
                String entry = root.getHudInputBindingEntry();
                if (text == null) {
                    // An input this tool binds but gives nothing to do has no action name, and must show no prompt.
                    if (entry != null) {
                        failures.add(tool.getKey() + " " + type + ": prompt " + entry + " has no English text");
                    }
                    continue;
                }
                if (!expected.equals(entry)) {
                    failures.add(tool.getKey() + " " + type + ": expected prompt " + expected + ", the loaded root has " + entry);
                    continue;
                }
                if (!expected.equals(root.toPacket().hudInputBindingEntry)) {
                    failures.add(tool.getKey() + " " + type + ": the prompt is not in the packet sent to clients");
                    continue;
                }
                ToolKeyPromptOverrides.Listing listing = ToolKeyPromptOverrides.listingOf(type);
                if (listing == null) {
                    failures.add(tool.getKey() + " " + type + ": the client has no list for this input");
                    continue;
                }
                if (listing == ToolKeyPromptOverrides.Listing.PLAYER_OVERRIDE) {
                    if (!rootId.equals(mirrored.get(type))) {
                        failures.add(tool.getKey() + " " + type + ": not mirrored into the player's overrides");
                        continue;
                    }
                    if (RootInteraction.getAssetMap().getIndex(rootId) == Integer.MIN_VALUE) {
                        failures.add(tool.getKey() + " " + type + ": root " + rootId + " has no index to send to clients");
                        continue;
                    }
                    throughOverrides++;
                } else {
                    besideAbilitySlots++;
                }
                prompts++;
                LOG.atInfo().log("Tool key prompt: %s %s -> \"%s\" (%s, root %s)", tool.getKey(), type, text, listing, rootId);
            }
        }
        int listedPrompts = prompts;
        int beside = besideAbilitySlots;
        int overrides = throughOverrides;
        int missing = notLoaded;
        World world = Universe.get().getDefaultWorld();
        CompletableFuture<Integer> standIn = new CompletableFuture<>();
        if (world == null) {
            failures.add("no default world to run the stand-in check in");
            standIn.complete(0);
        } else {
            world.execute(() -> {
                try {
                    standIn.complete(standInCheck(world.getEntityStore().getStore(), loaded, failures));
                } catch (Throwable t) {
                    failures.add("stand-in check threw " + t);
                    LOG.atWarning().withCause(t).log("Tool key prompt stand-in check threw");
                    standIn.complete(0);
                }
            });
        }
        return standIn.orTimeout(60, TimeUnit.SECONDS).handle((checked, error) -> {
            if (error != null) {
                failures.add("stand-in check did not finish: " + error);
            }
            for (String failure : failures) {
                LOG.atWarning().log("Tool key prompt check: %s", failure);
            }
            int onStandIn = checked == null ? 0 : checked;
            boolean passed = failures.isEmpty() && listedPrompts > 0 && onStandIn == loaded.size();
            String result = "Tool key prompt check " + (passed ? "PASSED" : "FAILED") + ": "
                + loaded.size() + " tools loaded, " + listedPrompts + " prompts (" + beside + " beside the ability slots, "
                + overrides + " through player overrides), " + onStandIn + " tools held by a stand-in, "
                + missing + " tools not loaded, " + failures.size() + " failures.";
            LOG.atInfo().log(result);
            ctx.sendMessage(Message.raw(result));
            for (String failure : failures.stream().limit(8).toList()) {
                ctx.sendMessage(Message.raw(failure));
            }
            return null;
        });
    }

    /**
     * Holds each tool in turn on an entity with a hotbar and an interaction manager and runs the real reconcile on
     * it. Must run on the world thread. Returns how many tools passed every step.
     */
    private static int standInCheck(@Nonnull Store<EntityStore> store, @Nonnull List<String> tools, @Nonnull List<String> failures) {
        UnarmedInteractions emptyHand = UnarmedInteractions.getAssetMap().getAsset("Empty");
        String foreign = emptyHand == null ? null : emptyHand.getInteractions().get(InteractionType.Primary);
        if (foreign == null || ToolKeyPromptOverrides.isPromptRoot(foreign)) {
            failures.add("no vanilla root interaction to stand for someone else's override");
            return 0;
        }
        InventoryComponent.Hotbar hotbar = new InventoryComponent.Hotbar((short) 9);
        byte held = hotbar.getActiveSlot();
        byte empty = (byte) (held == 0 ? 1 : 0);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(InventoryComponent.Hotbar.getComponentType(), hotbar);
        holder.addComponent(
            InteractionModule.get().getInteractionManagerComponent(),
            new InteractionManager(null, new InteractionSimulationHandler())
        );
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null || !ref.isValid()) {
            failures.add("could not add the stand-in entity");
            return 0;
        }
        int passed = 0;
        try {
            InventoryComponent.Hotbar live = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            if (live == null) {
                failures.add("the stand-in entity has no hotbar");
                return 0;
            }
            for (String toolId : tools) {
                Item item = Item.getAssetMap().getAsset(toolId);
                if (item == null) {
                    continue;
                }
                Map<InteractionType, String> want =
                    ToolKeyPromptOverrides.mirrored(item.getInteractions(), ToolKeyPromptOverrides::isPromptRoot);
                String failure = holdAndPutAway(store, ref, live, held, empty, toolId, want, foreign);
                if (failure == null) {
                    passed++;
                } else {
                    failures.add(toolId + " on a stand-in: " + failure);
                }
            }
            // Leaving the world: whatever a tool put there must not be saved with the entity.
            live.getInventory().setItemStackForSlot(held, new ItemStack(tools.get(0)));
            live.setActiveSlot(held, ref, store);
            ToolKeyPromptOverrides.reconcile(ref, store);
            // The printout a player gets from "ah test mykeys", run here so it is known to work on a live store.
            List<String> state = AetherhavenMyKeysTestCommand.describe(store, ref, "stand-in");
            for (String line : state) {
                LOG.atInfo().log("Tool key state: %s", line);
            }
            if (state.size() < 3) {
                failures.add("the player printout has no line per input");
            }
            boolean hadEntries = store.getComponent(ref, Interactions.getComponentType()) != null;
            Holder<EntityStore> removed = store.removeEntity(ref, RemoveReason.REMOVE);
            int stripped = ToolKeyPromptOverrides.strip(removed);
            if (hadEntries && (stripped == 0 || removed.getComponent(Interactions.getComponentType()) != null)) {
                failures.add("the overrides were still on the entity after it left the world");
                return 0;
            }
        } finally {
            if (ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        return passed;
    }

    /** One tool through every state. Returns what went wrong, or null. */
    @Nullable
    private static String holdAndPutAway(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull InventoryComponent.Hotbar hotbar,
        byte held,
        byte empty,
        @Nonnull String toolId,
        @Nonnull Map<InteractionType, String> want,
        @Nonnull String foreign
    ) {
        hotbar.getInventory().setItemStackForSlot(held, new ItemStack(toolId));
        hotbar.setActiveSlot(held, ref, store);
        ToolKeyPromptOverrides.reconcile(ref, store);
        if (!want.equals(entries(store, ref))) {
            return "in hand the overrides are " + entries(store, ref) + ", expected " + want;
        }
        if (ToolKeyPromptOverrides.reconcile(ref, store) != 0) {
            return "a second pass changed the overrides again";
        }
        if (want.isEmpty()) {
            // A tool whose only prompt is the one the client lists from the item itself.
            return store.getComponent(ref, Interactions.getComponentType()) == null ? null : "a component was added for nothing";
        }
        Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
        if (interactions == null) {
            return "no override component in hand";
        }
        // Someone else takes one of the inputs, as a game mode or the creative eraser would.
        InteractionType taken = want.keySet().iterator().next();
        interactions.setInteractionId(taken, foreign);
        ToolKeyPromptOverrides.reconcile(ref, store);
        Map<InteractionType, String> withForeign = new EnumMap<>(want);
        withForeign.put(taken, foreign);
        if (!withForeign.equals(entries(store, ref))) {
            return "someone else's " + taken + " override was not left alone: " + entries(store, ref);
        }
        // Put away: only the other owner's entry may remain.
        hotbar.setActiveSlot(empty, ref, store);
        ToolKeyPromptOverrides.reconcile(ref, store);
        if (!Map.of(taken, foreign).equals(entries(store, ref))) {
            return "put away, the overrides are " + entries(store, ref) + ", expected only " + taken + " of the other owner";
        }
        interactions = store.getComponent(ref, Interactions.getComponentType());
        if (interactions == null) {
            return "the other owner's component was removed";
        }
        interactions.removeInteractionId(taken);
        // In hand again, then a spectator style override that replaces every input: the tool does nothing there.
        hotbar.setActiveSlot(held, ref, store);
        ToolKeyPromptOverrides.reconcile(ref, store);
        if (!want.equals(entries(store, ref))) {
            return "back in hand the overrides are " + entries(store, ref) + ", expected " + want;
        }
        interactions = store.getComponent(ref, Interactions.getComponentType());
        if (interactions == null) {
            return "no override component back in hand";
        }
        interactions.setOverrideAll(true);
        ToolKeyPromptOverrides.reconcile(ref, store);
        if (!entries(store, ref).isEmpty()) {
            return "entries stayed while every input was overridden: " + entries(store, ref);
        }
        interactions.setOverrideAll(false);
        ToolKeyPromptOverrides.reconcile(ref, store);
        if (!want.equals(entries(store, ref))) {
            return "after the spectator style override the overrides are " + entries(store, ref) + ", expected " + want;
        }
        // Put away with nobody else there: the component itself goes.
        hotbar.setActiveSlot(empty, ref, store);
        ToolKeyPromptOverrides.reconcile(ref, store);
        if (store.getComponent(ref, Interactions.getComponentType()) != null) {
            return "put away, the override component stayed: " + entries(store, ref);
        }
        return null;
    }

    @Nonnull
    private static Map<InteractionType, String> entries(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
        return interactions == null ? Map.of() : interactions.getInteractions();
    }
}
