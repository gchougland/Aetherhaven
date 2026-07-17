package com.hexvane.aetherhaven.monument;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * After chunk load, {@link com.hypixel.hytale.server.core.modules.entity.system.ModelSystems.SetRenderedModel} rebuilds
 * {@link ModelComponent} from {@link PersistentModel} using only the {@code Player} asset (no cosmetics). This system
 * runs later and reapplies the full stone statue from persisted {@link FounderMonumentStatueSkin}.
 */
public final class FounderMonumentStatueRestoreSystem extends HolderSystem<EntityStore> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final ComponentType<EntityStore, FounderMonumentStatueSkin> skinType = FounderMonumentStatueSkin.getComponentType();
    private final ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(this.skinType, this.npcType);
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, ModelSystems.SetRenderedModel.class),
            new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class)
        );
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
        if (reason != AddReason.SPAWN && reason != AddReason.LOAD) {
            return;
        }
        UUIDComponent uuidComponent = holder.getComponent(UUIDComponent.getComponentType());
        World world = store.getExternalData().getWorld();
        if (uuidComponent == null || world == null) {
            return;
        }

        // Old saves persist "Player". Replace that intermediate appearance before model packets are queued.
        if (reason == AddReason.LOAD) {
            Model fallback = FounderMonumentSpawnService.buildFallbackModel();
            if (fallback != null) {
                holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(fallback));
                holder.putComponent(PersistentModel.getComponentType(), FounderMonumentSpawnService.toPersistentModel(fallback));
            }
        }

        UUID entityId = uuidComponent.getUuid();
        world.execute(() -> restoreDeferred(entityId, store));
    }

    private void restoreDeferred(@Nonnull UUID entityId, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityId);
        if (ref == null || !ref.isValid()) {
            return;
        }
        FounderMonumentStatueSkin stored = store.getComponent(ref, this.skinType);
        PlayerSkin skin = stored != null ? stored.tryToProtocol() : null;
        if (skin == null) {
            LOGGER.atWarning().log("Founder monument %s: persisted player skin is missing or malformed", entityId);
            applyFallback(ref, store);
            return;
        }
        Model monument = FounderMonumentSpawnService.buildMonumentModel(skin);
        if (monument == null) {
            LOGGER.atWarning().log("Founder monument %s: could not rebuild player cosmetics; using stone fallback", entityId);
            applyFallback(ref, store);
            return;
        }
        applyModel(ref, store, monument);
    }

    private static void applyFallback(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Model fallback = FounderMonumentSpawnService.buildFallbackModel();
        if (fallback != null) {
            applyModel(ref, store, fallback);
        }
    }

    private static void applyModel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Model model) {
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
        store.putComponent(ref, PersistentModel.getComponentType(), FounderMonumentSpawnService.toPersistentModel(model));
        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {}
}
