package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.plot.GaiaStatueBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerRevivalService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class GaiaStatueRevivePage extends InteractiveCustomUIPage<GaiaStatueRevivePage.PageData> {
    private static final String ROWS = "#Rows";
    private static final int MAX_ROWS = 16;

    private final Ref<ChunkStore> statueBlockRef;
    private final Vector3i statueBlockWorldPos;
    private boolean templateAppended;

    public GaiaStatueRevivePage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<ChunkStore> statueBlockRef,
        @Nonnull Vector3i statueBlockWorldPos
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.statueBlockRef = statueBlockRef;
        this.statueBlockWorldPos = statueBlockWorldPos;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/GaiaStatueRevivePage.ui");
            templateAppended = true;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("server.aetherhaven.common.pluginNotLoaded"));
            commandBuilder.clear(ROWS);
            return;
        }
        Store<ChunkStore> cs = statueBlockRef.getStore();
        GaiaStatueBlock gb = cs.getComponent(statueBlockRef, GaiaStatueBlock.getComponentType());
        if (gb == null || gb.getTownId().isBlank()) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("server.aetherhaven.common.statueNotLinked"));
            commandBuilder.clear(ROWS);
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(gb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("server.aetherhaven.common.invalidTownLink"));
            commandBuilder.clear(ROWS);
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("server.aetherhaven.common.townNotFound"));
            commandBuilder.clear(ROWS);
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            commandBuilder.set("#Hint.TextSpans", Message.translation("server.aetherhaven.common.ownerOnlyStatue"));
            commandBuilder.clear(ROWS);
            return;
        }

        commandBuilder.set(
            "#Hint.TextSpans",
            Message.translation("server.aetherhaven.ui.gaiaStatue.hint")
                .param("cost", AetherhavenConstants.GAIA_STATUE_REVIVE_COST_ESSENCE)
        );
        commandBuilder.clear(ROWS);

        List<ResidentNpcRecord> candidates = ResidentRegistryService.revivalCandidatesMerged(town, store);
        int n = Math.min(candidates.size(), MAX_ROWS);
        for (int i = 0; i < n; i++) {
            ResidentNpcRecord r = candidates.get(i);
            String roleId = r.getNpcRoleId();
            Ref<EntityStore> ent = store.getExternalData().getRefFromUUID(r.getLastEntityUuid());
            boolean present = ent != null && ent.isValid();
            commandBuilder.append(ROWS, "Aetherhaven/GaiaStatueReviveRow.ui");
            String row = ROWS + "[" + i + "]";
            commandBuilder.set(row + " #Portrait.AssetPath", NpcPortraitProvider.portraitPathForRoleId(roleId));
            String profKey = NpcPortraitProvider.professionTranslationKey(roleId, r.getKind());
            Message statusMsg =
                present
                    ? Message.translation("server.aetherhaven.ui.gaiaStatue.status.present")
                    : Message.translation("server.aetherhaven.ui.gaiaStatue.status.missing");
            Message nameMsg = Message.translation("server.npcRoles." + roleId + ".name");
            commandBuilder.set(
                row + " #VillagerLine.TextSpans",
                Message.translation("server.aetherhaven.ui.gaiaStatue.row")
                    .param("name", nameMsg)
                    .param("profession", Message.translation(profKey))
                    .param("status", statusMsg)
            );
            commandBuilder.set(row + " #ReviveButton.Disabled", present);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                row + " #ReviveButton",
                new EventData().append("Action", "Revive").append("NpcRoleId", roleId),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null || !"Revive".equalsIgnoreCase(data.action) || data.npcRoleId == null || data.npcRoleId.isBlank()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null) {
            return;
        }
        Store<ChunkStore> cs = statueBlockRef.getStore();
        GaiaStatueBlock gb = cs.getComponent(statueBlockRef, GaiaStatueBlock.getComponentType());
        if (gb == null || gb.getTownId().isBlank()) {
            return;
        }
        UUID townUuid;
        try {
            townUuid = UUID.fromString(gb.getTownId().trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townUuid);
        if (town == null) {
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (uc == null || player == null || !town.getOwnerUuid().equals(uc.getUuid())) {
            return;
        }
        String role = data.npcRoleId.trim();
        ResidentNpcRecord record = null;
        for (ResidentNpcRecord r : ResidentRegistryService.revivalCandidatesMerged(town, store)) {
            if (role.equalsIgnoreCase(r.getNpcRoleId())) {
                record = r;
                break;
            }
        }
        if (record == null) {
            return;
        }
        String err = VillagerRevivalService.validateCanRevive(store, record);
        if (err != null) {
            if (pr != null) {
                pr.sendMessage(Message.raw(err));
            }
            refresh(ref, store);
            return;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        int need = AetherhavenConstants.GAIA_STATUE_REVIVE_COST_ESSENCE;
        int have = InventoryMaterials.count(inv, AetherhavenConstants.ITEM_LIFE_ESSENCE);
        if (have < need) {
            if (pr != null) {
                pr.sendMessage(
                    Message.translation("server.aetherhaven.ui.gaiaStatue.notEnoughEssence")
                        .param("need", String.valueOf(need))
                );
            }
            refresh(ref, store);
            return;
        }
        ItemStackTransaction tx = inv.removeItemStack(new ItemStack(AetherhavenConstants.ITEM_LIFE_ESSENCE, need));
        if (!tx.succeeded()) {
            if (pr != null) {
                pr.sendMessage(Message.translation("server.aetherhaven.ui.gaiaStatue.essenceRemoveFailed"));
            }
            refresh(ref, store);
            return;
        }
        Vector3d spawn =
            new Vector3d(
                statueBlockWorldPos.x + 0.5,
                statueBlockWorldPos.y,
                statueBlockWorldPos.z + 1.5
            );
        boolean ok = VillagerRevivalService.reviveResident(world, plugin, town, tm, store, record, spawn);
        if (pr != null) {
            pr.sendMessage(
                ok
                    ? Message.translation("server.aetherhaven.ui.gaiaStatue.reviveSuccess")
                    : Message.translation("server.aetherhaven.ui.gaiaStatue.reviveFailed")
            );
        }
        if (!ok) {
            player.giveItem(new ItemStack(AetherhavenConstants.ITEM_LIFE_ESSENCE, need), ref, store);
        }
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder ev = new UIEventBuilder();
        build(ref, cmd, ev, store);
        sendUpdate(cmd, ev, false);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
            .add()
            .append(new KeyedCodec<>("NpcRoleId", Codec.STRING), (d, v) -> d.npcRoleId = v, d -> d.npcRoleId)
            .add()
            .build();

        @Nullable
        private String action;

        @Nullable
        private String npcRoleId;
    }
}
