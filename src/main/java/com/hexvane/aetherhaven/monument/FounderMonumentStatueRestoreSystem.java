package com.hexvane.aetherhaven.monument;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * After chunk load, {@link com.hypixel.hytale.server.core.modules.entity.system.ModelSystems.SetRenderedModel} rebuilds
 * {@link ModelComponent} from {@link PersistentModel} using only the {@code Player} asset (no cosmetics). This system
 * runs later and reapplies the full stone statue from persisted {@link FounderMonumentStatueSkin}.
 */
public final class FounderMonumentStatueRestoreSystem extends HolderSystem<EntityStore> {
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
        return Set.of(new SystemDependency<>(Order.AFTER, ModelSystems.SetRenderedModel.class));
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {
        FounderMonumentStatueSkin stored = holder.getComponent(this.skinType);
        if (stored == null) {
            return;
        }
        PlayerSkin skin = stored.toProtocol();
        Model monument = FounderMonumentSpawnService.buildMonumentModel(skin);
        if (monument == null) {
            return;
        }
        holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(monument));
        float persistScale = FounderMonumentSpawnService.safePersistScale(monument.getScale());
        holder.putComponent(
            PersistentModel.getComponentType(),
            new PersistentModel(new Model.ModelReference("Player", persistScale, monument.getRandomAttachmentIds(), true))
        );
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {}
}
