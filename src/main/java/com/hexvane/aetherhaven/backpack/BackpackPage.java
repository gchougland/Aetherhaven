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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Every button refers to an immutable view of the exact item it displayed. */
public final class BackpackPage extends AetherhavenInteractiveCustomUIPage<BackpackPage.Data> {
    private final Ref<EntityStore> leatherworker;
    private short selected=-1;
    private ItemStack expected;
    private long frame;
    private boolean appended;
    private String picker="";
    private String hint="Left click moves a stack. Right click moves one item.";
    private record Row(int slot,ItemStack stack,boolean deposit){}
    private final Map<String,Row> rows=new HashMap<>();
    private final Map<String,String> actions=new HashMap<>();
    public BackpackPage(PlayerRef player,short selected,Ref<EntityStore> leatherworker){super(player,CustomPageLifetime.CanDismiss,Data.CODEC);this.selected=selected;this.leatherworker=leatherworker;}
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store){
        if(!appended){c.append("Aetherhaven/BackpackPage.ui");appended=true;}
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);if(inv==null){close();return;}
        frame++;rows.clear();actions.clear();counts.clear();
        c.clear("#Choices");c.clear("#Contents");c.clear("#PlayerItems");c.clear("#Options");
        if(selected<0||selected>=inv.getCapacity()||!Backpack.isBackpack(inv.getItemStack(selected))){selected=-1;for(short i=0;i<inv.getCapacity();i++)if(Backpack.isBackpack(inv.getItemStack(i))){selected=i;break;}}

        c.set("#Title.Text","BACKPACK");c.set("#Hint.Text",hint);
        expected=selected<0?null:inv.getItemStack(selected);var pack=Backpack.read(expected);
        c.set("#Summary.Text",pack==null?(selected<0?"Visit Sera Brindle to craft a backpack.":"This backpack cannot be read. Its contents have been preserved."):pack.displayName()+"   |   "+pack.used()+" / "+pack.tier.slots+" slots   |   "+Backpack.stacksLabel(pack.multiplier())+" per slot");
        c.set("#Main.Visible",picker.isEmpty());c.set("#InventoryPanel.Visible",picker.isEmpty());c.set("#Picker.Visible",!picker.isEmpty());
        c.set("#BagIcon.Visible",pack!=null);if(pack!=null)c.set("#BagIcon.ItemId",expected.getItemId());
        c.set("#BagName.Text",pack==null?"No backpack selected":pack.displayName());
        c.set("#Rename.Value",pack==null?"":pack.name);c.set("#RenameButton.Disabled",pack==null);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#RenameButton",event("Rename").append("@Name","#Rename.Value"),false);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#ChooseBag",event("ChooseBag"),false);
        e.addEventBinding(CustomUIEventBindingType.Activating,"#PickerBack",event("Back"),false);
        if("bags".equals(picker)){
            c.set("#PickerTitle.Text","YOUR BACKPACKS");int index=0;
            for(short i=0;i<inv.getCapacity();i++)if(Backpack.isBackpack(inv.getItemStack(i))){
                var bag=Backpack.read(inv.getItemStack(i));c.append("#Choices","Aetherhaven/BackpackChoice.ui");String at="#Choices["+(index++)+"] #Button";
                c.set(at+" #Icon.ItemId",inv.getItemStack(i).getItemId());c.set(at+" #Name.Text",bag==null?"Unreadable backpack":bag.displayName());
                c.set(at+" #Detail.Text",bag==null?"Contents preserved":bag.tier.label+" | "+bag.used()+" / "+bag.tier.slots+" slots"+(i==selected?" | Selected":""));
                e.addEventBinding(CustomUIEventBindingType.Activating,at,event("Select:"+i),false);
            }
        }else if("music".equals(picker)&&pack!=null&&pack.jukebox){
            c.set("#PickerTitle.Text","CHOOSE MUSIC");
            for(var song:AetherhavenPlugin.get().getBardSongCatalog().songsOrdered()){
                var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();var name=i18n==null?null:i18n.getMessage(playerRef.getLanguage(),song.getDisplayLangKey());
                button(c,e,"#Choices",(song.getId().equals(pack.song)?"Playing: ":"")+(name==null?song.getId():name),"Song:"+song.getId());
            }
        }
        if(pack!=null){
            for(int i=0;i<pack.tier.slots;i++)cell(c,e,"#Contents",i,pack.items[i],false);
            if(pack.magnet)button(c,e,"#Options","Magnet: "+(pack.magnetOn?"On":"Off"),"Magnet");
            if(pack.jukebox){
                button(c,e,"#Options","Stop music","Song:");
                button(c,e,"#Options","Choose music","ChooseMusic");
            }
        }
        com.hexvane.aetherhaven.ui.PlayerInventoryLayout.render(c,store,ref,inv,(host,column,slot)->cell(c,e,host,slot,inv.getItemStack(slot),true));
    }
    private EventData event(String action){return new EventData().append("Action",action).append("Frame",Long.toString(frame));}
    private void button(UICommandBuilder c,UIEventBuilder e,String host,String label,String action){
        String id="b"+actions.size();actions.put(id,action);int index=counts.merge(host,1,Integer::sum)-1;
        c.append(host,host.equals("#Choices")?"Aetherhaven/BackpackMusicChoice.ui":"Aetherhaven/BackpackControl.ui");String at=host+"["+index+"] #Button";c.set(at+" #Text.Text",label);e.addEventBinding(CustomUIEventBindingType.Activating,at,event(id),false);
    }
    private final Map<String,Integer> counts=new HashMap<>();
    private void cell(UICommandBuilder c,UIEventBuilder e,String host,int slot,ItemStack stack,boolean deposit){
        int index=counts.merge(host,1,Integer::sum)-1;c.append(host,"Aetherhaven/WarehouseItemSlot.ui");String at=host+"["+index+"]";
        boolean empty=ItemStack.isEmpty(stack);c.set(at+" #Icon.Visible",!empty);c.set(at+" #Quantity.Text",empty?"":Integer.toString(stack.getQuantity()));c.set(at+" #Cell.Disabled",empty);
        if(empty)return;c.set(at+" #Icon.ItemId",stack.getItemId());c.set(at+" #Cell.TooltipText",itemName(stack)+"\n"+stack.getQuantity()+(stack.getQuantity()==1?" item\n":" items\n")+(deposit?"Store in backpack":"Take from backpack"));
        if(deposit&&Backpack.isBackpack(stack)){c.set(at+" #Cell.Disabled",true);return;}
        String id="r"+rows.size();rows.put(id,new Row(slot,stack,deposit));
        e.addEventBinding(CustomUIEventBindingType.Activating,at+" #Cell",event(id),false);
        e.addEventBinding(CustomUIEventBindingType.RightClicking,at+" #Cell",event(id).append("One","true"),false);
    }
    private String itemName(ItemStack stack){var i18n=com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();String s=i18n==null?null:i18n.getMessage(playerRef.getLanguage(),stack.getItem().getTranslationKey());return s==null?stack.getItemId():s;}
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data){
        if(!Long.toString(frame).equals(data.frame))return;
        var inv=InventoryComponent.getCombined(store,ref,InventoryComponent.HOTBAR_STORAGE_BACKPACK);if(inv==null){close();return;}
        boolean ok=false;var row=rows.get(data.action);String action=actions.getOrDefault(data.action,data.action);
        if(row!=null)ok=BackpackExchange.transfer(inv,selected,expected,row.slot(),row.stack(),row.deposit(),"true".equals(data.one));
        else if(action!=null&&action.startsWith("Select:")){selected=Short.parseShort(action.substring(7));picker="";ok=true;}
        else if("ChooseBag".equals(action)){picker="bags";ok=true;}
        else if("ChooseMusic".equals(action)){picker="music";ok=true;}
        else if("Back".equals(action)){picker="";ok=true;}
        else if("Rename".equals(action))ok=BackpackExchange.change(inv,selected,expected,Map.of(),p->p.rename(data.name));
        else if("Magnet".equals(action))ok=BackpackExchange.change(inv,selected,expected,Map.of(),p->{if(!p.magnet)return false;p.magnetOn=!p.magnetOn;return true;});
        else if(action!=null&&action.startsWith("Song:")){
            String song=action.substring(5);if(song.isEmpty()||AetherhavenPlugin.get().getBardSongCatalog().byId(song)!=null)ok=BackpackExchange.change(inv,selected,expected,Map.of(),p->{if(!p.jukebox)return false;p.song=song;return true;});
            if(ok)picker="";
        }
        hint=ok?"Ready. Left click moves a stack. Right click moves one item.":"Unable to do that. Check materials and free space. If the backpack moved, select it again.";
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public String action,frame,one,name;
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Frame",Codec.STRING),(d,v)->d.frame=v,d->d.frame).add()
            .append(new KeyedCodec<>("@Name",Codec.STRING),(d,v)->d.name=v,d->d.name).add()
            .append(new KeyedCodec<>("One",Codec.STRING),(d,v)->d.one=v,d->d.one).add().build();
    }
}
