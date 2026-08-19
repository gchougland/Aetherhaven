package com.hexvane.aetherhaven.calendar;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalRewardNotify;
import com.hexvane.aetherhaven.festival.wintertide.WintertideGifts;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.DialoguePage;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gives birthday presents and opens the birthday gift talk. */
public final class PlayerBirthdayGiftService {
    private PlayerBirthdayGiftService() {}

    public static void giveIncomingGift(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        Ref<EntityStore> giverRef = resolveGiverRef(playerRef, store, npcRef);
        if (pu == null || player == null || giverRef == null || !giverRef.isValid()) {
            return;
        }
        UUIDComponent nu = store.getComponent(giverRef, UUIDComponent.getComponentType());
        if (nu == null) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        long year = AetherhavenCalendar.from(wtr.getGameDateTime()).year();
        PlayerTownJournalState journal = store.getComponent(playerRef, PlayerTownJournalState.getComponentType());
        if (journal == null) {
            return;
        }
        if (PlayerBirthdayService.alreadyGiftedThisYear(journal, year, nu.getUuid())) {
            PlayerBirthdayGiftSeekSystem.clearSeek(giverRef, store);
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        VillagerDefinition def = null;
        String kind = null;
        TownVillagerBinding binding = store.getComponent(giverRef, TownVillagerBinding.getComponentType());
        if (binding != null) {
            kind = binding.getKind();
        }
        NPCEntity npc = store.getComponent(giverRef, NPCEntity.getComponentType());
        if (plugin != null && npc != null && npc.getRoleName() != null) {
            def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
        }
        PlayerTownJournalState next = (PlayerTownJournalState) journal.clone();
        next.markBirthdayGifted(nu.getUuid(), year);
        store.putComponent(playerRef, PlayerTownJournalState.getComponentType(), next);
        Random rnd =
            new Random(year ^ pu.getUuid().getLeastSignificantBits() ^ nu.getUuid().getMostSignificantBits());
        for (ItemStack stack : WintertideGifts.toItemStacks(WintertideGifts.pick(kind, def, rnd))) {
            FestivalRewardNotify.giveAndNotify(player, playerRef, store, stack);
        }
        PlayerBirthdayGiftSeekSystem.clearSeek(giverRef, store);
    }

    @Nullable
    private static Ref<EntityStore> resolveGiverRef(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (npcRef != null && npcRef.isValid()) {
            return npcRef;
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu == null) {
            return null;
        }
        return PlayerBirthdayGiftSeekSystem.findSeekingVillager(store, pu.getUuid());
    }

    public static void onDialogueDismissed(
        @Nonnull String treeId,
        @Nonnull UUID playerUuid,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (!PlayerBirthdayIds.DIALOGUE_INCOMING.equals(treeId)) {
            return;
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
        if (playerRef != null && playerRef.isValid()) {
            giveIncomingGift(playerRef, store, npcRef);
        }
    }

    static void openDialogue(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (plugin == null
            || player == null
            || pr == null
            || plugin.getDialogueCatalog().get(PlayerBirthdayIds.DIALOGUE_INCOMING) == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        player
            .getPageManager()
            .openCustomPage(
                playerRef,
                store,
                new DialoguePage(
                    pr,
                    plugin.getDialogueCatalog(),
                    plugin.createDialogueWorldView(world, npcRef),
                    PlayerBirthdayIds.DIALOGUE_INCOMING,
                    "gift",
                    npcRef
                )
            );
    }

    @Nullable
    static TownRecord townForVillager(@Nonnull Store<EntityStore> store, @Nullable UUID townId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null || townId == null) {
            return null;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        return tm.getTown(townId);
    }
}
