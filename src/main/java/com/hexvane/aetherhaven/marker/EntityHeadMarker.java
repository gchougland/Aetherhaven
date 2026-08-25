package com.hexvane.aetherhaven.marker;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Floats a particle above an entity for a chosen audience, pinned to the model so it rides along as the entity moves.
 * Works the same for players and villagers.
 *
 * <p>These particle systems are short lived by design, so a caller that wants a marker to stay put has to re-send it
 * before the system's lifespan runs out.
 */
public final class EntityHeadMarker {
    private EntityHeadMarker() {}

    /** Removes every marker this mod put on the entity, for the given audience. */
    public static boolean clear(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        return send(entityRef, audience, accessor, new com.hypixel.hytale.protocol.ModelParticle[0]);
    }

    /**
     * @param nodeName names the emitter so a later send of the same name replaces it rather than stacking
     * @param heightBlocks how far above the entity's origin the marker sits
     */
    public static boolean spawn(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull String particleSystemId,
        @Nonnull String nodeName,
        float heightBlocks,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        ModelParticle marker = new ModelParticle(
            particleSystemId,
            EntityPart.Self,
            nodeName,
            null,
            1.0f,
            new Vector3f(0.0f, heightBlocks, 0.0f),
            null,
            false
        );
        return send(
            entityRef,
            audience,
            accessor,
            new com.hypixel.hytale.protocol.ModelParticle[] { marker.toPacket() }
        );
    }

    private static boolean send(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull List<Ref<EntityStore>> audience,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull com.hypixel.hytale.protocol.ModelParticle[] particles
    ) {
        NetworkId networkId = accessor.getComponent(entityRef, NetworkId.getComponentType());
        if (networkId == null) {
            return false;
        }
        SpawnModelParticles packet = new SpawnModelParticles(networkId.getId(), particles);
        boolean sent = false;
        for (Ref<EntityStore> viewerRef : audience) {
            if (!viewerRef.isValid()) {
                continue;
            }
            PlayerRef pr = accessor.getComponent(viewerRef, PlayerRef.getComponentType());
            if (pr != null) {
                pr.getPacketHandler().write(packet);
                sent = true;
            }
        }
        return sent;
    }
}
