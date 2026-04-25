package com.hexvane.aetherhaven.villager.gift;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.VillagerGiftLogEntry;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerGiftService {
    public static final int MAX_GIFTS_PER_WEEK_BLOCK = 2;

    private static final String GIFT_EMOTION_HEARTS = "Hearts";
    private static final String GIFT_EMOTION_HEARTS_SUBTLE = "Hearts_Subtle";
    private static final String GIFT_EMOTION_ANGRY = "Angry";
    /** Neutral or ambiguous reaction; from {@code Server/Particles/NPC/Emotions/Question_Subtle.particlesystem}. */
    private static final String GIFT_EMOTION_NEUTRAL = "Question_Subtle";

    private VillagerGiftService() {}

    public static boolean isVisitor(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid()) {
            return true;
        }
        TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        return b == null || TownVillagerBinding.isVisitorKind(b.getKind());
    }

    @Nonnull
    public static GiftEligibility canGift(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable TownRecord town,
        @Nullable Ref<EntityStore> npcRef
    ) {
        if (town == null || npcRef == null || !npcRef.isValid()) {
            return GiftEligibility.no(GiftEligibility.Reason.NO_CONTEXT);
        }
        if (isVisitor(store, npcRef)) {
            return GiftEligibility.no(GiftEligibility.Reason.VISITOR);
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return GiftEligibility.no(GiftEligibility.Reason.NO_PLAYER);
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(
            playerRef, InventoryComponent.Hotbar.getComponentType()
        );
        ItemStack inHand = hotbar != null ? hotbar.getActiveItem() : null;
        if (ItemStack.isEmpty(inHand)) {
            return GiftEligibility.no(GiftEligibility.Reason.EMPTY_HAND);
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (pu == null || nu == null) {
            return GiftEligibility.no(GiftEligibility.Reason.NO_CONTEXT);
        }
        long day = VillagerReputationService.currentGameEpochDay(store);
        long weekBlock = day / 7L;
        VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid());
        if (e.getLastGiftGameEpochDay() != null && e.getLastGiftGameEpochDay() == day) {
            return GiftEligibility.no(GiftEligibility.Reason.DAILY_LIMIT);
        }
        if (e.getGiftWeekBlockId() == null || e.getGiftWeekBlockId() != weekBlock) {
            return GiftEligibility.yes();
        }
        if (e.getGiftsThisWeekBlock() >= MAX_GIFTS_PER_WEEK_BLOCK) {
            return GiftEligibility.no(GiftEligibility.Reason.WEEKLY_LIMIT);
        }
        return GiftEligibility.yes();
    }

    /**
     * Text after the gift offer label when the choice is shown disabled (daily/weekly cap only).
     */
    @Nullable
    public static Message giftBlockMessageForDialogue(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable TownRecord town,
        @Nullable Ref<EntityStore> npcRef
    ) {
        GiftEligibility g = canGift(playerRef, store, town, npcRef);
        if (g.allowed || g.reason == null) {
            return null;
        }
        return switch (g.reason) {
            case DAILY_LIMIT -> Message.translation("server.aetherhaven.dialogue.gift.disabled.daily");
            case WEEKLY_LIMIT -> Message.translation("server.aetherhaven.dialogue.gift.disabled.weekly");
            default -> null;
        };
    }

    public record GiftApplyResult(
        boolean success,
        @Nullable GiftPreference preference,
        @Nullable String gotoNodeId,
        @Nullable GiftEligibility.Reason failReason
    ) {
        @Nonnull
        public static GiftApplyResult fail(@Nullable GiftEligibility.Reason r) {
            return new GiftApplyResult(false, null, null, r);
        }
    }

    @Nullable
    public static String reactionNodeIdFor(@Nonnull JsonObject action, @Nonnull GiftPreference preference) {
        String k =
            switch (preference) {
                case LOVE -> "reactionNodeLove";
                case LIKE -> "reactionNodeLike";
                case NEUTRAL -> "reactionNodeNeutral";
                case DISLIKE -> "reactionNodeDislike";
            };
        if (action.has(k) && action.get(k).isJsonPrimitive()) {
            String s = action.get(k).getAsString();
            if (s != null && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }

    @Nonnull
    public static GiftApplyResult applyGiftFromDialogue(
        @Nonnull JsonObject action,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> npcRef,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        if (npcRef == null || !npcRef.isValid()) {
            return GiftApplyResult.fail(GiftEligibility.Reason.NO_CONTEXT);
        }
        GiftEligibility el = canGift(playerRef, store, town, npcRef);
        if (!el.allowed) {
            return new GiftApplyResult(false, null, null, el.reason);
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return GiftApplyResult.fail(GiftEligibility.Reason.NO_PLAYER);
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
            return GiftApplyResult.fail(GiftEligibility.Reason.NO_CONTEXT);
        }
        String roleName = npc.getRoleName().trim();
        VillagerDefinitionCatalog cat = plugin.getVillagerDefinitionCatalog();
        VillagerDefinition def = cat.byNpcRoleId(roleName);
        InventoryComponent.Hotbar hotbar = store.getComponent(
            playerRef, InventoryComponent.Hotbar.getComponentType()
        );
        ItemStack inHand = hotbar != null ? hotbar.getActiveItem() : null;
        if (ItemStack.isEmpty(inHand)) {
            return GiftApplyResult.fail(GiftEligibility.Reason.EMPTY_HAND);
        }
        String itemId = inHand.getItemId();
        GiftPreference tier = VillagerGiftRules.classifyItem(itemId, def);
        int delta = VillagerGiftRules.reputationDelta(tier);
        String gotoNode = reactionNodeIdFor(action, tier);
        if (gotoNode == null) {
            return GiftApplyResult.fail(GiftEligibility.Reason.NO_CONTEXT);
        }
        if (!removeOneFromActiveHotbar(playerRef, store, inHand)) {
            return GiftApplyResult.fail(GiftEligibility.Reason.EMPTY_HAND);
        }
        UUIDComponent pu = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUIDComponent nu = store.getComponent(npcRef, UUIDComponent.getComponentType());
        if (pu == null || nu == null) {
            return GiftApplyResult.fail(GiftEligibility.Reason.NO_CONTEXT);
        }
        long day = VillagerReputationService.currentGameEpochDay(store);
        long weekBlock = day / 7L;
        VillagerReputationEntry e = VillagerReputationService.getOrCreateEntry(town, pu.getUuid(), nu.getUuid());
        if (e.getGiftWeekBlockId() == null || e.getGiftWeekBlockId() != weekBlock) {
            e.setGiftWeekBlockId(weekBlock);
            e.setGiftsThisWeekBlock(0);
        }
        e.setLastGiftGameEpochDay(day);
        e.setGiftsThisWeekBlock(e.getGiftsThisWeekBlock() + 1);
        com.hypixel.hytale.server.core.universe.world.World world = store.getExternalData().getWorld();
        int repBefore = e.getReputation();
        if (delta != 0) {
            VillagerReputationService.addReputationInternal(
                town,
                world,
                pu.getUuid(),
                nu.getUuid(),
                e,
                delta,
                tm
            );
        } else {
            tm.updateTown(town);
        }
        int repDelta = e.getReputation() - repBefore;
        playGiftEmotionParticles(npcRef, store, tier);
        notifyGiftReputationChange(playerRef, store, repDelta);
        town.appendVillagerGiftLog(roleName, new VillagerGiftLogEntry(itemId, tier, pu.getUuid().toString(), day));
        tm.updateTown(town);
        return new GiftApplyResult(true, tier, gotoNode, null);
    }

    private static void playGiftEmotionParticles(
        @Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store, @Nonnull GiftPreference tier
    ) {
        String baseName =
            switch (tier) {
                case LOVE -> GIFT_EMOTION_HEARTS;
                case LIKE -> GIFT_EMOTION_HEARTS_SUBTLE;
                case NEUTRAL -> GIFT_EMOTION_NEUTRAL;
                case DISLIKE -> GIFT_EMOTION_ANGRY;
            };
        String systemId = resolveEmotionParticleSystemId(baseName);
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        double hx = tc.getPosition().getX();
        double hy = tc.getPosition().getY();
        double hz = tc.getPosition().getZ();
        float eye = 1.6F;
        ModelComponent mc = store.getComponent(npcRef, ModelComponent.getComponentType());
        if (mc != null) {
            Model m = mc.getModel();
            if (m != null) {
                eye = m.getEyeHeight(npcRef, store);
            }
        }
        Vector3d pos = new Vector3d(hx, hy + eye, hz);
        World w = store.getExternalData().getWorld();
        w.execute(() -> ParticleUtil.spawnParticleEffect(systemId, pos, store));
    }

    @Nonnull
    private static String resolveEmotionParticleSystemId(@Nonnull String baseName) {
        String[] candidates = {
            baseName,
            "NPC/Emotions/" + baseName,
            "Server/Particles/NPC/Emotions/" + baseName,
            "Particles/NPC/Emotions/" + baseName
        };
        for (String id : candidates) {
            if (ParticleSystem.getAssetMap().getAsset(id) != null) {
                return id;
            }
        }
        return baseName;
    }

    private static void notifyGiftReputationChange(
        @Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store, int repDelta
    ) {
        if (repDelta == 0) {
            return;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        String amountStr = repDelta > 0 ? "+" + repDelta : String.valueOf(repDelta);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("server.aetherhaven.reputation.giftRepChange").param("amount", amountStr),
            repDelta > 0 ? NotificationStyle.Success : NotificationStyle.Warning
        );
    }

    private static boolean removeOneFromActiveHotbar(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull ItemStack inHand
    ) {
        InventoryComponent.Hotbar hotbar = store.getComponent(
            playerRef, InventoryComponent.Hotbar.getComponentType()
        );
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

    public static final class GiftEligibility {
        public enum Reason {
            NO_CONTEXT,
            NO_PLAYER,
            VISITOR,
            EMPTY_HAND,
            DAILY_LIMIT,
            WEEKLY_LIMIT
        }

        public final boolean allowed;
        @Nullable
        public final Reason reason;

        private GiftEligibility(boolean allowed, @Nullable Reason reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        private static GiftEligibility yes() {
            return new GiftEligibility(true, null);
        }

        private static GiftEligibility no(@Nullable Reason r) {
            return new GiftEligibility(false, r);
        }
    }
}
