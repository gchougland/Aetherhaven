package com.hexvane.aetherhaven.patrol;

import com.google.gson.JsonElement;
import com.hexvane.aetherhaven.rts.RtsHostileQuery;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.EntityFilterBase;
import com.hypixel.hytale.server.npc.corecomponents.IEntityFilter;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderEntityFilterBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import javax.annotation.Nonnull;

/**
 * Entity filter {@code AetherhavenGuardThreat} for the guard roles' own target sensors: matches only a creature the
 * guard may fight under the same rule the Java targeting uses ({@link RtsHostileQuery#isGuardThreatTarget}). It
 * replaces a filter on the base game's Aggressive group, which matched every creature of a hostile kind, whoever it
 * belonged to.
 */
public final class BuilderEntityFilterGuardThreat extends BuilderEntityFilterBase {
    @Override @Nonnull public String getShortDescription() { return "Matches a creature this town guard may fight"; }
    @Override @Nonnull public String getLongDescription() { return getShortDescription(); }
    @Override @Nonnull public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Stable; }
    @Override @Nonnull public Builder<IEntityFilter> readConfig(JsonElement data) { return this; }
    @Override @Nonnull public IEntityFilter build(BuilderSupport support) { return new Threat(); }

    private static final class Threat extends EntityFilterBase {
        @Override
        public boolean matchesEntity(@Nonnull Ref<EntityStore> ref, @Nonnull Ref<EntityStore> targetRef,
                                     @Nonnull ExecutionSupport execution, @Nonnull Store<EntityStore> store) {
            return RtsHostileQuery.isGuardThreatTarget(ref, targetRef, store);
        }

        /** A few component reads: dearer than a range test, far cheaper than line of sight, so it runs before that. */
        @Override
        public int cost() {
            return MID_COST;
        }
    }
}
