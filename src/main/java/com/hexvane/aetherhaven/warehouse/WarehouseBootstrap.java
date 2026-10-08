package com.hexvane.aetherhaven.warehouse;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;

public final class WarehouseBootstrap {
    private WarehouseBootstrap() {}

    public static void register(AetherhavenPlugin plugin) {
        WarehouseDeskBlock.register(plugin.getChunkStoreRegistry());
        WarehouseSupplyLinkBlock.register(plugin.getChunkStoreRegistry());
        CourierTravelState.register(plugin.getEntityStoreRegistry());
        com.hexvane.aetherhaven.shopspot.ShopClerkState.register(plugin.getEntityStoreRegistry());
        com.hexvane.aetherhaven.workplace.ApprenticeWorkState.register(plugin.getEntityStoreRegistry());
        OpenCustomUIInteraction.registerCustomPageSupplier(plugin, com.hexvane.aetherhaven.workplace.WorkplaceOrdersPage.class, "AetherhavenWorkplaceBench",
            (ref,accessor,player,context)->{
                var pos=context.getTargetBlock();if(pos==null)return null;
                var target=com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess.find(ref.getStore().getExternalData().getWorld(),new org.joml.Vector3i(pos.x,pos.y,pos.z));
                if(!com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess.allowed(target,player.getUuid())) {
                    player.sendMessage(Message.raw("Create a Jewelry Crafting workplace around this bench to plan tasks."));return null;
                }
                return new com.hexvane.aetherhaven.workplace.WorkplaceOrdersPage(player,target.town().getTownId(),target.site().id(),target.pos());
            });
        OpenCustomUIInteraction.registerCustomPageSupplier(plugin, WarehousePage.class, "AetherhavenWarehouse",
            (ref, accessor, player, context) -> {
                var target = context.getTargetBlock();
                if (target == null) return null;
                var world = ref.getStore().getExternalData().getWorld();
                var block = ChunkSectionBlockUtil.blockType(world, target.x, target.y, target.z);
                if (block == null || !(WarehouseAccess.DESK_ITEM.equals(block.getId())||WarehouseSupplyLink.ITEM.equals(block.getId()))) return null;
                var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                var town = manager.findTownContainingBlock(world.getName(), target.x, target.z);
                if (town == null) {
                    player.sendMessage(Message.raw("Place this block inside your town's borders."));
                    return null;
                }
                if (!WarehouseAccess.canBrowse(town, player.getUuid())) {
                    player.sendMessage(Message.raw("You need permission to open this town's storage."));
                    return null;
                }
                return new WarehousePage(player, town.getTownId());
            });
        OpenCustomUIInteraction.registerCustomPageSupplier(plugin, WarehousePage.class, "AetherhavenFieldLedger",
            (ref, accessor, player, context) -> {
                var transform = accessor.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                if (transform == null) return null;
                var world = ref.getStore().getExternalData().getWorld();
                var position = transform.getPosition();
                var manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                var town = manager.findTownContainingBlock(world.getName(), (int)Math.floor(position.x), (int)Math.floor(position.z));
                if (!WarehouseAccess.canBrowse(town, player.getUuid()) || !WarehouseAccess.hasManager(town)) {
                    player.sendMessage(Message.raw("Use this ledger inside a town where you can access a resident Warehouse Manager's storage."));
                    return null;
                }
                return new WarehousePage(player, town.getTownId());
            });
    }
}
