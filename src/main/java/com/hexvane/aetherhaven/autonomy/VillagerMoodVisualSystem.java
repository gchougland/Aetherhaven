package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.npc.NpcFaceVisualState;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import javax.annotation.Nonnull;

/** Applies needs-based face mood to town NPCs while they are not in dialogue. */
public final class VillagerMoodVisualSystem extends EntityTickingSystem<EntityStore> {
    /** Re-send grin/frown so work Action/Emote overlays cannot leave the face stuck neutral. */
    private static final long MOOD_FACE_KEEPALIVE_MS = 4500L;

    private final AetherhavenPlugin plugin;
    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();

    public VillagerMoodVisualSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TownVillagerBinding.getComponentType(),
            VillagerNeeds.getComponentType(),
            NPCEntity.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        VillagerNeeds needs = archetypeChunk.getComponent(index, VillagerNeeds.getComponentType());
        if (npc == null || needs == null || npc.getRole() == null) {
            return;
        }
        if (!NpcFaceVisuals.supportsFaceExpressions(ref, store)) {
            return;
        }
        if (NpcFaceVisuals.isInInteractionDialogue(npc)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        NpcFaceVisualState faceState = archetypeChunk.getComponent(index, NpcFaceVisualState.getComponentType());
        if (faceState == null) {
            faceState = NpcFaceVisualState.fresh();
            commandBuffer.putComponent(ref, NpcFaceVisualState.getComponentType(), faceState);
        }
        if (faceState.isTalkBurstActive(nowMs)) {
            return;
        }
        ActiveAnimationComponent active = archetypeChunk.getComponent(index, ActiveAnimationComponent.getComponentType());
        if (active != null) {
            String faceAnim = active.getActiveAnimations()[AnimationSlot.Face.ordinal()];
            if (NpcFaceVisuals.isTalkAnimation(faceAnim)) {
                return;
            }
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        NpcFaceVisuals.MoodConfig moodConfig = new NpcFaceVisuals.MoodConfig(
            cfg.getDialogueTalkDurationSeconds(),
            cfg.getNeedsMoodLowThreshold(),
            cfg.getNeedsMoodHighThreshold()
        );
        NpcFaceVisuals.NeedsMoodTier tier = NpcFaceVisuals.resolveMoodTier(
            needs,
            moodConfig.lowThreshold(),
            moodConfig.highThreshold()
        );
        TownVillagerBinding binding = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
        boolean townsfolk = binding != null && TownVillagerBinding.KIND_TOWNSFOLK.equals(binding.getKind());
        String desiredFace = NpcFaceVisuals.moodFaceAnimationId(tier, townsfolk);
        String currentFace = active != null ? active.getActiveAnimations()[AnimationSlot.Face.ordinal()] : null;
        boolean needsKeepalive =
            faceState.getLastMoodApplyEpochMs() <= 0L
                || nowMs - faceState.getLastMoodApplyEpochMs() >= MOOD_FACE_KEEPALIVE_MS;
        if (tier.ordinal() == faceState.getLastMoodTier()
            && java.util.Objects.equals(desiredFace, currentFace)
            && !needsKeepalive) {
            return;
        }
        NpcFaceVisuals.applyMoodFace(ref, commandBuffer, store, moodConfig);
    }
}
