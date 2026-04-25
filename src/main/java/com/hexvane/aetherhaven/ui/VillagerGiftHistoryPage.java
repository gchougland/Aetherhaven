package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerGiftLogEntry;
import com.hexvane.aetherhaven.villager.gift.GiftPreference;
import com.hexvane.aetherhaven.villager.gift.VillagerGiftService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Town gift log for one villager role, filtered to the viewing player. Only item ids the player has actually gifted
 * appear; duplicates are merged to a single entry per item (latest gift defines reaction tier). Shown as a wrapping
 * item grid per reaction tier.
 */
public final class VillagerGiftHistoryPage extends InteractiveCustomUIPage<VillagerGiftHistoryPage.PageData> {
    private static final String ROWS = "#Content #GiftListPanel #ListScroll #Rows";
    private static final int MAX_ICONS_PER_TIER = 200;

    private final UUID townId;
    @Nonnull
    private final String npcRoleId;
    @Nonnull
    private final UUID villagerEntityUuid;
    private final int returnVillagerIndex;
    @Nullable
    private final Ref<ChunkStore> managementBlockRef;
    @Nullable
    private final Vector3i managementBlockPos;
    private boolean templateAppended;

    public VillagerGiftHistoryPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        @Nonnull String npcRoleId,
        @Nonnull UUID villagerEntityUuid,
        int returnVillagerIndex,
        @Nullable Ref<ChunkStore> managementBlockRef,
        @Nullable Vector3i managementBlockPos
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.npcRoleId = npcRoleId.trim();
        this.villagerEntityUuid = villagerEntityUuid;
        this.returnVillagerIndex = returnVillagerIndex;
        this.managementBlockRef = managementBlockRef;
        this.managementBlockPos = managementBlockPos != null ? managementBlockPos.clone() : null;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/VillagerGiftHistory.ui");
            templateAppended = true;
        }
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#GiftHistoryBack",
            new EventData().append("Action", "BackToNeeds"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#GiftCyclePrev",
            new EventData().append("Action", "CyclePrev"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#GiftCycleNext",
            new EventData().append("Action", "CycleNext"),
            false
        );
        commandBuilder.set("#Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(npcRoleId));
        commandBuilder.set(
            "#VillagerName.TextSpans",
            Message.translation("server.npcRoles." + npcRoleId + ".name")
        );
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            commandBuilder.set("#GiftCycleRow.Visible", false);
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#ListScroll.Visible", false);
            commandBuilder.set("#GiftWeekLine.Visible", false);
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            commandBuilder.set("#GiftCycleRow.Visible", false);
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#ListScroll.Visible", false);
            commandBuilder.set("#GiftWeekLine.Visible", false);
            return;
        }
        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu == null) {
            commandBuilder.set("#GiftCycleRow.Visible", false);
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#ListScroll.Visible", false);
            commandBuilder.set("#GiftWeekLine.Visible", false);
            return;
        }
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<TownVillagerRow> residents = TownVillagerDirectory.listResidents(entityStore, town);
        commandBuilder.set("#GiftCycleRow.Visible", !residents.isEmpty());
        long day = VillagerReputationService.currentGameEpochDay(store);
        long weekBlock = day / 7L;
        VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), villagerEntityUuid);
        int usedThisWeek;
        if (e.getGiftWeekBlockId() == null || e.getGiftWeekBlockId() != weekBlock) {
            usedThisWeek = 0;
        } else {
            usedThisWeek = Math.min(VillagerGiftService.MAX_GIFTS_PER_WEEK_BLOCK, e.getGiftsThisWeekBlock());
        }
        commandBuilder.set("#GiftWeekLine.Visible", true);
        commandBuilder.set(
            "#GiftWeekLine.TextSpans",
            Message.translation("server.aetherhaven.ui.giftHistory.giftsThisWeek")
                .param("used", String.valueOf(usedThisWeek))
                .param("max", String.valueOf(VillagerGiftService.MAX_GIFTS_PER_WEEK_BLOCK))
        );
        String giver = pu.getUuid().toString();
        List<VillagerGiftLogEntry> all = town.getVillagerGiftLogByRoleId().getOrDefault(npcRoleId, List.of());
        Map<String, VillagerGiftLogEntry> latestByItem = new HashMap<>();
        for (VillagerGiftLogEntry ent : all) {
            if (ent == null || !giver.equals(ent.getGiverPlayerUuid())) {
                continue;
            }
            String iid = ent.getItemId().trim();
            if (iid.isEmpty()) {
                continue;
            }
            VillagerGiftLogEntry prev = latestByItem.get(iid);
            if (prev == null || ent.getGameEpochDay() > prev.getGameEpochDay()) {
                latestByItem.put(iid, ent);
            }
        }
        if (latestByItem.isEmpty()) {
            commandBuilder.set("#EmptyHint.Visible", true);
            commandBuilder.set("#ListScroll.Visible", false);
            commandBuilder.clear(ROWS);
        } else {
            Map<GiftPreference, List<VillagerGiftLogEntry>> byTier = new EnumMap<>(GiftPreference.class);
            for (GiftPreference p : GiftPreference.values()) {
                byTier.put(p, new ArrayList<>());
            }
            for (VillagerGiftLogEntry ent : latestByItem.values()) {
                byTier.get(ent.getTier()).add(ent);
            }
            for (List<VillagerGiftLogEntry> list : byTier.values()) {
                list.sort(Comparator.comparingLong(VillagerGiftLogEntry::getGameEpochDay).reversed());
            }
            List<String> loves = tierItemIds(byTier, GiftPreference.LOVE);
            List<String> likes = tierItemIds(byTier, GiftPreference.LIKE);
            List<String> neutrals = tierItemIds(byTier, GiftPreference.NEUTRAL);
            List<String> dislikes = tierItemIds(byTier, GiftPreference.DISLIKE);
            commandBuilder.set("#EmptyHint.Visible", false);
            commandBuilder.set("#ListScroll.Visible", true);
            commandBuilder.clear(ROWS);
            int slot = 0;
            GiftPreference[] order = {GiftPreference.LOVE, GiftPreference.LIKE, GiftPreference.NEUTRAL, GiftPreference.DISLIKE};
            for (GiftPreference p : order) {
                List<String> items =
                    switch (p) {
                        case LOVE -> loves;
                        case LIKE -> likes;
                        case NEUTRAL -> neutrals;
                        case DISLIKE -> dislikes;
                    };
                if (items == null || items.isEmpty()) {
                    continue;
                }
                commandBuilder.append(ROWS, "Aetherhaven/VillagerGiftHistoryTierBlock.ui");
                String block = ROWS + "[" + slot + "]";
                commandBuilder.set(
                    block + " #Section.TextSpans",
                    Message.translation(tierSectionKey(p))
                );
                int n = Math.min(items.size(), MAX_ICONS_PER_TIER);
                ItemGridSlot[] gridSlots = new ItemGridSlot[n];
                for (int i = 0; i < n; i++) {
                    gridSlots[i] = new ItemGridSlot(new ItemStack(items.get(i), 1));
                }
                commandBuilder.set(block + " #IconGrid.Slots", gridSlots);
                slot++;
            }
        }
    }

    @Nonnull
    private static List<String> tierItemIds(
        @Nonnull Map<GiftPreference, List<VillagerGiftLogEntry>> byTier, @Nonnull GiftPreference tier
    ) {
        List<VillagerGiftLogEntry> list = byTier.get(tier);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (VillagerGiftLogEntry e : list) {
            if (e != null) {
                out.add(e.getItemId().trim());
            }
        }
        return out;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        if (data.action.equalsIgnoreCase("CycleNext") || data.action.equalsIgnoreCase("CyclePrev")) {
            openCycledVillager(ref, store, data.action.equalsIgnoreCase("CycleNext") ? 1 : -1);
            return;
        }
        if (data.action.equalsIgnoreCase("BackToNeeds")) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            player
                .getPageManager()
                .openCustomPage(
                    ref,
                    store,
                    new VillagerNeedsOverviewPage(
                        playerRef,
                        townId,
                        managementBlockRef,
                        managementBlockPos,
                        returnVillagerIndex
                    )
                );
        }
    }

    private void openCycledVillager(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, int delta) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            return;
        }
        List<TownVillagerRow> rows = TownVillagerDirectory.listResidents(world.getEntityStore().getStore(), town);
        if (rows.isEmpty()) {
            return;
        }
        int idx = TownVillagerDirectory.indexOfEntity(rows, villagerEntityUuid);
        if (idx < 0) {
            idx = Math.min(Math.max(0, returnVillagerIndex), rows.size() - 1);
        }
        int n = rows.size();
        int newIdx = (idx + delta + n) % n;
        TownVillagerRow v = rows.get(newIdx);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player
            .getPageManager()
            .openCustomPage(
                ref,
                store,
                new VillagerGiftHistoryPage(
                    playerRef,
                    townId,
                    v.roleId(),
                    v.entityUuid(),
                    newIdx,
                    managementBlockRef,
                    managementBlockPos
                )
            );
    }

    @Nonnull
    private static String tierSectionKey(@Nonnull GiftPreference p) {
        return switch (p) {
            case LOVE -> "server.aetherhaven.ui.giftHistory.section.love";
            case LIKE -> "server.aetherhaven.ui.giftHistory.section.like";
            case NEUTRAL -> "server.aetherhaven.ui.giftHistory.section.neutral";
            case DISLIKE -> "server.aetherhaven.ui.giftHistory.section.dislike";
        };
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .build();

        @Nullable
        private String action;
    }
}
