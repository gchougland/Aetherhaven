package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Gets every input of a held tool into the game's own key list, where each line carries the player's own key.
 *
 * <p><b>What the 0.7.0-pre.5.1 client lists.</b> It has two lists and each reads a different place.
 * <ul>
 *   <li>For the item in the hand it looks at one input only, {@link #HELD_ITEM_LISTED}: if the root interaction the
 *       item binds there has {@code HudInputBindingEntry}, one prompt is drawn beside the ability slots. Seen in game
 *       on October 11: this happens only for an item filed under {@code Items.Weapons} (the one item category name
 *       built into the client), and while such an item is held the longer list below is not shown at all, not even
 *       its Inventory and Map lines. So no tool of this mod is filed as a weapon.</li>
 *   <li>The longer list at the bottom right looks at eight inputs ({@link #CLIENT_OVERRIDE_LIST}), but it reads them
 *       from the player's own interaction overrides (the {@link Interactions} component on the player entity, which
 *       is how the spectator game mode shows its three prompts) and from a carried block. It never reads the held
 *       item.</li>
 * </ul>
 *
 * <p><b>What this does.</b> While a tool is the item the engine would run an input on, the root interaction the tool
 * binds to that input is also written into the player's overrides, for every input in {@link #MIRRORED}. The override
 * names exactly the root the item already binds, so the server and the client run what they ran before; the only
 * difference is that the client now lists it. {@link #HELD_ITEM_LISTED} is mirrored like the rest: for a tool, which
 * is not a weapon, the held item's own prompt is never drawn, so the list is the only place it can show.
 *
 * <p>The entries are owned by this class and by nothing else: an override that someone else put there (a game mode,
 * the creative eraser) is never replaced, as it took precedence over the held item before, and entries are taken off
 * again as soon as the tool is no longer in use, and before the player is saved.
 */
public final class ToolKeyPromptOverrides {
    /** The one input the client lists for a held item by itself, and only when the item is a weapon. */
    public static final InteractionType HELD_ITEM_LISTED = InteractionType.Ability4;

    /** The inputs the client lists from a player's own overrides, in the order it lists them (client table of eight). */
    public static final List<InteractionType> CLIENT_OVERRIDE_LIST = List.of(
        InteractionType.Primary,
        InteractionType.Secondary,
        InteractionType.Ability1,
        InteractionType.Ability2,
        InteractionType.Ability3,
        InteractionType.Ability4,
        InteractionType.Use,
        InteractionType.Pick
    );

    /**
     * The inputs copied from a held tool into the player's overrides: every input a tool binds. Not the two rune
     * ability inputs, which no tool binds and the engine reads from the ability slots.
     */
    public static final Set<InteractionType> MIRRORED = Collections.unmodifiableSet(EnumSet.of(
        InteractionType.Primary,
        InteractionType.Secondary,
        InteractionType.Ability1,
        InteractionType.Ability4,
        InteractionType.Use,
        InteractionType.Pick
    ));

    private ToolKeyPromptOverrides() {}

    /** One edit to a player's overrides. A null root takes the entry off. */
    public record Change(@Nonnull InteractionType type, @Nullable String rootId) {}

    /** Where the game lists the prompt of an input a tool binds, or null if it cannot list it at all. */
    public enum Listing {
        /** Beside the ability slots, from the held item alone. */
        HELD_ITEM,
        /** In the list at the bottom right, through the player's overrides. */
        PLAYER_OVERRIDE
    }

    @Nullable
    public static Listing listingOf(@Nonnull InteractionType type) {
        return MIRRORED.contains(type) ? Listing.PLAYER_OVERRIDE : null;
    }

    /**
     * The override entries a tool wants while it is in use: its own root for each mirrored input that has a prompt.
     *
     * @param toolInteractions the item's {@code Interactions} map
     * @param promptRoot whether a root interaction id carries one of this mod's tool prompts
     */
    @Nonnull
    public static Map<InteractionType, String> mirrored(
        @Nonnull Map<InteractionType, String> toolInteractions,
        @Nonnull Predicate<String> promptRoot
    ) {
        Map<InteractionType, String> out = new EnumMap<>(InteractionType.class);
        for (InteractionType type : MIRRORED) {
            String rootId = toolInteractions.get(type);
            if (rootId != null && promptRoot.test(rootId)) {
                out.put(type, rootId);
            }
        }
        return out;
    }

    /**
     * The edits that bring a player's overrides in line with what the tool in use wants. Stateless: it can be run at
     * any time, any number of times, and a missed run is made up by the next one.
     *
     * @param current the player's override entries now
     * @param overrideAll whether the overrides replace every held item input (spectators); nothing is added then,
     *     because the held tool does nothing in that state either
     * @param desired {@link #mirrored} for the tool in use, per input; empty when no tool is in use
     * @param promptRoot whether a root interaction id carries one of this mod's tool prompts
     */
    @Nonnull
    public static List<Change> plan(
        @Nonnull Map<InteractionType, String> current,
        boolean overrideAll,
        @Nonnull Map<InteractionType, String> desired,
        @Nonnull Predicate<String> promptRoot
    ) {
        List<Change> changes = new ArrayList<>();
        for (InteractionType type : InteractionType.values()) {
            String now = current.get(type);
            if (now != null && !promptRoot.test(now)) {
                // Someone else's override. It won over the held item before this class existed, and still does.
                continue;
            }
            String want = overrideAll || !MIRRORED.contains(type) ? null : desired.get(type);
            if (!Objects.equals(now, want)) {
                changes.add(new Change(type, want));
            }
        }
        return changes;
    }

    /** Applies {@link #plan} to a component. Returns whether anything changed. */
    public static boolean apply(@Nonnull Interactions interactions, @Nonnull List<Change> changes) {
        for (Change change : changes) {
            if (change.rootId() == null) {
                interactions.removeInteractionId(change.type());
            } else {
                interactions.setInteractionId(change.type(), change.rootId());
            }
        }
        return !changes.isEmpty();
    }

    /** Whether a root interaction that is loaded right now carries one of this mod's tool prompts. */
    public static boolean isPromptRoot(@Nullable String rootId) {
        if (rootId == null) {
            return false;
        }
        RootInteraction root = RootInteraction.getAssetMap().getAsset(rootId);
        return root != null && ToolKeyPrompts.isPromptKey(root.getHudInputBindingEntry());
    }

    /**
     * What the tool in use wants in the player's overrides, per input. The item is chosen by the engine's own rule
     * for the input ({@link InteractionContext#forInteraction}), so nothing is mirrored while a block is carried, a
     * builder tool is active, or the other hand takes the input.
     */
    @Nonnull
    public static Map<InteractionType, String> desired(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        ItemStack active = hotbar != null ? hotbar.getActiveItem() : null;
        if (active == null || active.isEmpty() || !ToolKeyPrompts.items().containsKey(active.getItemId())) {
            return Map.of();
        }
        if (accessor.getComponent(ref, CarriedBlock.getComponentType()) != null) {
            // The engine runs the carried block's interactions, or the empty hand's, never the hotbar item's.
            return Map.of();
        }
        InteractionManager manager = accessor.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        if (manager == null) {
            return Map.of();
        }
        Map<InteractionType, String> out = new EnumMap<>(InteractionType.class);
        for (InteractionType type : MIRRORED) {
            Item item = InteractionContext.forInteraction(manager, ref, type, accessor).getOriginalItemType();
            if (item == null || !ToolKeyPrompts.items().containsKey(item.getId())) {
                continue;
            }
            String rootId = item.getInteractions().get(type);
            if (isPromptRoot(rootId)) {
                out.put(type, rootId);
            }
        }
        return out;
    }

    /** The edits a player's overrides need right now. Read only. */
    @Nonnull
    public static List<Change> pending(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Interactions interactions = accessor.getComponent(ref, Interactions.getComponentType());
        InventoryComponent.Hotbar hotbar = accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        ItemStack active = hotbar != null ? hotbar.getActiveItem() : null;
        boolean toolInHand = active != null && !active.isEmpty() && ToolKeyPrompts.items().containsKey(active.getItemId());
        if (interactions == null && !toolInHand) {
            // Nearly every player on nearly every tick.
            return List.of();
        }
        return plan(
            interactions != null ? interactions.getInteractions() : Map.of(),
            interactions != null && interactions.isOverrideAll(),
            desired(ref, accessor),
            ToolKeyPromptOverrides::isPromptRoot
        );
    }

    /**
     * Brings a player's overrides in line with the tool in use. Adds and removes the component, so it must run where
     * the store may be changed: from {@code CommandBuffer.run} or {@code World.execute}, never inside a tick.
     *
     * @return how many entries changed
     */
    public static int reconcile(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!ref.isValid()) {
            return 0;
        }
        List<Change> changes = pending(ref, store);
        if (changes.isEmpty()) {
            return 0;
        }
        Interactions interactions = store.getComponent(ref, Interactions.getComponentType());
        if (interactions == null) {
            Interactions added = new Interactions();
            apply(added, changes);
            if (!added.isEmpty()) {
                store.putComponent(ref, Interactions.getComponentType(), added);
            }
            return changes.size();
        }
        apply(interactions, changes);
        if (interactions.isEmpty()) {
            store.tryRemoveComponent(ref, Interactions.getComponentType());
        }
        return changes.size();
    }

    /**
     * Takes this class's entries off an entity that is entering or leaving a world, so they are never saved with the
     * player. Returns how many were taken off.
     */
    public static int strip(@Nonnull Holder<EntityStore> holder) {
        Interactions interactions = holder.getComponent(Interactions.getComponentType());
        if (interactions == null) {
            return 0;
        }
        List<Change> changes = plan(interactions.getInteractions(), false, Map.of(), ToolKeyPromptOverrides::isPromptRoot);
        apply(interactions, changes);
        if (!changes.isEmpty() && interactions.isEmpty()) {
            holder.tryRemoveComponent(Interactions.getComponentType());
        }
        return changes.size();
    }

    /** Registers the systems that keep the overrides in step with the item in hand. */
    public static void register(@Nonnull JavaPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new ToolKeyPromptOverrideSystems.Tick());
        plugin.getEntityStoreRegistry().registerSystem(new ToolKeyPromptOverrideSystems.SlotChange());
        plugin.getEntityStoreRegistry().registerSystem(new ToolKeyPromptOverrideSystems.StripOnWorldChange());
        plugin.getEntityStoreRegistry().registerSystem(new ToolKeyPromptOverrideSystems.UseEntityGuard());
    }
}
