package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Use (F) while holding the packaging wand: turns the looked-at prop back into a held item. */
public final class AetherhavenPackagePropInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<AetherhavenPackagePropInteraction> CODEC =
        BuilderCodec.builder(AetherhavenPackagePropInteraction.class, AetherhavenPackagePropInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Packaging wand: turn the looked-at prop back into a held item.")
            .build();

    @Override
    protected void firstRun(
        @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (ref == null || commandBuffer == null) {
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_common.aetherhaven.common.pluginNotLoaded"));
            return;
        }
        World world = store.getExternalData().getWorld();
        // Look-ray is read-only; packageInstance itself defers Store writes onto the world queue.
        boolean packaged = PropPackageCommit.tryPackageLookedAtProp(world, plugin, ref, store);
        if (!packaged) {
            playerRef.sendMessage(Message.translation("aetherhaven_props.aetherhaven.prop.packaging.nothingFound"));
        }
    }
}
