package com.hexvane.aetherhaven.festival.market;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.festival.FestivalDanceSystem;
import com.hexvane.aetherhaven.festival.FestivalSpotService;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.shopspot.ShopSpotBrowseVisuals;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Walks Elder Lyren along the four market stands and plays a ponder emote at each. Session fields only; animations
 * go through the command buffer.
 */
public final class MarketJudgeDirectorSystem extends EntityTickingSystem<EntityStore> {
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(
            new SystemDependency<>(Order.AFTER, VillagerAutonomySystem.class),
            new SystemDependency<>(Order.AFTER, FestivalDanceSystem.class)
        );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            NPCEntity.getComponentType(),
            TownVillagerBinding.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        TownVillagerBinding binding = chunk.getComponent(index, TownVillagerBinding.getComponentType());
        if (binding == null || !TownVillagerBinding.KIND_ELDER.equalsIgnoreCase(binding.getKind())) {
            return;
        }
        UUID townId = binding.getTownId();
        if (townId == null) {
            return;
        }
        MarketSession session = MarketSessionIndex.get(townId);
        if (session == null || !session.isJudging()) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null || !MarketIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
            return;
        }
        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (tc == null || ref == null || !ref.isValid()) {
            return;
        }
        PoiEntry stand =
            FestivalSpotService.findSpotForKind(world, plugin, town, MarketIds.standKind(session.getCurrentStandIndex()));
        if (stand == null) {
            if (session.isPondering()) {
                ShopSpotBrowseVisuals.endPonder(ref, store, commandBuffer);
            }
            session.advanceAfterPonder();
            if (session.isJudged()) {
                MarketLeaderboard.recordTown(world, plugin, town, session.getScore());
            }
            return;
        }
        if (!isStandingAt(tc, stand)) {
            return;
        }
        long now = System.currentTimeMillis();
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (!session.isPondering()) {
            session.beginPonder(now + MarketIds.PONDER_MS);
            if (npc != null) {
                NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
            }
            playPonder(ref, commandBuffer, npc);
            return;
        }
        if (!session.ponderFinished(now)) {
            holdPonder(ref, store, commandBuffer, npc);
            return;
        }
        ShopSpotBrowseVisuals.endPonder(ref, store, commandBuffer);
        session.advanceAfterPonder();
        if (session.isJudged()) {
            MarketLeaderboard.recordTown(world, plugin, town, session.getScore());
        }
    }

    @Nullable
    public static PoiEntry redirectSpot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownVillagerBinding binding,
        @Nullable UUID villagerUuid
    ) {
        if (!MarketIds.FESTIVAL_ID.equals(town.getActiveFestivalId())) {
            return null;
        }
        if (!TownVillagerBinding.KIND_ELDER.equalsIgnoreCase(binding.getKind())) {
            return null;
        }
        MarketSession session = MarketSessionIndex.get(town.getTownId());
        if (session == null || !session.isJudging()) {
            return null;
        }
        return FestivalSpotService.findSpotForKind(
            world, plugin, town, MarketIds.standKind(session.getCurrentStandIndex())
        );
    }

    private static void holdPonder(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc
    ) {
        NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
        if (isPlayingEmote(store, ref, ShopSpotBrowseVisuals.PONDER_EMOTE_ID)) {
            return;
        }
        playPonder(ref, commandBuffer, npc);
    }

    private static void playPonder(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc
    ) {
        if (npc != null) {
            NpcAnimationPlayback.play(
                ref, npc, AnimationSlot.Emote, ShopSpotBrowseVisuals.PONDER_EMOTE_ID, commandBuffer
            );
            commandBuffer.putComponent(ref, NPCEntity.getComponentType(), npc);
            return;
        }
        NpcAnimationPlayback.play(ref, AnimationSlot.Emote, ShopSpotBrowseVisuals.PONDER_EMOTE_ID, commandBuffer);
    }

    private static boolean isPlayingEmote(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull String emote
    ) {
        ActiveAnimationComponent active = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (active == null) {
            return false;
        }
        String current = active.getActiveAnimations()[AnimationSlot.Emote.ordinal()];
        return emote.equals(current);
    }

    private static boolean isStandingAt(@Nonnull TransformComponent tc, @Nonnull PoiEntry spot) {
        double tx = spot.getInteractionTargetX() != null ? spot.getInteractionTargetX() : spot.getX() + 0.5;
        double tz = spot.getInteractionTargetZ() != null ? spot.getInteractionTargetZ() : spot.getZ() + 0.5;
        double dx = tc.getPosition().x - tx;
        double dz = tc.getPosition().z - tz;
        return dx * dx + dz * dz <= MarketIds.STALL_ARRIVED_DIST_SQ;
    }
}
