package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.construction.assembly.BuildingStaffEffects;
import com.hexvane.aetherhaven.construction.assembly.BuildingStaffTip;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Range;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawnerGroup;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3f;

/**
 * Read-only check of the Building Staff's particle effects as the running server decoded them. Effects spawned at a
 * place in the world must end by themselves, because nothing removes them. The effects held while secondary is down
 * must all hang from one point on the staff, its tip.
 */
final class AetherhavenStaffEffectsTestCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "Aetherhaven_Building_Staff_";
    private static final float TOLERANCE = 0.01F;

    AetherhavenStaffEffectsTestCommand() {
        super("stafffx", "Check that Building Staff particle effects end by themselves and share one anchor");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        List<String> failures = new ArrayList<>();
        List<String> lifetimes = new ArrayList<>();
        for (String id : BuildingStaffEffects.FREE_STANDING_SYSTEM_IDS) {
            ParticleSystem system = ParticleSystem.getAssetMap().getAsset(id);
            if (system == null || system.getSpawners() == null || system.getSpawners().length == 0) {
                failures.add(id + ": not loaded");
                continue;
            }
            List<BuildingStaffEffects.Spawner> spawners = new ArrayList<>();
            for (ParticleSpawnerGroup group : system.getSpawners()) {
                ParticleSpawner spawner = ParticleSpawner.getAssetMap().getAsset(group.getSpawnerId());
                if (spawner == null) {
                    failures.add(id + ": spawner " + group.getSpawnerId() + " not loaded");
                    continue;
                }
                spawners.add(describe(group, spawner));
            }
            float seconds = BuildingStaffEffects.secondsUntilGone(system.getLifeSpan(), spawners);
            String name = id.startsWith(PREFIX) ? id.substring(PREFIX.length()) : id;
            lifetimes.add(name + " " + (Float.isInfinite(seconds) ? "never ends" : String.format(Locale.ROOT, "%.2fs", seconds)));
            if (!BuildingStaffEffects.endsInTime(system.getLifeSpan(), spawners)) {
                failures.add(id + ": " + (Float.isInfinite(seconds) ? "never ends by itself" : "lasts " + seconds + " seconds"));
            }
        }
        String held = checkHeldAnchor(failures);
        for (String failure : failures) {
            LOG.atWarning().log("Building staff effects: %s", failure);
        }
        String result = "Building staff effects check: " + (failures.isEmpty() ? "PASSED" : "FAILED") + ", "
            + lifetimes.size() + " effects spawned in the world, longest time on screen per spawn: " + String.join(", ", lifetimes)
            + "; held effects: " + held + "; " + failures.size() + " failures.";
        LOG.atInfo().log(result);
        ctx.sendMessage(Message.raw(result));
        for (String failure : failures) {
            ctx.sendMessage(Message.raw(failure));
        }
        return CompletableFuture.completedFuture(null);
    }

    /** The one attachment of the held interaction and the spawners that share its origin. */
    private static String checkHeldAnchor(List<String> failures) {
        Interaction interaction = Interaction.getAssetMap().getAsset(BuildingStaffEffects.HELD_INTERACTION_ID);
        ModelParticle[] attached = interaction == null || interaction.getEffects() == null ? null : interaction.getEffects().getParticles();
        if (attached == null || attached.length != 1) {
            failures.add(BuildingStaffEffects.HELD_INTERACTION_ID + ": expected one attached effect, found "
                + (attached == null ? "none" : String.valueOf(attached.length)));
            return "not loaded";
        }
        ModelParticle anchor = attached[0];
        Vector3f offset = anchor.getPositionOffset();
        float along = offset == null ? 0.0F : offset.x;
        if (!BuildingStaffEffects.HELD_SYSTEM_ID.equals(anchor.getSystemId())) {
            failures.add("held effect is " + anchor.getSystemId() + ", expected " + BuildingStaffEffects.HELD_SYSTEM_ID);
        }
        if (!BuildingStaffEffects.HELD_ANCHOR_NODE.equals(anchor.getTargetNodeName())) {
            failures.add("held effect hangs from " + anchor.getTargetNodeName() + ", expected " + BuildingStaffEffects.HELD_ANCHOR_NODE);
        }
        if (offset == null || Math.abs(along - (float) BuildingStaffTip.TIP_REACH_BLOCKS) > TOLERANCE
            || Math.abs(offset.y) > TOLERANCE || Math.abs(offset.z) > TOLERANCE) {
            failures.add("held effect offset is " + offset + ", expected " + BuildingStaffTip.TIP_REACH_BLOCKS + " along the handle");
        }
        ParticleSystem system = ParticleSystem.getAssetMap().getAsset(BuildingStaffEffects.HELD_SYSTEM_ID);
        int spawners = system == null || system.getSpawners() == null ? 0 : system.getSpawners().length;
        if (spawners == 0) {
            failures.add(BuildingStaffEffects.HELD_SYSTEM_ID + ": not loaded");
            return "not loaded";
        }
        for (ParticleSpawnerGroup group : system.getSpawners()) {
            Vector3f moved = group.getPositionOffset();
            if (moved != null && (Math.abs(moved.x) > TOLERANCE || Math.abs(moved.y) > TOLERANCE || Math.abs(moved.z) > TOLERANCE)) {
                failures.add(group.getSpawnerId() + " starts " + moved + " away from the shared anchor");
            }
            if (ParticleSpawner.getAssetMap().getAsset(group.getSpawnerId()) == null) {
                failures.add(BuildingStaffEffects.HELD_SYSTEM_ID + ": spawner " + group.getSpawnerId() + " not loaded");
            }
        }
        return spawners + " spawners on " + anchor.getTargetNodeName() + " at "
            + String.format(Locale.ROOT, "%.3f", along) + " blocks, scale " + String.format(Locale.ROOT, "%.2f", anchor.getScale());
    }

    private static BuildingStaffEffects.Spawner describe(ParticleSpawnerGroup group, ParticleSpawner spawner) {
        Range total = spawner.getTotalParticles();
        Rangef rate = spawner.getSpawnRate();
        Rangef life = spawner.getParticleLifeSpan();
        Rangef groupLife = group.getLifeSpan();
        return new BuildingStaffEffects.Spawner(
            spawner.getId(),
            group.getStartDelay(),
            total == null ? -1 : Math.max(total.min, total.max),
            rate == null ? BuildingStaffEffects.DEFAULT_SPAWN_RATE : Math.min(rate.min, rate.max),
            spawner.isSpawnBurst(),
            spawner.getMaxConcurrentParticles(),
            spawner.getLifeSpan(),
            groupLife == null ? 0.0F : Math.max(groupLife.min, groupLife.max),
            life == null ? BuildingStaffEffects.DEFAULT_PARTICLE_LIFE_SECONDS : Math.max(life.min, life.max)
        );
    }
}
