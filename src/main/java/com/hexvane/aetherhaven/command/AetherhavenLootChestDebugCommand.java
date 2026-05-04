package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.jewelry.LootChestBonusApplier;
import com.hypixel.hytale.builtin.adventure.stash.StashGameplayConfig;
import com.hypixel.hytale.builtin.adventure.stash.StashPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Debug: apply the same Aetherhaven chest bonus rolls to the item container the player is looking at (independent of
 * block id filters). Uses {@link TargetUtil#getTargetBlock} and the same block resolution as the built-in stash
 * command (filler / multi-voxel).
 */
public final class AetherhavenLootChestDebugCommand extends AbstractCommandCollection {
    public AetherhavenLootChestDebugCommand() {
        super("debug-lootchest", "aetherhaven_commands_help.commands.aetherhaven.debug_lootchest.desc");
        this.addSubCommand(new FillSubCommand());
    }

    private static final class FillSubCommand extends AbstractPlayerCommand {
        private static final int REACH = 10;

        FillSubCommand() {
            super("fill", "aetherhaven_commands_help.commands.aetherhaven.debug_lootchest.fill.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            if (plugin == null) {
                return;
            }
            AetherhavenPluginConfig cfg = plugin.getConfig().get();
            Vector3i block = TargetUtil.getTargetBlock(ref, (double) REACH, store);
            if (block == null) {
                playerRef.sendMessage(Message.translation("server.commands.errors.playerNotLookingAtBlock"));
                return;
            }
            BlockTarget target = resolveItemContainerBlock(world, block);
            if (target == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_quests_portals.aetherhaven.debug.loot.noContainer")
                );
                return;
            }
            if (target.container().getDroplist() != null) {
                StashGameplayConfig sg = StashGameplayConfig.getOrDefault(world.getGameplayConfig());
                StashPlugin.stash(target.stateInfo(), target.container(), sg.isClearContainerDropList());
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Store<ChunkStore> cs = target.store();
            BlockModule.BlockStateInfo bsi = target.stateInfo();
            ItemContainerBlock c = target.container();
            LootChestBonusApplier.applyAll(cs, bsi, c, cfg, rnd, true, true, true);
            SimpleItemContainer inv = c.getItemContainer();
            if (inv != null) {
                LootChestBonusApplier.tryInjectGaiaDraughtBonusesToContainer(inv, cfg, rnd, true);
            }
            playerRef.sendMessage(Message.translation("aetherhaven_quests_portals.aetherhaven.debug.loot.bonusApplied"));
        }
    }

    private record BlockTarget(
        @Nonnull Store<ChunkStore> store, @Nonnull BlockModule.BlockStateInfo stateInfo, @Nonnull ItemContainerBlock container
    ) {}

    @Nullable
    private static BlockTarget resolveItemContainerBlock(@Nonnull World world, @Nonnull Vector3i block) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(block.x, block.y, block.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        BlockSection section = chunkStoreStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return null;
        }
        int filler = section.getFiller(block.x, block.y, block.z);
        if (filler != 0) {
            block.x = block.x - FillerBlockUtil.unpackX(filler);
            block.y = block.y - FillerBlockUtil.unpackY(filler);
            block.z = block.z - FillerBlockUtil.unpackZ(filler);
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(block.x, block.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }
        BlockComponentChunk worldChunkComponent = chunkStoreStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (worldChunkComponent == null) {
            return null;
        }
        Ref<ChunkStore> state = worldChunkComponent.getEntityReference(ChunkUtil.indexBlockInColumn(block.x, block.y, block.z));
        if (state == null) {
            return null;
        }
        Store<ChunkStore> s = state.getStore();
        ItemContainerBlock c = s.getComponent(state, ItemContainerBlock.getComponentType());
        BlockModule.BlockStateInfo bsi = s.getComponent(state, BlockModule.BlockStateInfo.getComponentType());
        if (c == null || bsi == null) {
            return null;
        }
        return new BlockTarget(s, bsi, c);
    }
}
