package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Keeps a rescue contract's townsfolk at the camp, sitting bound until a player cuts the ropes. */
public final class RescueCaptiveSystem extends EntityTickingSystem<EntityStore> {
    /** The pose every townsfolk model shares. A captive sits on the ground while tied. */
    static final String TIED_POSE = "Sit";
    private static final float POSE_REFRESH_SECONDS = 2.0F;

    private final AetherhavenPlugin plugin;
    private float sinceRefresh;

    public RescueCaptiveSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            TownsfolkCharacterBinding.getComponentType(),
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType()
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
        RescueCampService.refreshCaptives(store.getExternalData().getWorld(), plugin);
        if (!RescueCampService.hasCaptives()) {
            return;
        }
        UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        RescueCampService.Captive captive = uuid == null ? null : RescueCampService.captive(uuid.getUuid());
        if (captive == null) {
            return;
        }
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (npc == null || transform == null || npc.getRole() == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (captive.anchor == null) {
            captive.anchor = new Vector3d(transform.getPosition());
        }
        NpcStandStill.maintainHold(ref, npc, captive.anchor, commandBuffer);
        captive.sincePose += dt;
        boolean wantSeated = captive.tied;
        if (wantSeated != captive.seated || (wantSeated && captive.sincePose >= POSE_REFRESH_SECONDS && !NpcFaceVisuals.isInInteractionDialogue(npc))) {
            NpcAnimationPlayback.play(ref, npc, AnimationSlot.Status, wantSeated ? TIED_POSE : null, commandBuffer);
            captive.seated = wantSeated;
            captive.sincePose = 0.0F;
        }
        // The ropes are part of the captive's model. Anything that rebuilds their look drops them, so this puts them
        // back, and takes them off once a player has cut them.
        var worn = chunk.getComponent(index, ModelComponent.getComponentType());
        if (worn != null && worn.getModel() != null && RescueCampService.wearsRopes(worn.getModel()) != captive.tied) {
            commandBuffer.putComponent(ref, ModelComponent.getComponentType(),
                new ModelComponent(RescueCampService.withRopes(worn.getModel(), captive.tied)));
        }
    }
}
