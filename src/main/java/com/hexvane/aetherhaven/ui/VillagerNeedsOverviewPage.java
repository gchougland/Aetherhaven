package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerNeedsOverviewPage extends InteractiveCustomUIPage<VillagerNeedsOverviewPage.PageData> {
    private static final String VILLAGER_ROWS = "#VillagerRows";
    private static final String REPUTATION_HEART_SLOTS = "#ReputationHeartSlots";
    private static final int MAX_ROWS = 16;

    private final UUID townId;
    private int selectedIndex;
    /** {@code append(ui)} must run only once per page instance; repeating it on every {@link #sendUpdate} duplicates the whole tree. */
    private boolean templateAppended;
    private boolean reputationHeartSlotsAppended;

    public VillagerNeedsOverviewPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/VillagerNeedsOverview.ui");
            templateAppended = true;
        }
        if (templateAppended && !reputationHeartSlotsAppended) {
            for (int h = 0; h < 10; h++) {
                commandBuilder.append(REPUTATION_HEART_SLOTS, "Aetherhaven/HeartSlot.ui");
            }
            reputationHeartSlotsAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("Aetherhaven not loaded."));
            return;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        if (town == null) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("Town not found."));
            return;
        }

        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        List<VillagerRow> rows = buildResidentRows(entityStore, town);
        if (rows.isEmpty()) {
            commandBuilder.set("#Hint.TextSpans", Message.raw("No town residents tracked yet."));
            commandBuilder.clear(VILLAGER_ROWS);
            return;
        }
        if (selectedIndex >= rows.size()) {
            selectedIndex = 0;
        }

        commandBuilder.set("#Hint.TextSpans", Message.raw("Residents with a town binding (inn visitors are not listed). Meters show loaded entities only."));
        commandBuilder.clear(VILLAGER_ROWS);
        int n = Math.min(rows.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            VillagerRow r = rows.get(i);
            commandBuilder.append(VILLAGER_ROWS, "Aetherhaven/VillagerNeedsRow.ui");
            String row = VILLAGER_ROWS + "[" + i + "]";
            commandBuilder.set(row + " #Pick #Label.TextSpans", Message.raw(r.label()));
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #Pick",
                new EventData().append("Action", "Select").append("Index", Integer.toString(i)),
                false
            );
        }

        VillagerRow sel = rows.get(selectedIndex);
        VillagerNeeds needs = findNeeds(entityStore, sel.entityUuid());
        float hunger = needs != null ? needs.getHunger() / VillagerNeeds.MAX : 0.5f;
        float energy = needs != null ? needs.getEnergy() / VillagerNeeds.MAX : 0.5f;
        float fun = needs != null ? needs.getFun() / VillagerNeeds.MAX : 0.5f;
        commandBuilder.set("#HungerBar.Value", hunger);
        commandBuilder.set("#EnergyBar.Value", energy);
        commandBuilder.set("#FunBar.Value", fun);
        commandBuilder.set("#Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(sel.roleId()));

        UUIDComponent pu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (pu != null) {
            int rep = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), sel.entityUuid()).getReputation();
            ReputationHeartUi.applyHearts(commandBuilder, REPUTATION_HEART_SLOTS, rep);
            commandBuilder.set(
                "#ReputationBlock.TooltipText",
                rep + "/" + VillagerReputationService.MAX_REPUTATION
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !data.action.equalsIgnoreCase("Select")) {
            return;
        }
        if (data.index >= 0 && data.index < MAX_ROWS) {
            selectedIndex = data.index;
        }
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    @Nonnull
    private static List<VillagerRow> buildResidentRows(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        UUID tid = town.getTownId();
        Map<UUID, VillagerRow> byUuid = new LinkedHashMap<>();
        Query<EntityStore> q =
            Query.and(TownVillagerBinding.getComponentType(), UUIDComponent.getComponentType(), NPCEntity.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    if (b == null || !tid.equals(b.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                        continue;
                    }
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                    if (uc == null || npc == null || npc.getRoleName() == null) {
                        continue;
                    }
                    UUID u = uc.getUuid();
                    String roleId = npc.getRoleName();
                    String label = NpcPortraitProvider.displayLabelForRoleId(roleId);
                    int ko = kindOrderForBindingKind(b.getKind());
                    byUuid.put(u, new VillagerRow(label, u, roleId, ko));
                }
            }
        );

        addFallbackIfMissing(byUuid, town.getElderEntityUuid(), AetherhavenConstants.ELDER_NPC_ROLE_ID);
        addFallbackIfMissing(byUuid, town.getInnkeeperEntityUuid(), AetherhavenConstants.INNKEEPER_NPC_ROLE_ID);

        List<VillagerRow> out = new ArrayList<>(byUuid.values());
        out.sort(
            Comparator.comparingInt(VillagerRow::kindOrder).thenComparing(VillagerRow::label, String.CASE_INSENSITIVE_ORDER)
        );
        return out;
    }

    private static void addFallbackIfMissing(
        @Nonnull Map<UUID, VillagerRow> byUuid,
        @Nullable UUID entityUuid,
        @Nonnull String roleId
    ) {
        if (entityUuid == null || byUuid.containsKey(entityUuid)) {
            return;
        }
        byUuid.put(
            entityUuid,
            new VillagerRow(NpcPortraitProvider.displayLabelForRoleId(roleId), entityUuid, roleId, kindOrderForRoleId(roleId))
        );
    }

    private static int kindOrderForBindingKind(@Nonnull String kind) {
        if (TownVillagerBinding.KIND_ELDER.equals(kind)) {
            return 0;
        }
        if (TownVillagerBinding.KIND_INNKEEPER.equals(kind)) {
            return 1;
        }
        if (TownVillagerBinding.KIND_MERCHANT.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_FARMER.equals(kind)) {
            return 2;
        }
        if (TownVillagerBinding.KIND_BLACKSMITH.equals(kind)) {
            return 2;
        }
        return 3;
    }

    private static int kindOrderForRoleId(@Nonnull String roleId) {
        if (AetherhavenConstants.ELDER_NPC_ROLE_ID.equals(roleId)) {
            return 0;
        }
        if (AetherhavenConstants.INNKEEPER_NPC_ROLE_ID.equals(roleId)) {
            return 1;
        }
        if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_FARMER.equals(roleId)) {
            return 2;
        }
        if (AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)) {
            return 2;
        }
        return 3;
    }

    @Nullable
    private static VillagerNeeds findNeeds(@Nonnull Store<EntityStore> store, @Nonnull UUID entityUuid) {
        VillagerNeeds[] found = new VillagerNeeds[1];
        store.forEachChunk(
            Query.and(VillagerNeeds.getComponentType(), UUIDComponent.getComponentType()),
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                if (found[0] != null) {
                    return;
                }
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    UUIDComponent u = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    if (u != null && entityUuid.equals(u.getUuid())) {
                        found[0] = archetypeChunk.getComponent(i, VillagerNeeds.getComponentType());
                        return;
                    }
                }
            }
        );
        return found[0];
    }

    private record VillagerRow(@Nonnull String label, @Nonnull UUID entityUuid, @Nonnull String roleId, int kindOrder) {}

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("Index", Codec.STRING), (d, s) -> {
                if (s != null && !s.isBlank()) {
                    try {
                        d.index = Integer.parseInt(s.trim());
                    } catch (NumberFormatException ignored) {
                        d.index = 0;
                    }
                }
            }, d -> Integer.toString(d.index))
            .add()
            .build();

        private String action;
        private int index;
    }
}
