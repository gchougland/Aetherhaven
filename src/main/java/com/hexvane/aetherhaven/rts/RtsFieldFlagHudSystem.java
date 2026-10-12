package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.rts.ui.RtsCommandHudSupport;
import com.hexvane.aetherhaven.rts.ui.RtsCommandStatusHud;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shows the guard command panel while the Command Flag is held away from the command post, and takes it down when
 * the flag is put away. At the command post the session keeps the same panel up itself, so this system stands aside.
 */
public final class RtsFieldFlagHudSystem extends EntityTickingSystem<EntityStore> {
    /** Town and guards are counted again this often, in ticks. The panel is only sent when something changed. */
    static final int RECOUNT_TICKS = 10;

    /** What the panel last showed a player, and how many ticks ago it was counted. */
    private record Shown(long signature, @Nonnull RtsOrderMode mode, int age) {}

    /** Players this system is showing the panel to. */
    private static final ConcurrentHashMap<UUID, Shown> SHOWN = new ConcurrentHashMap<>();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || pr == null) {
            return;
        }
        UUID id = pr.getUuid();
        RtsCommandPlayerComponent session = store.getComponent(ref, RtsCommandPlayerComponent.getComponentType());
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        boolean customPageOpen = player.getPageManager().getCustomPage() != null;
        switch (panelFor(session != null && session.isActive(), holdsFlag(hand), customPageOpen)) {
            case COMMAND_POST -> SHOWN.remove(id);
            case NONE -> {
                if (SHOWN.remove(id) != null) {
                    RtsCommandHudSupport.removeHud(player, pr);
                }
            }
            case FIELD -> showField(ref, store, player, pr, id);
        }
    }

    private static void showField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef pr,
        @Nonnull UUID id
    ) {
        Shown shown = SHOWN.get(id);
        RtsOrderMode mode = RtsFieldCommand.orderModeOf(ref, store);
        boolean panelUp = player.getHudManager().getCustomHud(AetherhavenConstants.RTS_COMMAND_HUD_KEY) instanceof RtsCommandStatusHud;
        // The order mode changes on a key press and must show at once. Counting guards can wait a few ticks.
        if (shown != null && panelUp && shown.mode() == mode && shown.age() < RECOUNT_TICKS) {
            SHOWN.put(id, new Shown(shown.signature(), mode, shown.age() + 1));
            return;
        }
        TownRecord town = RtsFieldCommand.townOf(ref, store);
        List<Ref<EntityStore>> guards = RtsFieldCommand.guardsOf(town, store);
        int underOrders = RtsFieldCommand.underOrders(guards, store);
        long signature = signature(mode, town != null ? town.getTownId() : null, guards.size(), underOrders);
        if (shown == null || !panelUp || shown.signature() != signature) {
            RtsCommandHudSupport
                .obtainHud(player, pr)
                .refreshField(mode, guardsState(town != null, guards.size()), guards.size(), underOrders);
        }
        SHOWN.put(id, new Shown(signature, mode, 0));
    }

    /** Which panel a player should see. */
    enum Panel {
        /** The command post session keeps the panel up. */
        COMMAND_POST,
        /** The flag is in hand away from the command post. */
        FIELD,
        /** Nothing to show. */
        NONE
    }

    @Nonnull
    static Panel panelFor(boolean atCommandPost, boolean holdsFlag, boolean customPageOpen) {
        if (atCommandPost) {
            return Panel.COMMAND_POST;
        }
        return holdsFlag && !customPageOpen ? Panel.FIELD : Panel.NONE;
    }

    @Nonnull
    static RtsCommandStatusHud.FieldGuards guardsState(boolean hasTown, int guards) {
        if (!hasTown) {
            return RtsCommandStatusHud.FieldGuards.NO_TOWN;
        }
        return guards > 0 ? RtsCommandStatusHud.FieldGuards.LISTENING : RtsCommandStatusHud.FieldGuards.NONE;
    }

    static boolean holdsFlag(@Nullable ItemStack hand) {
        return hand != null && !hand.isEmpty() && AetherhavenConstants.RTS_FLAG_ITEM_ID.equals(hand.getItemId());
    }

    /** Changes whenever anything the panel shows does. */
    static long signature(@Nonnull RtsOrderMode mode, @Nullable UUID townId, int guards, int underOrders) {
        long h = townId != null ? townId.hashCode() + 1L : 0L;
        h = 31 * h + mode.ordinal();
        h = 31 * h + guards;
        h = 31 * h + underOrders;
        return h;
    }
}
