package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Permanent charter choices, presented with the same nodes and details as building upgrades. */
public final class CharterAmendmentsPage extends AetherhavenInteractiveCustomUIPage<CharterAmendmentsPage.PageData> {
    private record Choice(String id,String label,String icon,int tier,int x,int y) {}
    private static final List<Choice> CHOICES=List.of(
        new Choice("t1_per_capita","t1.perCapita","MultipleUsers",1,125,52),
        new Choice("t1_happiness","t1.happiness","Emotions",1,341,52),
        new Choice("t2_mining","t2.mining","Pickaxe",2,125,290),
        new Choice("t2_logging","t2.logging","Axe",2,341,290),
        new Choice("t2_farming","t2.farming","Farmer",2,125,452),
        new Choice("t2_ranching","t2.ranching","Bull",2,341,452));
    private boolean appended;
    private String selected="t1_per_capita",hint="Choose one amendment from each tier. Adopted choices are permanent.";
    public CharterAmendmentsPage(PlayerRef player){super(player,CustomPageLifetime.CanDismissOrCloseThroughInteraction,PageData.CODEC);}
    private TownRecord town(Store<EntityStore> store){
        var plugin=AetherhavenPlugin.get();if(plugin==null)return null;
        var town=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin).findTownForOwnerInWorld(playerRef.getUuid());
        return town!=null&&town.playerCanManageConstructions(playerRef.getUuid())?town:null;
    }
    private static Message label(String key){return Message.translation("aetherhaven_ui_town.aetherhaven.ui.charter."+key);}
    private static boolean chosen(TownRecord town,Choice c){
        return switch(c.id()){
            case "t1_per_capita"->town.getCharterTaxPolicyEnum()==CharterTaxPolicy.PER_CAPITA;
            case "t1_happiness"->town.getCharterTaxPolicyEnum()==CharterTaxPolicy.HAPPINESS_WEIGHTED;
            default->town.getCharterSpecializationEnum()!=null&&town.getCharterSpecializationEnum().name().equalsIgnoreCase(c.id().substring(3));
        };
    }
    private static String blocked(TownRecord town,Choice c,int tier){
        if(chosen(town,c))return "ADOPTED";
        if(c.tier()==1&&town.getCharterTaxPolicyEnum()!=null||c.tier()==2&&town.getCharterSpecializationEnum()!=null)return "ANOTHER AMENDMENT CHOSEN";
        if(tier<c.tier())return "Requires Town Level "+(c.tier()==1?3:5);
        if(c.tier()==2&&town.getCharterTaxPolicyEnum()==null)return "Adopt a tax policy first";
        return null;
    }
    private static void anchor(UICommandBuilder c,String path,int x,int y,int width,int height){
        var a=new Anchor();a.setLeft(Value.of(x));a.setTop(Value.of(y));a.setWidth(Value.of(width));a.setHeight(Value.of(height));c.setObject(path+".Anchor",a);
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store){
        var town=town(store);if(town==null){close();return;}
        if(!appended){c.append("Aetherhaven/CharterAmendments.ui");appended=true;}
        int townLevel=TownPopulation.levelFor(TownPopulation.count(town)),tier=TownCharterService.unlockedAmendmentTier(townLevel);
        c.set("#Summary.Text",town.getDisplayName()+"  |  Town Level "+townLevel+"  |  Charter amendments");
        c.clear("#Canvas");
        c.appendInline("#Canvas","Label { Anchor: (Left: 24, Top: 10, Width: 590, Height: 28); Text: \"TAX POLICY | TOWN LEVEL 3 | CHOOSE ONE\"; Style: (FontSize: 14, TextColor: #e8c984); }");
        c.appendInline("#Canvas","Group { Anchor: (Left: 319, Top: 181, Width: 2, Height: 68); Background: #7bad99; }");
        c.appendInline("#Canvas","Label { Anchor: (Left: 24, Top: 249, Width: 590, Height: 28); Text: \"SPECIALIZATION | TOWN LEVEL 5 | CHOOSE ONE\"; Style: (FontSize: 14, TextColor: #e8c984); }");
        c.appendInline("#Canvas","Group #Nodes { Anchor: (Full: 0); }");
        for(int i=0;i<CHOICES.size();i++){
            var n=CHOICES.get(i);String path="#Nodes["+i+"]",reason=blocked(town,n,tier);boolean owned=chosen(town,n);
            c.append("#Nodes","Aetherhaven/BuildingUpgradeNode.ui");anchor(c,path,n.x(),n.y(),174,112);
            c.set(path+" #Title.TextSpans",label(n.label()+"Short"));
            c.set(path+" #Icon.AssetPath","Icons/ItemsGenerated/Aetherhaven_Charter_Icon_"+n.icon()+".png");
            c.set(path+" #Item.Visible",false);c.set(path+" #Selected.Visible",selected.equals(n.id()));
            c.set(path+" #LockedBackground.Visible",!owned&&reason!=null);c.set(path+" #Lock.Visible",!owned&&reason!=null);
            c.set(path+" #State.Text",owned?"ADOPTED":reason==null?"AVAILABLE":"LOCKED");
            c.set(path+" #State.Style.TextColor",owned?"#96d4b4":reason==null?"#e8c984":"#889bad");
            e.addEventBinding(CustomUIEventBindingType.Activating,path,new EventData().append("Action","Select").append("Choice",n.id()),false);
        }
        var n=CHOICES.stream().filter(v->v.id().equals(selected)).findFirst().orElse(CHOICES.getFirst());String reason=blocked(town,n,tier);
        c.set("#DetailTitle.TextSpans",label(n.label()+"Short"));c.set("#Description.TextSpans",label(n.label()+"Tooltip"));
        c.set("#DetailIcon.AssetPath","Icons/ItemsGenerated/Aetherhaven_Charter_Icon_"+n.icon()+".png");c.set("#DetailIcon.Visible",true);c.set("#DetailItem.Visible",false);
        c.set("#DetailLock.Visible",!chosen(town,n)&&reason!=null);c.set("#DetailState.Text",reason==null?"READY TO ADOPT":reason);
        c.set("#Requires.Text",(n.tier()==1?"Town Level 3":"Town Level 5\nA chosen tax policy")+"\nChoose one option in this tier.");
        c.clear("#Costs");c.set("#Gold.Text","No gold required");c.set("#Purchase.Text",chosen(town,n)?"Adopted":"Adopt amendment");c.set("#Purchase.Disabled",reason!=null);
        c.set("#Hint.Text",hint);e.addEventBinding(CustomUIEventBindingType.Activating,"#Purchase",new EventData().append("Action","Pick").append("Choice",n.id()),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,PageData data){
        var town=town(store);if(town==null){close();return;}
        var choice=CHOICES.stream().filter(c->c.id().equals(data.choice)).findFirst().orElse(null);if(choice==null)return;
        if("Select".equals(data.action))selected=choice.id();
        else if("Pick".equals(data.action)){
            var error=blocked(town,choice,TownCharterService.unlockedAmendmentTier(TownPopulation.levelFor(TownPopulation.count(town))));
            if(error!=null)hint=error;
            else{
                if(choice.tier()==1)town.setCharterTaxPolicy((choice.id().equals("t1_per_capita")?CharterTaxPolicy.PER_CAPITA:CharterTaxPolicy.HAPPINESS_WEIGHTED).id());
                else town.setCharterSpecialization(CharterSpecialization.valueOf(choice.id().substring(3).toUpperCase(Locale.ROOT)).id());
                AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),AetherhavenPlugin.get()).updateTown(town);
                hint="Amendment adopted. Your charter has been updated.";
            }
        }else return;
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC=BuilderCodec.builder(PageData.class,PageData::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Choice",Codec.STRING),(d,v)->d.choice=v,d->d.choice).add().build();
        String action,choice;
    }
}
