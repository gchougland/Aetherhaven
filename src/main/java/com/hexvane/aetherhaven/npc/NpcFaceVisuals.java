package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Face-slot talk bursts and needs-based mood expressions for humanoid town NPCs. */
public final class NpcFaceVisuals {
    public static final String FACE_FROWN = "Frown";
    public static final String FACE_GRIN = "Grin";
    private static final String PLAYER_BLOCKYMODEL = "Characters/Player.blockymodel";
    private static final String[] TALK_ANIMATIONS = { "Talk", "Talk2", "Talk3", "Talk4", "Talk5" };

    public enum NeedsMoodTier {
        LOW,
        NEUTRAL,
        HIGH
    }

    private NpcFaceVisuals() {}

    @Nonnull
    public static NeedsMoodTier resolveMoodTier(@Nullable VillagerNeeds needs, float lowThreshold, float highThreshold) {
        if (needs == null) {
            return NeedsMoodTier.NEUTRAL;
        }
        float minNeed = Math.min(needs.getHunger(), Math.min(needs.getEnergy(), needs.getFun()));
        if (minNeed < lowThreshold) {
            return NeedsMoodTier.LOW;
        }
        if (minNeed >= highThreshold) {
            return NeedsMoodTier.HIGH;
        }
        return NeedsMoodTier.NEUTRAL;
    }

    @Nullable
    public static String moodFaceAnimationId(@Nonnull NeedsMoodTier tier, boolean townsfolk) {
        return switch (tier) {
            case LOW -> FACE_FROWN;
            case HIGH -> FACE_GRIN;
            case NEUTRAL -> townsfolk ? FACE_GRIN : null;
        };
    }

    public static boolean isTownsfolk(@Nonnull ComponentAccessor<EntityStore> componentAccessor, @Nonnull Ref<EntityStore> npcRef) {
        TownVillagerBinding binding = componentAccessor.getComponent(npcRef, TownVillagerBinding.getComponentType());
        return binding != null && TownVillagerBinding.KIND_TOWNSFOLK.equals(binding.getKind());
    }

    /** Face-slot talk and mood expressions only work on the detached player face rig. */
    public static boolean supportsFaceExpressions(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor
    ) {
        if (!npcRef.isValid()) {
            return false;
        }
        String assetId = resolveModelAssetId(npcRef, componentAccessor);
        if (assetId == null || assetId.isBlank()) {
            return false;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(assetId.trim());
        if (asset == null) {
            return false;
        }
        String blockyModel = asset.getModel();
        return blockyModel != null && PLAYER_BLOCKYMODEL.equals(blockyModel);
    }

    public static boolean isTalkAnimation(@Nullable String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return false;
        }
        for (String talk : TALK_ANIMATIONS) {
            if (talk.equals(animationId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInInteractionDialogue(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return false;
        }
        StateSupport stateSupport = npc.getRole().getStateSupport();
        int interactionState = stateSupport.getStateHelper().getStateIndex("$Interaction");
        return interactionState >= 0 && stateSupport.inState(interactionState);
    }

    public static void applyMoodFace(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        applyMoodFace(npcRef, store, readConfig());
    }

    public static void applyMoodFace(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store
    ) {
        applyMoodFace(npcRef, commandBuffer, store, readConfig());
    }

    public static void applyMoodFace(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull MoodConfig config
    ) {
        if (!npcRef.isValid()) {
            return;
        }
        if (!supportsFaceExpressions(npcRef, store)) {
            return;
        }
        VillagerNeeds needs = store.getComponent(npcRef, VillagerNeeds.getComponentType());
        NeedsMoodTier tier = resolveMoodTier(needs, config.lowThreshold(), config.highThreshold());
        boolean townsfolk = isTownsfolk(store, npcRef);
        playFaceAnimation(npcRef, moodFaceAnimationId(tier, townsfolk), store);
        NpcFaceVisualState state = store.getComponent(npcRef, NpcFaceVisualState.getComponentType());
        if (state != null) {
            state.setLastMoodTier(tier.ordinal());
            state.setLastMoodApplyEpochMs(System.currentTimeMillis());
        }
    }

    public static void applyMoodFace(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull MoodConfig config
    ) {
        if (!npcRef.isValid()) {
            return;
        }
        if (!supportsFaceExpressions(npcRef, store)) {
            return;
        }
        VillagerNeeds needs = store.getComponent(npcRef, VillagerNeeds.getComponentType());
        NeedsMoodTier tier = resolveMoodTier(needs, config.lowThreshold(), config.highThreshold());
        boolean townsfolk = isTownsfolk(store, npcRef);
        playFaceAnimation(npcRef, moodFaceAnimationId(tier, townsfolk), commandBuffer);
        NpcFaceVisualState state = store.getComponent(npcRef, NpcFaceVisualState.getComponentType());
        if (state == null) {
            state = NpcFaceVisualState.fresh();
        }
        state.setLastMoodTier(tier.ordinal());
        state.setLastMoodApplyEpochMs(System.currentTimeMillis());
        commandBuffer.putComponent(npcRef, NpcFaceVisualState.getComponentType(), state);
    }

    public static void playTalkBurst(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        playTalkBurst(npcRef, null, store, readConfig());
    }

    public static void playTalkBurst(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        playTalkBurst(npcRef, playerEntityRef, store, readConfig());
    }

    public static void playTalkBurst(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store, @Nonnull MoodConfig config) {
        playTalkBurst(npcRef, null, store, config);
    }

    public static void playTalkBurst(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull MoodConfig config
    ) {
        if (!npcRef.isValid()) {
            return;
        }
        if (!supportsFaceExpressions(npcRef, store)) {
            return;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || store.getComponent(npcRef, NetworkId.getComponentType()) == null) {
            return;
        }
        String talkId = TALK_ANIMATIONS[ThreadLocalRandom.current().nextInt(TALK_ANIMATIONS.length)];
        playFaceAnimation(npcRef, talkId, store);
        long untilMs = System.currentTimeMillis() + (long) (config.talkDurationSeconds() * 1000f);
        NpcFaceVisualState faceState = store.getComponent(npcRef, NpcFaceVisualState.getComponentType());
        if (faceState == null) {
            faceState = NpcFaceVisualState.fresh();
        }
        faceState.setTalkUntilMs(untilMs);
        store.putComponent(npcRef, NpcFaceVisualState.getComponentType(), faceState);
        World world = store.getExternalData().getWorld();
        scheduleTalkRestore(npcRef, world, config, config.talkDurationSeconds());
        // Speech blips are started from DialoguePage with the resolved body text (letter-mapped).
    }

    public static void clearDialogueFace(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (!npcRef.isValid()) {
            return;
        }
        if (!supportsFaceExpressions(npcRef, store)) {
            return;
        }
        NpcFaceVisualState state = store.getComponent(npcRef, NpcFaceVisualState.getComponentType());
        if (state != null) {
            state.setTalkUntilMs(0L);
            store.putComponent(npcRef, NpcFaceVisualState.getComponentType(), state);
        }
        applyMoodFace(npcRef, store);
    }

    public static void onDialogueOpened(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        onDialogueOpened(npcRef, null, store);
    }

    public static void onDialogueOpened(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store
    ) {
        MoodConfig config = readConfig();
        applyMoodFace(npcRef, store, config);
        playTalkBurst(npcRef, playerEntityRef, store, config);
    }

    @Nonnull
    private static MoodConfig readConfig() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return MoodConfig.defaults();
        }
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        return new MoodConfig(
            cfg.getDialogueTalkDurationSeconds(),
            cfg.getNeedsMoodLowThreshold(),
            cfg.getNeedsMoodHighThreshold()
        );
    }

    private static void scheduleTalkRestore(@Nonnull Ref<EntityStore> npcRef, @Nonnull World world, @Nonnull MoodConfig config, float delaySeconds) {
        long delayMs = Math.max(1L, (long) (delaySeconds * 1000f));
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
            () -> world.execute(
                () -> {
                    if (!npcRef.isValid()) {
                        return;
                    }
                    Store<EntityStore> store = npcRef.getStore();
                    if (store == null) {
                        return;
                    }
                    NpcFaceVisualState state = store.getComponent(npcRef, NpcFaceVisualState.getComponentType());
                    if (state != null && state.isTalkBurstActive(System.currentTimeMillis())) {
                        return;
                    }
                    applyMoodFace(npcRef, store, config);
                }
            ),
            delayMs,
            TimeUnit.MILLISECONDS
        );
    }

    @Nullable
    private static String resolveModelAssetId(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor
    ) {
        ModelComponent modelComponent = componentAccessor.getComponent(npcRef, ModelComponent.getComponentType());
        if (modelComponent != null) {
            Model model = modelComponent.getModel();
            if (model != null) {
                String id = model.getModelAssetId();
                if (id != null && !id.isBlank()) {
                    return id.trim();
                }
            }
        }
        PersistentModel persistent = componentAccessor.getComponent(npcRef, PersistentModel.getComponentType());
        if (persistent != null) {
            Model.ModelReference reference = persistent.getModelReference();
            if (reference != null) {
                String id = reference.getModelAssetId();
                if (id != null && !id.isBlank()) {
                    return id.trim();
                }
            }
        }
        return null;
    }

    private static void playFaceAnimation(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable String animationId,
        @Nonnull Store<EntityStore> store
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || store.getComponent(npcRef, NetworkId.getComponentType()) == null) {
            return;
        }
        if (animationId == null) {
            ActiveAnimationComponent active = store.getComponent(npcRef, ActiveAnimationComponent.getComponentType());
            if (active != null && active.getActiveAnimations()[AnimationSlot.Face.ordinal()] != null) {
                npc.playAnimation(npcRef, AnimationSlot.Face, null, store);
            } else {
                AnimationUtils.stopAnimation(npcRef, AnimationSlot.Face, store);
            }
            return;
        }
        npc.playAnimation(npcRef, AnimationSlot.Face, animationId, store);
    }

    private static void playFaceAnimation(
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable String animationId,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        NPCEntity npc = commandBuffer.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null || commandBuffer.getComponent(npcRef, NetworkId.getComponentType()) == null) {
            return;
        }
        if (animationId == null) {
            ActiveAnimationComponent active = commandBuffer.getComponent(npcRef, ActiveAnimationComponent.getComponentType());
            if (active != null && active.getActiveAnimations()[AnimationSlot.Face.ordinal()] != null) {
                NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Face, null, commandBuffer);
            } else {
                NpcAnimationPlayback.stop(npcRef, AnimationSlot.Face, commandBuffer);
            }
            return;
        }
        NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Face, animationId, commandBuffer);
    }

    public record MoodConfig(float talkDurationSeconds, float lowThreshold, float highThreshold) {
        @Nonnull
        public static MoodConfig defaults() {
            return new MoodConfig(3f, 40f, 70f);
        }
    }
}
