package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

public final class NativeTimberUprooting {
    private NativeTimberUprooting() {}

    /** Do not forget ownership until the paid stone is safely back in the world. */
    public static String uproot(World world, TimberProductionState timber, int stone) {
        if (!world.isInThread()) throw new IllegalStateException("Uprooting requires the world thread");
        var planted = timber.trees().get(stone);
        String issue = TimberRootstoneRestoration.restore(planted, p -> {
            var ground = NativeTimberPlanting.observe(world, p);
            return new Observed(ground.loaded(), ground.block(), ground.rotation(), ground.filler(), ground.protectedCell());
        }, (p, target) -> NativeTimberHarvest.write(world, p, target));
        if (issue != null) return issue;
        // An abandoned half-grown tree must stay as it is, not grow again and consume the restored stone.
        if (planted != null && NativeTimberTending.growth(world, planted.tree()) != null) {
            var p = planted.tree().root();
            var ref = ChunkSectionBlockUtil.blockEntityRefAt(world, p.x(), p.y(), p.z());
            ref.getStore().removeComponent(ref, FarmingBlock.getComponentType());
            var section = ChunkSectionBlockUtil.blockSectionAt(world, p.x(), p.y(), p.z());
            if (section != null) section.setTicking(p.x(), p.y(), p.z(), false);
            var components = ChunkSectionBlockUtil.blockComponentSectionAt(world, p.x(), p.y(), p.z());
            if (components != null) components.markBlockNeedsSaving(ChunkUtil.indexBlock(p.x(), p.y(), p.z()));
        }
        return timber.uproot(stone) ? null : "This Rootstone is no longer bound to a tree.";
    }
}
