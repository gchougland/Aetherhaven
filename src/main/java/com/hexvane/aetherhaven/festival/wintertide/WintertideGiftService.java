package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.calendar.VillagerBirthdayService;
import com.hexvane.aetherhaven.dialogue.DialogueActionBatchResult;
import com.hexvane.aetherhaven.festival.FestivalAttendanceService;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerLookup;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hexvane.aetherhaven.town.VillagerGiftLogEntry;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftRules;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Applies Wintertide gifts, tickets, and incoming presents. */
public final class WintertideGiftService {
    private WintertideGiftService() {}

    public static boolean isWintertideActive(@Nullable TownRecord town) {
        return town != null && WintertideIds.FESTIVAL_ID.equals(town.getActiveFestivalId());
    }

    @Nonnull
    public static WintertideSession sessionFor(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        WintertideSession session = WintertideSessionIndex.getOrCreate(town.getTownId());
        ensureAssignments(town, store, session);
        return session;
    }

    public static void ensureAssignments(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull WintertideSession session
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        long year = 1L;
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr != null) {
            year = AetherhavenCalendar.from(wtr.getGameDateTime()).year();
        }
        if (session.getYear() != year) {
            session.clearAll();
            session.setYear(year);
        }
        List<WintertideAssignmentService.PlayerMember> players = townMembers(world, town);
        List<WintertideAssignmentService.Resident> residents = giftableResidents(store, plugin, town.getTownId());
        Set<UUID> before = new LinkedHashSet<>(session.assignedVillagerUuids());
        WintertideAssignmentService.assignAll(
            session, players, residents, WintertideAssignmentService.seedFor(town.getTownId(), year)
        );
        List<UUID> newlyAssigned = new ArrayList<>();
        for (UUID villagerUuid : session.assignedVillagerUuids()) {
            if (!before.contains(villagerUuid)) {
                newlyAssigned.add(villagerUuid);
            }
        }
        if (!newlyAssigned.isEmpty()) {
            FestivalAttendanceService.interruptVillagerUuids(store, newlyAssigned);
        }
    }

    public static boolean canGiveToVillager(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (!isWintertideActive(town) || npcRef == null || !npcRef.isValid()) {
            return false;
        }
        WintertideSession session = sessionFor(town, store);
        if (!session.isCeremonyStarted(playerUuid) || session.hasGiven(playerUuid)) {
            return false;
        }
        WintertideTarget target = session.getOutgoing(playerUuid);
        if (target == null || !target.isVillager()) {
            return false;
        }
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        return nu != null && target.getUuid().equals(nu.getUuid());
    }

    public static boolean holdingItem(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        ItemStack inHand = hotbar != null ? hotbar.getActiveItem() : null;
        return !ItemStack.isEmpty(inHand);
    }

    public static void applyVillagerGift(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull DialogueActionBatchResult out
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (plugin == null || player == null || pu == null || npcRef == null || !npcRef.isValid()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = resolveTown(playerRef, store, npcRef);
        if (town == null || !town.hasMemberOrOwner(pu.getUuid()) || !canGiveToVillager(town, store, pu.getUuid(), npcRef)) {
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        ItemStack inHand = hotbar != null ? hotbar.getActiveItem() : null;
        if (ItemStack.isEmpty(inHand)) {
            return;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return;
        }
        VillagerDefinition def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
        GiftPreference tier = VillagerGiftRules.classifyItem(inHand.getItemId(), def);
        if (!removeOneFromActiveHotbar(playerRef, store, inHand)) {
            return;
        }
        applyReputation(playerRef, store, npcRef, plugin, tm, town, def, npc.getRoleName().trim(), inHand.getItemId(), tier);
        awardTickets(player, playerRef, store, WintertideIds.ticketCount(tier));
        VillagerGiftService.playLoveGiftParticles(npcRef, store);
        WintertideSession session = sessionFor(town, store);
        session.markGiven(pu.getUuid());
        session.setLastOutgoingPreference(pu.getUuid(), tier);
        queueIncomingSeek(store, session, pu.getUuid());
        String node =
            switch (tier) {
                case LOVE -> "gift_love";
                case LIKE -> "gift_like";
                case NEUTRAL -> "gift_neutral";
                case DISLIKE -> "gift_dislike";
            };
        Ref<EntityStore> villagerRef = npcRef;
        out.setCloseDialogue(true);
        out.setAfterClose(
            () -> openDialogue(playerRef, store, villagerRef, WintertideIds.DIALOGUE_GIFT_REACTION, node)
        );
    }

    public static void beginPlayerGift(
        @Nonnull Ref<EntityStore> giverRef,
        @Nonnull Ref<EntityStore> receiverRef,
        @Nonnull Store<EntityStore> store
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent giverUuid = store.getComponent(giverRef, UUIDComponent.getComponentType());
        UUIDComponent receiverUuid = store.getComponent(receiverRef, UUIDComponent.getComponentType());
        Player giver = store.getComponent(giverRef, Player.getComponentType());
        Player receiver = store.getComponent(receiverRef, Player.getComponentType());
        PlayerRef receiverPr = store.getComponent(receiverRef, PlayerRef.getComponentType());
        if (plugin == null
            || giverUuid == null
            || receiverUuid == null
            || giver == null
            || receiver == null
            || receiverPr == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord town = resolveTown(giverRef, store, null);
        if (town == null || !isWintertideActive(town) || !town.hasMemberOrOwner(giverUuid.getUuid())) {
            return;
        }
        WintertideSession session = sessionFor(town, store);
        WintertideTarget target = session.getOutgoing(giverUuid.getUuid());
        if (target == null
            || !target.isPlayer()
            || !receiverUuid.getUuid().equals(target.getUuid())
            || !session.isCeremonyStarted(giverUuid.getUuid())
            || session.hasGiven(giverUuid.getUuid())) {
            return;
        }
        if (session.getPendingPlayerGift() != null) {
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(giverRef, InventoryComponent.Hotbar.getComponentType());
        ItemStack inHand = hotbar != null ? hotbar.getActiveItem() : null;
        if (ItemStack.isEmpty(inHand) || !removeOneFromActiveHotbar(giverRef, store, inHand)) {
            return;
        }
        session.setPendingPlayerGift(
            new WintertideSession.PendingPlayerGift(
                giverUuid.getUuid(), receiverUuid.getUuid(), inHand.getItemId(), 1
            )
        );
        openDialogue(receiverRef, store, null, WintertideIds.DIALOGUE_PLAYER_RATE, "rate");
    }

    public static void applyPlayerRating(
        @Nonnull Ref<EntityStore> receiverRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull GiftPreference preference,
        @Nonnull DialogueActionBatchResult out
    ) {
        UUIDComponent receiverUuid = store.getComponent(receiverRef, UUIDComponent.getComponentType());
        TownRecord town = resolveTown(receiverRef, store, null);
        if (receiverUuid == null || town == null) {
            out.setCloseDialogue(true);
            return;
        }
        WintertideSession session = sessionFor(town, store);
        WintertideSession.PendingPlayerGift pending = session.getPendingPlayerGift();
        if (pending == null || !pending.receiverUuid().equals(receiverUuid.getUuid())) {
            out.setCloseDialogue(true);
            return;
        }
        Ref<EntityStore> giverRef = store.getExternalData().getRefFromUUID(pending.giverUuid());
        Player giver = giverRef != null && giverRef.isValid()
            ? store.getComponent(giverRef, Player.getComponentType())
            : null;
        Player receiver = store.getComponent(receiverRef, Player.getComponentType());
        if (receiver != null) {
            receiver.giveItem(new ItemStack(pending.itemId(), Math.max(1, pending.quantity())), receiverRef, store);
        }
        if (giver != null && giverRef != null) {
            awardTickets(giver, giverRef, store, WintertideIds.ticketCount(preference));
        }
        session.markGiven(pending.giverUuid());
        session.setLastOutgoingPreference(pending.giverUuid(), preference);
        session.setPendingPlayerGift(null);
        if (giverRef != null && giverRef.isValid()) {
            queueIncomingSeek(store, session, pending.giverUuid());
        }
        out.setCloseDialogue(true);
    }

    public static void onDialogueDismissed(
        @Nonnull String treeId,
        @Nonnull UUID playerUuid,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (WintertideIds.DIALOGUE_INCOMING.equals(treeId)) {
            Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(playerUuid);
            if (playerRef != null && playerRef.isValid()) {
                giveIncomingGift(playerRef, store, npcRef);
            }
            return;
        }
        if (!WintertideIds.DIALOGUE_PLAYER_RATE.equals(treeId)) {
            return;
        }
        TownRecord town = resolveTownByPlayer(store, playerUuid);
        if (town == null) {
            return;
        }
        WintertideSession session = WintertideSessionIndex.get(town.getTownId());
        if (session == null) {
            return;
        }
        WintertideSession.PendingPlayerGift pending = session.getPendingPlayerGift();
        if (pending == null || !pending.receiverUuid().equals(playerUuid)) {
            return;
        }
        Ref<EntityStore> giverRef = store.getExternalData().getRefFromUUID(pending.giverUuid());
        if (giverRef != null && giverRef.isValid()) {
            Player giver = store.getComponent(giverRef, Player.getComponentType());
            if (giver != null) {
                giver.giveItem(new ItemStack(pending.itemId(), Math.max(1, pending.quantity())), giverRef, store);
            }
        }
        session.setPendingPlayerGift(null);
    }

    public static void giveIncomingGift(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        TownRecord town = resolveTown(playerRef, store, npcRef);
        if (pu == null || player == null || town == null) {
            return;
        }
        WintertideSession session = sessionFor(town, store);
        if (session.hasReceived(pu.getUuid())) {
            return;
        }
        WintertideTarget incoming = session.getIncoming(pu.getUuid());
        if (incoming == null || !incoming.isVillager()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        VillagerDefinition def = null;
        String kind = incoming.getVillagerKind();
        if (plugin != null && npcRef != null && npcRef.isValid()) {
            NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npc != null && npc.getRoleName() != null) {
                def = plugin.getVillagerDefinitionCatalog().byNpcRoleId(npc.getRoleName().trim());
            }
        }
        Random rnd = new Random(
            WintertideAssignmentService.seedFor(town.getTownId(), session.getYear())
                ^ pu.getUuid().getLeastSignificantBits()
                ^ incoming.getUuid().getMostSignificantBits()
        );
        for (ItemStack stack : WintertideGifts.toItemStacks(WintertideGifts.pick(kind, def, rnd))) {
            player.giveItem(stack, playerRef, store);
        }
        session.markReceived(pu.getUuid());
        if (npcRef != null && npcRef.isValid()) {
            WintertideGiftSeekSystem.clearSeek(npcRef, store);
        }
    }

    public static void queueIncomingSeek(
        @Nonnull Store<EntityStore> store,
        @Nonnull WintertideSession session,
        @Nonnull UUID playerUuid
    ) {
        if (session.hasReceived(playerUuid) || session.isSeekQueued(playerUuid)) {
            return;
        }
        WintertideTarget incoming = session.getIncoming(playerUuid);
        if (incoming == null || !incoming.isVillager()) {
            return;
        }
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(incoming.getUuid());
        if (npcRef == null || !npcRef.isValid()) {
            return;
        }
        WintertideGiftSeekSystem.startSeek(npcRef, store, playerUuid);
        session.markSeekQueued(playerUuid);
    }

    @Nonnull
    static List<WintertideAssignmentService.PlayerMember> townMembers(
        @Nonnull World world,
        @Nonnull TownRecord town
    ) {
        List<WintertideAssignmentService.PlayerMember> out = new ArrayList<>();
        out.add(
            new WintertideAssignmentService.PlayerMember(
                town.getOwnerUuid(), TownPlayerLookup.ownerDisplayName(world, town)
            )
        );
        for (UUID member : town.getMemberPlayerUuids()) {
            if (member.equals(town.getOwnerUuid())) {
                continue;
            }
            out.add(
                new WintertideAssignmentService.PlayerMember(
                    member, TownPlayerLookup.displayNameForUuid(world, member)
                )
            );
        }
        return out;
    }

    @Nonnull
    static List<WintertideAssignmentService.Resident> giftableResidents(
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID townId
    ) {
        List<WintertideAssignmentService.Resident> out = new ArrayList<>();
        VillagerDefinitionCatalog catalog = plugin.getVillagerDefinitionCatalog();
        store.forEachChunk(
            Query.and(
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType(),
                NPCEntity.getComponentType()
            ),
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TownVillagerBinding binding = chunk.getComponent(i, TownVillagerBinding.getComponentType());
                    UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                    if (binding == null || uc == null || npc == null) {
                        continue;
                    }
                    if (!townId.equals(binding.getTownId())) {
                        continue;
                    }
                    String kind = binding.getKind();
                    if (kind == null
                        || TownVillagerBinding.isVisitorKind(kind)
                        || TownVillagerBinding.isRescueKind(kind)
                        || TownVillagerBinding.KIND_GUARD.equals(kind)
                        || TownVillagerBinding.KIND_TOWNSFOLK.equals(kind)) {
                        continue;
                    }
                    String role = npc.getRoleName() != null ? npc.getRoleName().trim() : "";
                    VillagerDefinition def = catalog.byNpcRoleId(role);
                    if (def == null) {
                        continue;
                    }
                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                    String name = def.getDisplayName();
                    if (ref != null && ref.isValid()) {
                        name = TownResidentDisplay.resolveFromEntity(store, ref, role, plugin).displayName();
                    }
                    out.add(new WintertideAssignmentService.Resident(uc.getUuid(), kind, name));
                }
            }
        );
        return out;
    }

    private static void applyReputation(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nullable VillagerDefinition def,
        @Nonnull String roleName,
        @Nonnull String itemId,
        @Nonnull GiftPreference tier
    ) {
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (pu == null || nu == null) {
            return;
        }
        int delta = WintertideIds.reputationDelta(tier);
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        boolean birthdayToday = wtr != null && def != null && VillagerBirthdayService.isBirthdayToday(def, wtr.getGameDateTime());
        if (birthdayToday && delta > 0) {
            delta *= 2;
        }
        VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid());
        World world = store.getExternalData().getWorld();
        int before = e.getReputation();
        if (delta != 0) {
            VillagerReputationService.addReputationInternal(town, world, pu.getUuid(), nu.getUuid(), e, delta, tm);
        } else {
            tm.updateTown(town);
        }
        VillagerGiftService.notifyReputationChange(playerRef, store, e.getReputation() - before);
        long day = VillagerReputationService.currentGameEpochDay(store);
        town.appendVillagerGiftLog(roleName, new VillagerGiftLogEntry(itemId, tier, pu.getUuid().toString(), day));
        tm.updateTown(town);
    }

    private static void awardTickets(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        int count
    ) {
        if (count <= 0) {
            return;
        }
        player.giveItem(new ItemStack(WintertideIds.WINTER_TICKET_ITEM_ID, count), playerRef, store);
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr != null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation("aetherhaven_festivals.aetherhaven.festival.wintertide.tickets")
                    .param("count", Integer.toString(count)),
                NotificationStyle.Success
            );
        }
    }

    static void openDialogue(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull String treeId,
        @Nonnull String entryNodeId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (plugin == null || player == null || pr == null || plugin.getDialogueCatalog().get(treeId) == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        player
            .getPageManager()
            .openCustomPage(
                playerRef,
                store,
                new com.hexvane.aetherhaven.ui.DialoguePage(
                    pr,
                    plugin.getDialogueCatalog(),
                    plugin.createDialogueWorldView(world, npcRef),
                    treeId,
                    entryNodeId,
                    npcRef
                )
            );
    }

    private static boolean removeOneFromActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ItemStack inHand
    ) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }
        byte slot = hotbar.getActiveSlot();
        if (slot < 0) {
            return false;
        }
        ItemContainer container = hotbar.getInventory();
        int q = inHand.getQuantity();
        ItemStack replacement;
        if (q <= 1) {
            replacement = ItemStack.EMPTY;
        } else {
            ItemStack dec = inHand.withQuantity(q - 1);
            replacement = dec != null ? dec : ItemStack.EMPTY;
        }
        container.replaceItemStackInSlot(slot, inHand, replacement);
        return true;
    }

    @Nullable
    public static TownRecord resolveTown(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        if (npcRef != null && npcRef.isValid()) {
            UUIDComponent npcUuid = store.getComponent(npcRef, UUIDComponent.getComponentType());
            if (npcUuid != null) {
                String id = npcUuid.getUuid().toString();
                for (TownRecord town : tm.allTowns()) {
                    if (!isWintertideActive(town)) {
                        continue;
                    }
                    for (String raw : town.getActiveFestivalNpcEntityUuids()) {
                        if (raw != null && id.equalsIgnoreCase(raw.trim())) {
                            return town;
                        }
                    }
                }
            }
            TownVillagerBinding binding = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
            if (binding != null && binding.getTownId() != null) {
                TownRecord town = tm.getTown(binding.getTownId());
                if (isWintertideActive(town)) {
                    return town;
                }
            }
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (pu != null) {
            TownRecord home = tm.findTownForPlayerInWorld(pu.getUuid());
            if (isWintertideActive(home)) {
                return home;
            }
        }
        return null;
    }

    @Nullable
    public static TownRecord resolveTownByPlayerUuid(@Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
        return resolveTownByPlayer(store, playerUuid);
    }

    @Nullable
    private static TownRecord resolveTownByPlayer(@Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord home = tm.findTownForPlayerInWorld(playerUuid);
        return isWintertideActive(home) ? home : null;
    }
}
