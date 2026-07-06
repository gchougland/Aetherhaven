package com.hexvane.aetherhaven.npctelemetry;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class NpcDebugStickInteractions {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float TARGET_RANGE = 5.0F;
    private static final long COOLDOWN_MS = 1000L;
    private static final ConcurrentHashMap<UUID, Long> LAST_DUMP_MS = new ConcurrentHashMap<>();

    private NpcDebugStickInteractions() {}

    public static boolean isDebugStickItem(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.NPC_DEBUG_STICK_ITEM_ID.equals(stack.getItemId());
    }

    public static boolean hasWorldEditorPermission(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        return pr != null && pr.hasPermission(AetherhavenConstants.PERMISSION_WORLD_EDITOR, false);
    }

    public static void handleSmack(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull ItemStack itemInHand
    ) {
        Store<EntityStore> store = commandBuffer.getStore();
        PlayerRef dumper = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (dumper == null) {
            return;
        }
        if (!hasWorldEditorPermission(playerRef, store)) {
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.noPermission"));
            return;
        }

        UUID dumperUuid = dumper.getUuid();
        if (dumperUuid != null) {
            long now = System.currentTimeMillis();
            Long last = LAST_DUMP_MS.get(dumperUuid);
            if (last != null && now - last < COOLDOWN_MS) {
                dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.cooldown"));
                return;
            }
            LAST_DUMP_MS.put(dumperUuid, now);
        }

        @Nullable
        Ref<EntityStore> targetRef = resolveTargetRef(playerRef, store, context);
        if (targetRef == null || !targetRef.isValid()) {
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.noTarget"));
            return;
        }
        if (!InteractionValidation.canPlayerInteractWithEntity(playerRef, store, itemInHand, targetRef)) {
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.noTarget"));
            return;
        }

        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) {
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.notNpc"));
            return;
        }

        playSmackEffects(store, targetRef);
        playNpcHurtReaction(targetRef, npc, store, commandBuffer);

        UUIDComponent uc = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (uc == null) {
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.notNpc"));
            return;
        }

        World world = store.getExternalData().getWorld();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.writeFailed"));
            return;
        }

        String handleHint = null;
        AetherhavenVillagerHandle handle = store.getComponent(targetRef, AetherhavenVillagerHandle.getComponentType());
        if (handle != null && !handle.getHandle().isBlank()) {
            handleHint = handle.getHandle();
        }

        Map<String, Object> report = NpcTelemetryCollector.collect(plugin, world, store, targetRef, dumper);
        try {
            Path file = NpcTelemetryWriter.resolvePath(plugin, world, uc.getUuid(), handleHint);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) report.computeIfAbsent("meta", k -> new java.util.LinkedHashMap<>());
            meta.put("reportFilePath", file.toString());
            NpcTelemetryWriter.write(file, report);
            dumper.sendMessage(
                Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.success").param("path", file.toString())
            );
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to write NPC telemetry for %s", uc.getUuid());
            dumper.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.debug.npc_stick.writeFailed"));
        }
    }

    @Nullable
    private static Ref<EntityStore> resolveTargetRef(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull InteractionContext context
    ) {
        @Nullable
        Ref<EntityStore> targeted = context.getTargetEntity();
        if (targeted != null && targeted.isValid()) {
            return targeted;
        }
        @Nullable
        InteractionSyncData sync = context.getClientState();
        if (sync != null && sync.entityId > 0) {
            @Nullable
            Ref<EntityStore> fromSync = store.getExternalData().getRefFromNetworkId(sync.entityId);
            if (fromSync != null && fromSync.isValid()) {
                return fromSync;
            }
        }
        return TargetUtil.getTargetEntity(playerRef, TARGET_RANGE, store);
    }

    private static void playNpcHurtReaction(
        @Nonnull Ref<EntityStore> targetRef,
        @Nonnull NPCEntity npc,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        MovementStatesComponent movementStatesComponent =
            store.getComponent(targetRef, MovementStatesComponent.getComponentType());
        if (movementStatesComponent == null) {
            return;
        }
        ModelComponent modelComponent = store.getComponent(targetRef, ModelComponent.getComponentType());
        Model model = modelComponent != null ? modelComponent.getModel() : null;
        if (model == null) {
            return;
        }
        DamageCause cause = DamageCause.getAssetMap().getAsset("Bludgeoning");
        if (cause == null) {
            cause = DamageCause.getAssetMap().getAsset("Physical");
        }
        if (cause == null) {
            return;
        }
        String[] animationIds =
            Entity.DefaultAnimations.getHurtAnimationIds(movementStatesComponent.getMovementStates(), cause);
        String selectedAnimationId = model.getFirstBoundAnimationId(animationIds);
        if (selectedAnimationId == null) {
            return;
        }
        NpcAnimationPlayback.play(targetRef, npc, AnimationSlot.Status, selectedAnimationId, commandBuffer);
    }

    private static void playSmackEffects(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> targetRef) {
        TransformComponent tc = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = new Vector3d(tc.getPosition());
        pos.y += 1.15;
        ParticleUtil.spawnParticleEffect(AetherhavenConstants.NPC_DEBUG_STICK_IMPACT_PARTICLE, pos, store);
        int impactSfx = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.NPC_DEBUG_STICK_IMPACT_SOUND);
        if (impactSfx != 0) {
            SoundUtil.playSoundEvent3d(null, impactSfx, pos, store);
        }
    }
}
