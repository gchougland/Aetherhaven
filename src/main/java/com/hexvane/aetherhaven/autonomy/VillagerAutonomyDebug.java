package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.RoleDebugFlags;
import java.util.EnumSet;
import javax.annotation.Nonnull;

/** Autonomy debug overlay: {@link VillagerAutonomyDebugTag} on the entity is the source of truth. */
public final class VillagerAutonomyDebug {
    private VillagerAutonomyDebug() {}

    /** True when this entity has the debug tag (command turned overlay on). */
    public static boolean isEnabled(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return store.getComponent(ref, VillagerAutonomyDebugTag.getComponentType()) != null;
    }

    /** True when town villager and debug tag present. */
    public static boolean isTownVillagerDebug(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        if (store.getComponent(ref, TownVillagerBinding.getComponentType()) == null) {
            return false;
        }
        return isEnabled(store, ref);
    }

    /**
     * Same pattern as {@code NPCDebugCommand.safeSetRoleDebugFlags}: remove {@link Nameplate} first, then update role
     * debug flags and {@link com.hypixel.hytale.server.npc.role.support.DebugSupport}. {@link com.hypixel.hytale.server.npc.role.RoleDebugDisplay}
     * only writes nameplate text when the assembled debug line is non-empty; otherwise the previous text would stay on the
     * component forever.
     */
    public static void clearAutonomyDebugForNpc(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull NPCEntity npc
    ) {
        store.tryRemoveComponent(ref, Nameplate.getComponentType());
        stripAutonomyDebugFlagsAndBuffers(npc);
    }

    /** Tick path: remove nameplate via command buffer, then strip flags and custom/path buffers. */
    public static void clearAutonomyDebugForNpc(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull NPCEntity npc
    ) {
        commandBuffer.tryRemoveComponent(ref, Nameplate.getComponentType());
        stripAutonomyDebugFlagsAndBuffers(npc);
    }

    private static void stripAutonomyDebugFlagsAndBuffers(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return;
        }
        EnumSet<RoleDebugFlags> flags = npc.getRoleDebugFlags().clone();
        boolean changed = false;
        if (flags.remove(RoleDebugFlags.DisplayCustom)) {
            changed = true;
        }
        if (flags.remove(RoleDebugFlags.VisPath)) {
            changed = true;
        }
        if (changed) {
            npc.setRoleDebugFlags(flags);
        }
        npc.getRole().getDebugSupport().setDisplayCustomString(null);
        npc.getRole().getDebugSupport().clearPathVisualization();
    }

    /**
     * Ensures vanilla role debug flags needed for custom string + path vis are set. Idempotent; call each tick while the
     * debug tag is present.
     */
    public static void ensureAutonomyDebugRoleFlags(@Nonnull NPCEntity npc) {
        if (npc.getRole() == null) {
            return;
        }
        EnumSet<RoleDebugFlags> flags = npc.getRoleDebugFlags().clone();
        boolean changed = false;
        if (flags.add(RoleDebugFlags.DisplayCustom)) {
            changed = true;
        }
        if (flags.add(RoleDebugFlags.VisPath)) {
            changed = true;
        }
        if (changed) {
            npc.setRoleDebugFlags(flags);
        }
    }
}
