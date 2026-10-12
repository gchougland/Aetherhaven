package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tells this mod's own entities from everybody else's. Code that changes or removes things across the whole world
 * asks here first, so a pet, a mount or a monster from the base game or another mod is never touched.
 *
 * <p>An entity is this mod's own when it carries a component this mod registered (a town villager's binding, a
 * townsfolk character, a placed world character, a festival actor, a marker and so on). A creature is also the mod's
 * own when its role is one of the mod's own role files, which all start with {@code Aetherhaven_}. Custom characters
 * from add-on packs carry a binding and so count too.
 */
public final class OwnCreatures {
    /** Every role file this mod ships starts with this. No base game role does. */
    public static final String ROLE_PREFIX = "Aetherhaven_";
    /** Every component this mod registers is a class under this package. */
    static final String MOD_PACKAGE = "com.hexvane.aetherhaven.";

    private OwnCreatures() {}

    /** The rule for a creature, on plain values so it can be checked without a server. */
    public static boolean isOwn(@Nullable String roleName, boolean carriesModComponent) {
        return carriesModComponent || (roleName != null && roleName.startsWith(ROLE_PREFIX));
    }

    /** True when {@code ref} is a creature this mod placed. False for anything that is not a creature. */
    public static boolean isOwnNpc(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        if (!ref.isValid()) {
            return false;
        }
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return false;
        }
        return isOwn(npc.getRoleName(), carriesModComponent(accessor.getArchetype(ref)));
    }

    /** True when the entity carries at least one component this mod registered. */
    public static boolean carriesModComponent(@Nullable Archetype<EntityStore> archetype) {
        if (archetype == null) {
            return false;
        }
        for (int i = archetype.getMinIndex(); i < archetype.length(); i++) {
            ComponentType<EntityStore, ?> type = archetype.get(i);
            if (type != null && isModComponent(type.getTypeClass())) {
                return true;
            }
        }
        return false;
    }

    static boolean isModComponent(@Nullable Class<?> componentClass) {
        return componentClass != null && componentClass.getName().startsWith(MOD_PACKAGE);
    }
}
