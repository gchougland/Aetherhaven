package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.backpack.BackpackCraftingSupply;
import com.hexvane.aetherhaven.warehouse.WarehouseLinkedWindow;
import com.hexvane.aetherhaven.warehouse.WarehouseSupplyLink;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.builtin.crafting.window.DiagramCraftingWindow;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.builtin.crafting.window.SimpleCraftingWindow;
import com.hypixel.hytale.builtin.crafting.window.StructuralCraftingWindow;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.BlockWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.joml.Vector3i;

/**
 * Opens a workplace bench's own crafting screen for a player.
 *
 * <p>Using the main bench of a workplace opens that site's tasks, so the bench's ordinary screen is offered as a button
 * on the pages the bench opens. The request travels as an ECS event so it runs with a command buffer, as the base game's
 * own bench interactions do, and it builds the same windows they build ({@code OpenBenchPageInteraction} and
 * {@code OpenProcessingBenchInteraction}). A crafting bench beside a Warehouse Supply Link, or used by someone carrying
 * backpacks, gets the linked window it gets outside a workplace (see {@code WarehouseBenchUseSystem}).
 *
 * <p>A request names two blocks: the bench the player used to reach the page, which is where they must be standing, and
 * the bench to open, which is that same bench or another one of the same workplace (a smeltery lists all its furnaces).
 * Every request answers with an {@link Outcome}, so a page can say why nothing opened instead of doing nothing.
 */
public final class WorkplaceNativeBench {
    private WorkplaceNativeBench() {}
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Shown on the page when the bench did not open and no closer reason is known. */
    public static final String UNAVAILABLE="The bench could not be opened. Stand next to it and try again.";

    /** What became of a request to open a bench. */
    public enum Outcome {
        /** The bench's screen replaced the page. */
        OPENED(null),
        NOT_ALLOWED("You need permission to manage buildings in this town."),
        /** The player has moved away from the bench they opened the page at. */
        TOO_FAR("You are too far from the bench. Stand next to it and try again."),
        /** The block is gone, unloaded, or no longer a bench of this workplace. */
        GONE("That bench is no longer part of this workplace."),
        NO_SCREEN("That bench has no screen of its own."),
        /** The player already has a bench screen open. */
        BUSY("Close the bench screen you have open, then try again."),
        /** The game refused the screen, for example a bench another mod keeps closed. */
        REFUSED(UNAVAILABLE);

        private final String message;
        Outcome(String message) { this.message=message; }
        /** What to tell the player, or null when the bench opened. */
        public String message() { return message; }
    }

    /** One player's request to open the bench at a block. */
    public static final class Open extends EcsEvent {
        private final Vector3i from,pos;
        private final boolean askOnly;
        private Outcome outcome=Outcome.GONE;
        public Open(Vector3i pos) { this(pos,pos,false); }
        /**
         * @param from    the bench the player used to reach the page
         * @param pos     the bench of the same workplace to open
         * @param askOnly answer whether the bench would open, without opening it
         */
        public Open(Vector3i from,Vector3i pos,boolean askOnly) { this.from=new Vector3i(from);this.pos=new Vector3i(pos);this.askOnly=askOnly; }
        public Vector3i from() { return new Vector3i(from); }
        public Vector3i pos() { return new Vector3i(pos); }
        public Outcome outcome() { return outcome; }
    }

    /** What the button says for a kind of bench. Null when the bench has no screen of its own. */
    public static String label(BenchType type) {
        if(type==null)return null;
        return switch(type) {
            case Crafting,DiagramCrafting,StructuralCrafting -> "Craft at this bench";
            case Processing -> "Use this bench";
        };
    }
    /** The button text for the bench at a block, or null when that block has no screen of its own to open. */
    public static String label(World world,Vector3i pos) {
        if(world==null||pos==null)return null;
        var block=ChunkSectionBlockUtil.blockType(world,pos.x,pos.y,pos.z);
        return block==null||block.getBench()==null||block.getItem()==null?null:label(block.getBench().getType());
    }
    /** Call from a page's event handler. The page is replaced by the bench's screen when the bench can be opened. */
    public static Outcome open(Ref<EntityStore> ref,Store<EntityStore> store,Vector3i pos) {
        return open(ref,store,pos,pos);
    }
    /**
     * Opens one bench of a workplace from the page of another: {@code from} is the bench the player stands at and
     * {@code pos} the bench to open. Both must belong to the same workplace.
     */
    public static Outcome open(Ref<EntityStore> ref,Store<EntityStore> store,Vector3i from,Vector3i pos) {
        if(ref==null||!ref.isValid()||from==null||pos==null)return Outcome.GONE;
        // Ask first, while the page is still up to say why a bench stays shut.
        var ask=new Open(from,pos,true);
        store.invoke(ref,ask);
        if(ask.outcome!=Outcome.OPENED)return ask.outcome;
        var entity=store.getComponent(ref,Player.getComponentType());
        if(entity==null)return Outcome.REFUSED;
        // Then leave the page the way every page is left, and open the bench a moment later from no page at all, which
        // is how the base game opens one. Putting the bench screen straight over a custom page makes the server wait for
        // the client to acknowledge that page's end. A client that does not answer for a bench screen would leave every
        // later page deaf to its buttons, and nothing on the server would show it.
        entity.getPageManager().setPage(ref,store,Page.None);
        store.getExternalData().getWorld().execute(()->{
            if(!ref.isValid())return;
            var request=new Open(from,pos,false);
            ref.getStore().invoke(ref,request);
            if(request.outcome==Outcome.OPENED)return;
            // The page is gone by now, so a bench that changed in between is explained in chat.
            var player=ref.getStore().getComponent(ref,PlayerRef.getComponentType());
            if(player!=null)player.sendMessage(Message.raw(request.outcome.message()));
        });
        return Outcome.OPENED;
    }

    public static final class OpenSystem extends EntityEventSystem<EntityStore,Open> {
        public OpenSystem() { super(Open.class); }
        @Override public Query<EntityStore> getQuery() { return Query.and(Player.getComponentType(),PlayerRef.getComponentType()); }
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Open event) {
            var ref=chunk.getReferenceTo(index);var player=chunk.getComponent(index,PlayerRef.getComponentType());
            var entity=chunk.getComponent(index,Player.getComponentType());
            event.outcome=open(ref,store,buffer,player,entity,event);
            // A bench that stays shut is worth a line: it is otherwise invisible from the server's side.
            if(event.outcome!=Outcome.OPENED)LOG.atInfo().log("Workplace bench at %s,%s,%s (page opened at %s,%s,%s) did not open for %s: %s",
                event.pos.x,event.pos.y,event.pos.z,event.from.x,event.from.y,event.from.z,player.getUuid(),event.outcome);
        }
        private static Outcome open(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,PlayerRef player,
                Player entity,Open event) {
            var world=store.getExternalData().getWorld();
            // The same people who may open the site's tasks, standing at the bench they used, as for every other button there.
            var from=WorkplaceBenchAccess.find(world,event.from);
            var target=event.from.equals(event.pos)?from:WorkplaceBenchAccess.find(world,event.pos);
            if(from==null||target==null||!sameSite(from,target))return Outcome.GONE;
            if(!WorkplaceBenchAccess.allowed(target,player.getUuid()))return Outcome.NOT_ALLOWED;
            if(!WorkplaceBenchAccess.inReach(ref,buffer,from.pos()))return Outcome.TOO_FAR;
            var pos=new Vector3i(target.pos());
            var chunks=world.getChunkStore();var section=chunks.getChunkSectionReferenceAtBlock(pos.x,pos.y,pos.z);
            if(section==null||!section.isValid())return Outcome.GONE;
            var cs=chunks.getStore();var blocks=cs.getComponent(section,BlockSection.getComponentType());if(blocks==null)return Outcome.GONE;
            var block=BlockType.getAssetMap().getAsset(blocks.get(pos.x,pos.y,pos.z));
            if(block==null||block.getBench()==null||block.getItem()==null)return Outcome.NO_SCREEN;
            var benchRef=BlockModule.getBlockEntity(cs,section,pos.x,pos.y,pos.z);if(benchRef==null)return Outcome.GONE;
            var bench=cs.getComponent(benchRef,BenchBlock.getComponentType());if(bench==null)return Outcome.NO_SCREEN;
            var craft=buffer.getComponent(ref,CraftingManager.getComponentType());
            if(craft==null)return Outcome.REFUSED;
            forgetClosedScreens(ref,buffer,entity,player.getUuid(),craft,bench.getWindows());
            // A bench screen takes over the player's crafting. Opening a second one would fail inside the game.
            if(craft.hasBenchSet()||bench.getWindows().containsKey(player.getUuid()))return Outcome.BUSY;
            if(block.getBench().getType()==BenchType.Processing&&cs.getComponent(benchRef,ProcessingBenchBlock.getComponentType())==null)return Outcome.NO_SCREEN;
            if(event.askOnly)return Outcome.OPENED;
            int rotation=blocks.getRotationIndex(pos.x,pos.y,pos.z);
            return block.getBench().getType()==BenchType.Processing
                ?openProcessing(ref,store,buffer,world,player,entity,pos,rotation,block,benchRef,bench)
                :openCrafting(ref,store,buffer,world,player,entity,pos,rotation,block,benchRef,bench);
        }
    }
    private static boolean sameSite(WorkplaceBenchAccess.Target a,WorkplaceBenchAccess.Target b) {
        return a.town().getTownId().equals(b.town().getTownId())&&a.site().id().equals(b.site().id());
    }
    /**
     * A screen that is no longer among the player's open windows cannot be what keeps a bench shut. Drops such a
     * leftover from the bench, and frees the player's crafting when no bench screen of theirs is open at all.
     */
    private static void forgetClosedScreens(Ref<EntityStore> ref,CommandBuffer<EntityStore> buffer,Player entity,UUID uuid,
            CraftingManager craft,Map<UUID,BenchWindow> windows) {
        var open=entity.getWindowManager().getWindows();
        var left=windows.get(uuid);
        if(left!=null&&!open.contains(left))windows.remove(uuid,left);
        if(craft.hasBenchSet()&&open.stream().noneMatch(window->window instanceof BenchWindow))craft.clearBench(ref,buffer);
    }
    /** Keeps the base game from closing the screen at once because the player stands a little beyond its own limit. */
    private static void reach(BlockWindow window,Ref<EntityStore> ref,ComponentAccessor<EntityStore> accessor) {
        var transform=accessor.getComponent(ref,TransformComponent.getComponentType());
        if(transform==null)return;
        double distance=Math.sqrt(transform.getPosition().distanceSquared(window.getX(),window.getY(),window.getZ()));
        window.setMaxDistance(WorkplaceBenchRows.screenReach(window.getMaxDistance(),distance));
    }

    /** As {@code OpenBenchPageInteraction}, choosing the Warehouse Supply Link window where the bench would have one. */
    private static Outcome openCrafting(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,World world,
            PlayerRef player,Player entity,Vector3i pos,int rotation,BlockType block,Ref<ChunkStore> benchRef,BenchBlock bench) {
        BenchBlock.refreshGrantedAugmentTags(world.getChunkStore().getStore(),benchRef,pos);
        if(!BenchBlock.tryOpen(ref,buffer,benchRef,pos))return Outcome.REFUSED;
        UUID uuid=player.getUuid();
        var link=supplyLink(ref,buffer,world,uuid,pos,rotation,block);
        BenchWindow window=switch(block.getBench().getType()) {
            case Crafting -> link==null?new SimpleCraftingWindow(pos.x,pos.y,pos.z,rotation,block,bench)
                :new WarehouseLinkedWindow.Simple(pos.x,pos.y,pos.z,rotation,block,bench,link);
            case DiagramCrafting -> link==null?new DiagramCraftingWindow(ref,buffer,pos.x,pos.y,pos.z,rotation,block,bench)
                :new WarehouseLinkedWindow.Diagram(ref,buffer,pos.x,pos.y,pos.z,rotation,block,bench,link);
            case StructuralCrafting -> link==null?new StructuralCraftingWindow(pos.x,pos.y,pos.z,rotation,block,bench)
                :new WarehouseLinkedWindow.Structural(pos.x,pos.y,pos.z,rotation,block,bench,link);
            case Processing -> throw new IllegalStateException("A processing bench has its own window");
        };
        reach(window,ref,buffer);
        var windows=bench.getWindows();
        if(windows.putIfAbsent(uuid,window)!=null)return Outcome.BUSY;
        if(!entity.getPageManager().setPageWithWindows(ref,store,Page.Bench,true,window)){windows.remove(uuid,window);return Outcome.REFUSED;}
        window.registerCloseEvent(closed->windows.remove(uuid,window));
        return Outcome.OPENED;
    }
    /** The town stock and carried backpacks this bench may draw on, or null for a plain bench. */
    private static WarehouseSupplyLink supplyLink(Ref<EntityStore> ref,CommandBuffer<EntityStore> buffer,World world,UUID uuid,
            Vector3i pos,int rotation,BlockType block) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var found=WarehouseSupplyLink.find(plugin,world,uuid,pos.x,pos.y,pos.z,block,rotation);
        if(found==null&&!BackpackCraftingSupply.carriesSupplies(
            InventoryComponent.getCombined(buffer,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK)))return null;
        Supplier<ItemContainer> carried=()->ref.isValid()
            ?InventoryComponent.getCombined(ref.getStore(),ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK):null;
        return (found==null?WarehouseSupplyLink.bagsOnly(plugin,world,uuid):found).withBags(()->BackpackCraftingSupply.slots(carried));
    }
    /** As {@code OpenProcessingBenchInteraction}: furnaces, tanning racks and salvager's benches. */
    private static Outcome openProcessing(Ref<EntityStore> ref,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,World world,
            PlayerRef player,Player entity,Vector3i pos,int rotation,BlockType block,Ref<ChunkStore> benchRef,BenchBlock bench) {
        var cs=world.getChunkStore().getStore();
        var state=cs.getComponent(benchRef,ProcessingBenchBlock.getComponentType());if(state==null)return Outcome.NO_SCREEN;
        var info=cs.getComponent(benchRef,BlockModule.BlockStateInfo.getComponentType());
        var window=new ProcessingBenchWindow(state,bench,info,pos.x,pos.y,pos.z,rotation,block);
        reach(window,ref,buffer);
        UUID uuid=player.getUuid();var windows=bench.getWindows();
        if(windows.putIfAbsent(uuid,window)!=null)return Outcome.BUSY;
        state.updateFuelValues(windows);
        if(!entity.getPageManager().setPageWithWindows(ref,store,Page.Bench,true,window)){windows.remove(uuid,window);return Outcome.REFUSED;}
        window.registerCloseEvent(closed->{
            windows.remove(uuid,window);
            // Reads live terrain only. A bench that was broken or unloaded while open has nothing to reset.
            var current=ChunkSectionBlockUtil.blockType(world,pos.x,pos.y,pos.z);
            if(current==null||current==BlockType.EMPTY||current==BlockType.UNKNOWN||current.getBench()==null)return;
            String shown=current.getCurrentInteractionState();
            var section=world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x,pos.y,pos.z);
            if(windows.isEmpty()&&section!=null&&section.isValid()
                &&!ProcessingBenchBlock.PROCESSING.equals(shown)&&!ProcessingBenchBlock.PROCESS_COMPLETED.equals(shown))
                BlockOperations.setBlockInteractionState(world.getChunkStore(),section,pos.x,pos.y,pos.z,
                    BenchBlock.getBaseBlockType(current),bench.getTierStateName(),true);
            SoundUtil.playSoundEvent2dToPlayer(player,block.getBench().getLocalCloseSoundEventIndex(),SoundCategory.UI);
        });
        SoundUtil.playSoundEvent2dToPlayer(player,block.getBench().getLocalOpenSoundEventIndex(),SoundCategory.UI);
        return Outcome.OPENED;
    }
}
