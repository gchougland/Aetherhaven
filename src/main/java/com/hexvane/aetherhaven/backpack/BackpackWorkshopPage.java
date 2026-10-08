package com.hexvane.aetherhaven.backpack;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Crafting is a separate transaction screen; the portable inventory never purchases upgrades. */
public final class BackpackWorkshopPage extends AetherhavenInteractiveCustomUIPage<BackpackWorkshopPage.Data> {
    private final Ref<EntityStore> leatherworker;
    private short selected=-1;
    private ItemStack expected;
    private long frame;
    private boolean appended;
    private String choice="IRON",hint="Select an upgrade to see its materials and requirements.";
    private record Node(String id,String title,String icon,int x,int y,String parent){}
    private static final List<Node> NODES=List.of(
        new Node("IRON","Iron","Aetherhaven_Backpack_Iron",130,0,""),
        new Node("COBALT","Cobalt","Aetherhaven_Backpack_Cobalt",20,150,"IRON"),
        new Node("THORIUM","Thorium","Aetherhaven_Backpack_Thorium",240,150,"IRON"),
        new Node("ADAMANTITE","Adamantite","Aetherhaven_Backpack_Adamantite",130,300,"COBALT"),
        new Node("Compression1","Compression I","Ingredient_Leather_Light",490,0,""),
        new Node("Compression2","Compression II","Ingredient_Leather_Medium",490,150,"Compression1"),
        new Node("Compression3","Compression III","Ingredient_Leather_Heavy",490,300,"Compression2"),
        new Node("Magnet","Item magnet","Ingredient_Bar_Cobalt",20,465,""),
        new Node("Jukebox","Travelling jukebox","",240,465,""));
    public BackpackWorkshopPage(PlayerRef player,Ref<EntityStore> leatherworker){super(player,CustomPageLifetime.CanDismiss,Data.CODEC);this.leatherworker=leatherworker;}
    private boolean permitted(Ref<EntityStore> ref,Store<EntityStore> store){
        if(leatherworker==null||!leatherworker.isValid())return false;
        var binding=store.getComponent(leatherworker,TownVillagerBinding.getComponentType());
        var a=store.getComponent(ref,TransformComponent.getComponentType());var b=store.getComponent(leatherworker,TransformComponent.getComponentType());
        if(binding==null||!"leatherworker".equals(binding.getKind())||a==null||b==null||a.getPosition().distanceSquared(b.getPosition())>100)return false;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),AetherhavenPlugin.get()).getTown(binding.getTownId());
        return town!=null&&town.hasMemberOrOwner(playerRef.getUuid());
    }
    private static String option(String id){return id.startsWith("Compression")?"Compression":id;}
    private static boolean owned(Backpack p,String id){
        if(p==null)return false;
        if(id.startsWith("Compression"))return p.compression>=Integer.parseInt(id.substring(11));
        if(id.equals("Magnet"))return p.magnet;if(id.equals("Jukebox"))return p.jukebox;
        return p.tier.name().equals(id)||id.equals("IRON")&&p.tier!=Backpack.Tier.SIMPLE;
    }
    private static boolean available(Backpack p,String id){return p!=null&&(!id.startsWith("Compression")||Integer.parseInt(id.substring(11))==p.compression+1)&&BackpackUpgrades.available(p).contains(option(id));}
    private static Map<String,Integer> cost(Backpack p,String id){
        if(p==null)p=Backpack.read(new ItemStack(Backpack.ITEM,1));
        if(id.startsWith("Compression")){int level=Integer.parseInt(id.substring(11));return Map.of("Ingredient_Leather_"+switch(level){case 1->"Light";case 2->"Medium";default->"Heavy";},level*10);}
        return BackpackUpgrades.cost(p,id);
    }
    private static boolean afford(ItemContainer inv,Map<String,Integer> cost){
        var left=new HashMap<>(cost);for(short i=0;i<inv.getCapacity();i++){var s=inv.getItemStack(i);if(!ItemStack.isEmpty(s))left.computeIfPresent(s.getItemId(),(k,v)->v-s.getQuantity());}
        return left.values().stream().allMatch(v->v<=0);
    }
    private EventData event(String action){return new EventData().append("Action",action).append("Frame",Long.toString(frame));}
    private static void anchor(UICommandBuilder c,String at,int x,int y){var a=new Anchor();a.setLeft(Value.of(x));a.setTop(Value.of(y));a.setWidth(Value.of(174));a.setHeight(Value.of(112));c.setObject(at+".Anchor",a);}
    private static void line(UICommandBuilder c,int x,int y,int w,int h){c.appendInline("#Tree","Group { Anchor: (Left: "+x+", Top: "+y+", Width: "+w+", Height: "+h+"); Background: #667b88; }");}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store){
        if(!appended){c.append("Aetherhaven/BackpackWorkshopPage.ui");appended=true;}
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);if(inv==null){close();return;}
        frame++;c.clear("#Bags");c.clear("#Tree");
        if(selected<0||selected>=inv.getCapacity()||!Backpack.isBackpack(inv.getItemStack(selected))){selected=-1;for(short i=0;i<inv.getCapacity();i++)if(Backpack.isBackpack(inv.getItemStack(i))){selected=i;break;}}
        int bi=0;for(short i=0;i<inv.getCapacity();i++)if(Backpack.isBackpack(inv.getItemStack(i))){
            c.append("#Bags","Aetherhaven/BackpackOwnedChoice.ui");String at="#Bags["+(bi++)+"] #Button";var p=Backpack.read(inv.getItemStack(i));
            c.set(at+" #Icon.ItemId",inv.getItemStack(i).getItemId());c.set(at+" #Detail.Text",p==null?"Contents preserved":p.tier.label+" | "+p.used()+" / "+p.tier.slots+" slots");
            c.set(at+" #Name.Text",(i==selected?"Selected: ":"")+(p==null?"Unreadable backpack":p.displayName()));e.addEventBinding(CustomUIEventBindingType.Activating,at,event("Bag:"+i),false);
        }
        expected=selected<0?null:inv.getItemStack(selected);var pack=Backpack.read(expected);boolean allowed=permitted(ref,store);
        c.set("#Summary.Text",pack==null?"Craft a backpack to begin.":pack.displayName()+" | "+pack.tier.slots+" slots | "+Backpack.stacksLabel(pack.multiplier())+" per slot");
        c.set("#Craft.Disabled",!allowed||!afford(inv,Map.of("Ingredient_Leather_Light",10)));c.set("#Open.Disabled",pack==null);
        line(c,217,112,3,20);line(c,107,130,223,3);line(c,107,130,3,20);line(c,327,130,3,20);
        line(c,107,262,3,20);line(c,327,262,3,20);line(c,107,280,223,3);line(c,217,280,3,20);
        line(c,577,112,3,38);line(c,577,262,3,38);
        c.appendInline("#Tree","Group #Nodes { Anchor: (Full: 0); }");int ni=0;
        for(var node:NODES){String at="#Nodes["+(ni++)+"]";c.append("#Nodes","Aetherhaven/BackpackUpgradeNode.ui");anchor(c,at,node.x(),node.y());
            boolean have=owned(pack,node.id()),ready=available(pack,node.id());
            c.set(at+" #Title.Text",node.title());c.set(at+" #State.Text",have?"OWNED":ready?"AVAILABLE":"LOCKED");c.set(at+" #State.Style.TextColor",have?"#8ec7a1":ready?"#e8c984":"#88929e");
            c.set(at+" #Selected.Visible",choice.equals(node.id()));c.set(at+" #LockedBackground.Visible",!have&&!ready);c.set(at+" #Lock.Visible",!have&&!ready);
            c.set(at+" #Icon.Visible",node.icon().isEmpty());c.set(at+" #Item.Visible",!node.icon().isEmpty());if(node.icon().isEmpty())c.set(at+" #Icon.AssetPath","UI/Custom/musical-note.png");else c.set(at+" #Item.ItemId",node.icon());
            c.set(at+".TooltipText",node.title()+"\n"+requirement(pack,node.id()));e.addEventBinding(CustomUIEventBindingType.Activating,at,event("Node:"+node.id()),false);
        }
        String materials=cost(pack,choice).entrySet().stream().sorted(Map.Entry.comparingByKey()).map(x->x.getValue()+" "+name(x.getKey())).collect(java.util.stream.Collectors.joining(", "));
        c.set("#Details.Text",description(pack,choice)+"\n"+materials+"\n"+requirement(pack,choice));
        c.set("#Purchase.Disabled",!allowed||!available(pack,choice)||!afford(inv,cost(pack,choice)));c.set("#Hint.Text",allowed?hint:"Stay near Sera to craft or upgrade a backpack.");
        for(String a:List.of("Craft","Open","Purchase"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+a,event(a),false);
    }
    private String name(String id){var item=new ItemStack(id,1);var i=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();var n=i==null?null:i.getMessage(playerRef.getLanguage(),item.getItem().getTranslationKey());return n==null?id:n;}
    private static String description(Backpack p,String id){if(id.startsWith("Compression"))return "Compression "+id.substring(11)+": adds one more native stack to every slot.";if(id.equals("Magnet"))return "Collect nearby loose items directly into this backpack.";if(id.equals("Jukebox"))return "Play bard music in a radius that follows you.";var t=Backpack.Tier.valueOf(id);return t.label+": "+t.slots+" slots, "+Backpack.stacksLabel(t.stacks+(p==null?0:p.compression))+" per slot.";}
    private static String requirement(Backpack p,String id){if(owned(p,id))return "Already installed.";if(available(p,id))return "Ready to purchase with the materials above.";if(p==null)return "Craft or select a backpack first.";if(id.startsWith("Compression"))return "Requires the previous compression upgrade.";return switch(id){case "IRON"->"Requires a Simple Backpack.";case "COBALT","THORIUM"->"Choose either Cobalt or Thorium from an Iron Backpack.";case "ADAMANTITE"->"Requires Cobalt or Thorium. Combines both paths.";default->"Requires a backpack.";};}
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data d){
        if(!Long.toString(frame).equals(d.frame)||d.action==null)return;var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);if(inv==null)return;
        boolean ok=true;
        if(d.action.startsWith("Bag:"))selected=Short.parseShort(d.action.substring(4));
        else if(d.action.startsWith("Node:")&&NODES.stream().anyMatch(n->n.id().equals(d.action.substring(5))))choice=d.action.substring(5);
        else if(d.action.equals("Open")&&Backpack.read(expected)!=null){store.getComponent(ref,Player.getComponentType()).getPageManager().openCustomPage(ref,store,new BackpackPage(playerRef,selected,null));return;}
        else if(d.action.equals("Craft")&&permitted(ref,store))ok=BackpackExchange.craft(inv);
        else if(d.action.equals("Purchase")&&permitted(ref,store)){var p=Backpack.read(expected);ok=available(p,choice)&&BackpackExchange.change(inv,selected,expected,cost(p,choice),b->BackpackUpgrades.apply(b,option(choice)));}
        else ok=false;
        hint=ok?"Select an upgrade to see its materials and requirements.":"Check your materials, free inventory space and selected backpack.";
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {public String action,frame;public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new).append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add().append(new KeyedCodec<>("Frame",Codec.STRING),(d,v)->d.frame=v,d->d.frame).add().build();}
}
