package com.hexvane.aetherhaven.festival.carnival;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.rescue.RescueVillagerSpawnService;
import com.hexvane.aetherhaven.rescue.RescueVillagerTrigger;
import com.hexvane.aetherhaven.rescue.RescueVillagerTriggers;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.DialoguePage;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Pays out carnival minigame rewards as soon as a round ends and opens the attendant result dialogue. Clearing the
 * session immediately prevents the game from staying busy when a player never talks to the NPC again.
 */
public final class CarnivalResultSettlement {
    private static final String WHEEL_TREE = "aetherhaven_festival_carnival_wheel";
    private static final String BALLOON_TREE = "aetherhaven_festival_carnival_balloon";
    private static final String WHACK_TREE = "aetherhaven_festival_carnival_whack";

    private CarnivalResultSettlement() {}

    public static void settleWheelAndPresent(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull CarnivalWheelSession session
    ) {
        if (session.getPhase() != CarnivalWheelSession.Phase.RESULTS) {
            return;
        }
        UUID playerUuid = session.getPlayerUuid();
        CarnivalWheelSession.Outcome outcome = session.getOutcome();
        session.clearGameplay();
        if (playerUuid == null) {
            return;
        }
        if (outcome == CarnivalWheelSession.Outcome.CLOWN) {
            settleClownWheel(store, town, playerUuid);
            return;
        }
        int tickets = outcome == CarnivalWheelSession.Outcome.WIN ? CarnivalIds.WHEEL_WIN_TICKETS : 0;
        String node = outcome == CarnivalWheelSession.Outcome.WIN ? "won" : "lost";
        present(store, town, playerUuid, WHEEL_TREE, node, CarnivalIds.WHEEL_NPC_ROLE, tickets, null, false);
    }

    private static void settleClownWheel(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid
    ) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        if (town.hasQuestCompleted(AetherhavenConstants.QUEST_CLOWN_RESCUE)) {
            present(store, town, playerUuid, WHEEL_TREE, "won_clown_already", CarnivalIds.WHEEL_NPC_ROLE, 0, null, false);
            return;
        }
        if (RescueVillagerSpawnService.townHasActiveRescueNpc(
            store, town.getTownId(), TownVillagerBinding.KIND_RESCUE_CLOWN
        )) {
            present(store, town, playerUuid, WHEEL_TREE, "won_clown_waiting", CarnivalIds.WHEEL_NPC_ROLE, 0, null, false);
            return;
        }
        world.execute(
            () -> {
                Store<EntityStore> live = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
                if (live == null) {
                    return;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin == null) {
                    return;
                }
                TownRecord liveTown =
                    AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(town.getTownId());
                if (liveTown == null || liveTown.hasQuestCompleted(AetherhavenConstants.QUEST_CLOWN_RESCUE)) {
                    present(
                        live,
                        liveTown != null ? liveTown : town,
                        playerUuid,
                        WHEEL_TREE,
                        "won_clown_already",
                        CarnivalIds.WHEEL_NPC_ROLE,
                        0,
                        null,
                        false
                    );
                    return;
                }
                if (RescueVillagerSpawnService.townHasActiveRescueNpc(
                    live, liveTown.getTownId(), TownVillagerBinding.KIND_RESCUE_CLOWN
                )) {
                    present(
                        live,
                        liveTown,
                        playerUuid,
                        WHEEL_TREE,
                        "won_clown_waiting",
                        CarnivalIds.WHEEL_NPC_ROLE,
                        0,
                        null,
                        false
                    );
                    return;
                }
                RescueVillagerTrigger trigger =
                    RescueVillagerTriggers.byBindingKind(TownVillagerBinding.KIND_RESCUE_CLOWN);
                Vector3d stand = CarnivalWheelPlacementService.resolveClownStandNearWheel(world, liveTown.getTownId());
                if (stand == null) {
                    Vector3d square = CarnivalAudio.squareCenter(plugin, liveTown);
                    if (square != null) {
                        int bx = (int) Math.floor(square.x);
                        int bz = (int) Math.floor(square.z);
                        int feetY =
                            com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.findStandY(
                                world, bx, bz, (int) Math.floor(square.y) + 2
                            );
                        if (feetY != Integer.MIN_VALUE) {
                            stand = new Vector3d(bx + 0.5, feetY, bz + 0.5);
                        }
                    }
                }
                if (trigger != null && stand != null) {
                    RescueVillagerSpawnService.spawnRescueAt(world, live, liveTown, stand, playerUuid, trigger);
                }
                present(live, liveTown, playerUuid, WHEEL_TREE, "won_clown", CarnivalIds.WHEEL_NPC_ROLE, 0, null, false);
            }
        );
    }

    public static void settleBalloonAndPresent(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull CarnivalBalloonSession session
    ) {
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid == null || !session.hasResult(playerUuid)) {
            return;
        }
        boolean perfect = session.isPendingPerfectClear(playerUuid);
        String cosmeticItemId = null;
        if (perfect) {
            Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
            if (playerRef != null && playerRef.isValid()) {
                cosmeticItemId = CarnivalDialogueHandlers.pickRandomUnownedBalloonHatItem(town, store, playerRef);
            }
        }
        int tickets = session.collectResult(playerUuid);
        if (tickets < 0) {
            return;
        }
        String node = "collect_result";
        if (perfect) {
            node = cosmeticItemId != null ? "collect_perfect" : "collect_perfect_already";
        }
        present(
            store,
            town,
            playerUuid,
            BALLOON_TREE,
            node,
            CarnivalIds.BALLOON_NPC_ROLE,
            tickets,
            cosmeticItemId,
            true
        );
    }

    public static void settleWhackAndPresent(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull CarnivalWhackSession session
    ) {
        UUID playerUuid = session.getPlayerUuid();
        if (playerUuid == null || !session.hasResult(playerUuid)) {
            return;
        }
        boolean perfect = session.isPendingPerfectClear(playerUuid);
        int tickets = session.collectResult(playerUuid);
        if (tickets < 0) {
            return;
        }
        CarnivalWhackClubUtil.removeAllWhackersForPlayer(store, playerUuid);
        String node = "collect_result";
        String cosmeticItemId = null;
        if (perfect && !town.hasVillagerCosmeticUnlocked(CarnivalIds.WHACK_PERFECT_COSMETIC_ID)) {
            cosmeticItemId = CarnivalIds.WHACK_PERFECT_COSMETIC_ITEM_ID;
            node = "collect_perfect";
        } else if (perfect) {
            node = "collect_perfect_already";
        }
        present(
            store,
            town,
            playerUuid,
            WHACK_TREE,
            node,
            CarnivalIds.WHACK_NPC_ROLE,
            tickets,
            cosmeticItemId,
            false
        );
    }

    private static void present(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull UUID playerUuid,
        @Nonnull String treeId,
        @Nonnull String entryNodeId,
        @Nonnull String attendantRoleId,
        int tickets,
        @Nullable String cosmeticItemId,
        boolean removeDarts
    ) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        UUID townId = town.getTownId();
        world.execute(
            () -> {
                Store<EntityStore> live = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
                if (live == null) {
                    return;
                }
                Ref<EntityStore> playerRef = live.getExternalData().getRefFromUUID(playerUuid);
                if (playerRef == null || !playerRef.isValid()) {
                    return;
                }
                Player player = live.getComponent(playerRef, Player.getComponentType());
                PlayerRef playerRefComp = live.getComponent(playerRef, PlayerRef.getComponentType());
                if (player == null || playerRefComp == null) {
                    return;
                }
                if (removeDarts) {
                    CarnivalDialogueHandlers.removeDarts(live, playerRef, CarnivalIds.BALLOON_DARTS);
                }
                if (tickets > 0) {
                    player.giveItem(new ItemStack(CarnivalIds.SUMMER_TICKET_ITEM_ID, tickets), playerRef, live);
                }
                if (cosmeticItemId != null && !cosmeticItemId.isBlank()) {
                    player.giveItem(new ItemStack(cosmeticItemId, 1), playerRef, live);
                }
                TownRecord liveTown = town;
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                if (plugin != null) {
                    TownRecord refreshed =
                        AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
                    if (refreshed != null) {
                        liveTown = refreshed;
                    }
                }
                Ref<EntityStore> attendantRef = findFestivalAttendant(live, liveTown, attendantRoleId);
                openResultDialogue(
                    live,
                    player,
                    playerRef,
                    playerRefComp,
                    world,
                    treeId,
                    entryNodeId,
                    attendantRef
                );
            }
        );
    }

    @Nullable
    private static Ref<EntityStore> findFestivalAttendant(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId
    ) {
        List<String> uuids = town.getActiveFestivalNpcEntityUuids();
        if (uuids == null || uuids.isEmpty()) {
            return null;
        }
        for (String raw : uuids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(UUID.fromString(raw.trim()));
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null && roleId.equals(npc.getRoleName())) {
                    return ref;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static void openResultDialogue(
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull PlayerRef playerRefComp,
        @Nonnull World world,
        @Nonnull String treeId,
        @Nonnull String entryNodeId,
        @Nullable Ref<EntityStore> attendantRef
    ) {
        if (!AetherhavenFeatures.isLoaded(AetherhavenPluginIds.DIALOGUE)) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null || plugin.getDialogueCatalog().get(treeId) == null) {
            return;
        }
        // Replace any open page (e.g. the "spinning" attendant line) with the result.
        player
            .getPageManager()
            .openCustomPage(
                playerRef,
                store,
                new DialoguePage(
                    playerRefComp,
                    plugin.getDialogueCatalog(),
                    plugin.createDialogueWorldView(world, attendantRef),
                    treeId,
                    entryNodeId,
                    attendantRef
                )
            );
    }
}
