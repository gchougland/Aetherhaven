package com.hexvane.aetherhaven.alchemy;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.IAttitudeProvider;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.BlackboardSystems;
import java.util.Set;

/**
 * Bug Repellent. Creatures listed in the Aetherhaven_Alchemy_Repelled NPC group ignore anyone carrying the
 * repellent effect, through the same attitude provider hook the native reputation system uses. Native roles pick
 * their prey by attitude, so no role asset is replaced.
 */
public final class AlchemyRepellentSystem extends StoreSystem<EntityStore> {
    public static final String EFFECT = "Aetherhaven_Alchemy_Repellent1";
    public static final String GROUP = "Aetherhaven_Alchemy_Repelled";
    /**
     * Below a creature's own attitude override (0), so one already fighting that has marked its attacker hostile
     * keeps fighting. Above spawn lineage and reputation (100) and the species attitude table (200).
     */
    public static final int PRIORITY = IAttitudeProvider.OVERRIDE_PRIORITY + 50;
    /** The attitude view only exists once the native blackboard has been set up for the world. */
    private final Set<Dependency<EntityStore>> dependencies = Set.of(new SystemDependency<>(Order.AFTER, BlackboardSystems.InitSystem.class));

    @Override public Set<Dependency<EntityStore>> getDependencies() { return dependencies; }
    @Override public void onSystemAddedToStore(Store<EntityStore> store) {
        store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class, 0)
            .registerProvider(PRIORITY, (ref, role, target, accessor) -> repelled(role, target, accessor) ? Attitude.IGNORE : null);
    }
    @Override public void onSystemRemovedFromStore(Store<EntityStore> store) {}

    private static boolean repelled(int role, Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor) {
        // The group test comes first: it is one set lookup and nearly every creature fails it.
        if (!listed(role, NPCGroup.getAssetMap().getIndex(GROUP))) return false;
        var controller = accessor.getComponent(target, EffectControllerComponent.getComponentType());
        return controller != null && controller.hasEffect(EntityEffect.getAssetMap().getIndex(EFFECT));
    }
    private static boolean listed(int role, int group) {
        if (role < 0 || group == AssetMapWithIndexes.NOT_FOUND) return false;
        var members = TagSetPlugin.get(NPCGroup.class).getSet(group);
        return members != null && members.contains(role);
    }
    /** Only a blow that lands on a listed creature, struck by someone the repellent is protecting, ends it. */
    public static boolean endsRepellent(boolean cancelled, float amount, boolean victimListed, boolean attackerProtected) {
        return !cancelled && amount > 0 && victimListed && attackerProtected;
    }

    /**
     * The repellent keeps bugs from bothering the drinker; it is not cover for hunting them. An ignored attacker is
     * invisible to the native "who hurt me" check, so without this a struck creature could only flee.
     */
    public static final class BrokenByAttack extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery() { return NPCEntity.getComponentType(); }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getInspectDamageGroup(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer, Damage damage) {
            if (!(damage.getSource() instanceof Damage.EntitySource source)) return;
            var attacker = source.getRef();
            var victim = chunk.getComponent(index, NPCEntity.getComponentType());
            if (victim == null || attacker == null || !attacker.isValid()) return;
            int effect = EntityEffect.getAssetMap().getIndex(EFFECT);
            var protection = buffer.getComponent(attacker, EffectControllerComponent.getComponentType());
            if (!endsRepellent(damage.isCancelled(), damage.getAmount(), listed(victim.getRoleIndex(), NPCGroup.getAssetMap().getIndex(GROUP)),
                protection != null && protection.hasEffect(effect))) return;
            // Deferred: the attacker is another entity, and its effects may be mid tick.
            buffer.run(st -> {
                if (!attacker.isValid()) return;
                var current = st.getComponent(attacker, EffectControllerComponent.getComponentType());
                if (current != null && current.hasEffect(effect)) current.removeEffect(attacker, effect, RemovalBehavior.COMPLETE, st);
            });
        }
    }
}
