package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.ShopClerkWorkService;
import com.hexvane.aetherhaven.shopspot.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Records shelf assignment. The shop is chosen here; when it comes first is the Commerce priority in the journal. */
public final class ShopStaffingPage extends AetherhavenInteractiveCustomUIPage<ShopStaffingPage.Data> {
    private final UUID townId,plotId;
    private boolean appended;
    private String selected,hint="Clerks work during daylight and take breaks for their needs. Sales pause while they are away.";
    private long frame;
    private final Set<String> choices=new HashSet<>();
    public ShopStaffingPage(PlayerRef player,UUID townId,UUID plotId) {
        super(player,CustomPageLifetime.CanDismissOrCloseThroughInteraction,Data.CODEC);this.townId=townId;this.plotId=plotId;
    }
    private boolean allowed(TownRecord town,AetherhavenPlugin plugin) {
        return town!=null&&town.hasMemberOrOwner(playerRef.getUuid())&&town.playerCanPlacePlots(playerRef.getUuid())
            &&ShopStaffing.isShop(town.findPlotById(plotId),plugin.getConstructionCatalog());
    }
    private String unavailable(TownRecord town,TownsfolkWorkRoster.Worker w) {
        return ShopStaffing.unavailable(w.progress(),TownAdmission.isMember(town,w.entityId()),w.alive(),w.guard(),town.getPayroll().overdue(w.characterId()));
    }
    @Override public void build(Ref<EntityStore> ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore> store) {
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(!allowed(town,plugin)){close();return;}
        if(!appended){c.append("Aetherhaven/ShopStaffingPage.ui");appended=true;}
        var plot=town.findPlotById(plotId);var roster=TownsfolkWorkRoster.list(town,manager,store,plugin);
        if(selected==null)selected=plot.getShopClerkId();
        var chosen=roster.stream().filter(w->Objects.equals(w.characterId(),selected)).findFirst().orElse(null);
        var current=roster.stream().filter(w->Objects.equals(w.characterId(),plot.getShopClerkId())).findFirst().orElse(null);
        int attending=ShopClerkWorkService.attendingSkill(store,town,plotId);
        c.set("#ShopStatus.Text",current==null?"No clerk assigned. The shop is closed.":current.name()+" | "+(attending>0?"Serving customers":!ShopSpotOpenService.isGameDay(store)?"Off shift until daylight":"Away from the counter"));
        c.set("#ClerkPortrait.Visible",chosen!=null);c.set("#ClerkName.Text",chosen==null?"Choose a clerk":chosen.name());
        c.set("#ClerkSkill.Text",chosen==null?"": "Commerce "+chosen.progress().skill(TownsfolkSkill.COMMERCE)+" / 10");
        c.set("#Chance.Text",chosen==null?"":Math.round(100*ShopStaffing.purchaseChance(chosen.progress().skill(TownsfolkSkill.COMMERCE)))+"% visitor purchase chance");
        String blocked=chosen==null?null:unavailable(town,chosen);
        String elsewhere=chosen==null?null:town.getPlotInstances().stream().filter(p->!plotId.equals(p.getPlotId())&&chosen.characterId().equals(p.getShopClerkId())).map(p->"Moves this clerk from another shop. ").findFirst().orElse("");
        c.set("#Details.Text",chosen==null?"Select a portrait to compare Commerce skill.":blocked!=null?blocked:elsewhere+"Uses the normal weekly Apprentice wage. During the day the clerk follows the Commerce priority in the town journal's Work tab: work ranked above it is done first.");
        if(chosen!=null)c.set("#ClerkPortrait.AssetPath",NpcPortraitProvider.portraitPathForTownsfolk(plugin.getTownsfolkCharacterCatalog().byId(chosen.characterId())));
        c.set("#Assign.Disabled",chosen==null||blocked!=null||Objects.equals(selected,plot.getShopClerkId())&&chosen.progress().isEmployed());
        c.set("#Assign.Text",chosen!=null&&Objects.equals(selected,plot.getShopClerkId())?(chosen.progress().isEmployed()?"Assigned":"Resume employment"):"Assign clerk");
        c.set("#Unassign.Disabled",plot.getShopClerkId()==null);c.set("#Hint.Text",hint);
        frame++;choices.clear();c.clear("#Residents");int index=0;
        for(var w:roster) {
            if(index%2==0)c.append("#Residents","Aetherhaven/InnGuestRow.ui");
            String host="#Residents["+index/2+"]";c.append(host,"Aetherhaven/InnGuestCard.ui");String card=host+"["+index%2+"]";index++;
            choices.add(w.characterId());c.set(card+" #Name.Text",w.name());
            c.set(card+" #Portrait.AssetPath",NpcPortraitProvider.portraitPathForTownsfolk(plugin.getTownsfolkCharacterCatalog().byId(w.characterId())));
            c.set(card+" #Skill.Text","Commerce "+w.progress().skill(TownsfolkSkill.COMMERCE)+" / 10");
            String reason=unavailable(town,w);
            c.set(card+" #Status.Text",reason!=null?!w.alive()?"Needs revival":w.guard()?"Guard duty":w.progress().skill(TownsfolkSkill.COMMERCE)==0?"Cannot do Commerce":"Overdue wages":Objects.equals(w.characterId(),plot.getShopClerkId())?"Assigned here":"Available");
            c.set(card+" #Selected.Visible",w.characterId().equals(selected));
            e.addEventBinding(CustomUIEventBindingType.Activating,card+" #Choose",new EventData().append("Action","Choose").append("Choice",w.characterId()).append("Frame",Long.toString(frame)),false);
        }
        if(index%2!=0)c.appendInline("#Residents["+(index-1)/2+"]","Group { FlexWeight: 1; Anchor: (Right: 8); }");
        c.set("#Empty.Visible",roster.isEmpty());
        for(String action:List.of("Assign","Unassign","Refresh","Close"))e.addEventBinding(CustomUIEventBindingType.Activating,"#"+action,new EventData().append("Action",action).append("Frame",Long.toString(frame)),false);
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref,Store<EntityStore> store,Data data) {
        if("Close".equals(data.action)){close();return;}
        var plugin=AetherhavenPlugin.get();if(plugin==null)return;
        var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);
        var town=manager.getTown(townId);if(!allowed(town,plugin)){close();return;}
        var plot=town.findPlotById(plotId);
        if(!Long.toString(frame).equals(data.frame))hint="The roster changed. Select a resident again.";
        else if("Choose".equals(data.action)&&choices.contains(data.choice))selected=data.choice;
        else if("Unassign".equals(data.action)) {
            ShopStaffing.assign(town,plot,null);manager.updateTown(town);
            hint="Shop assignment removed. Employment and work priorities are unchanged.";
        } else if("Assign".equals(data.action)&&selected!=null) {
            var worker=TownsfolkWorkRoster.list(town,manager,store,plugin).stream().filter(w->selected.equals(w.characterId())).findFirst().orElse(null);
            String error=worker==null?"Choose a current town resident.":unavailable(town,worker);
            if(error==null){ShopStaffing.assign(town,plot,selected);worker.progress().setEmployed(true);town.getPayroll().ensureCurrentBill(worker.characterId());manager.updateTown(town);hint=worker.name()+" will serve customers here during daylight. Pay weekly wages from your inventory or treasury in Bills.";}
            else hint=error;
        }
        var c=new UICommandBuilder();var e=new UIEventBuilder();build(ref,c,e,store);sendUpdate(c,e,false);
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC=BuilderCodec.builder(Data.class,Data::new)
            .append(new KeyedCodec<>("Action",Codec.STRING),(d,v)->d.action=v,d->d.action).add()
            .append(new KeyedCodec<>("Choice",Codec.STRING),(d,v)->d.choice=v,d->d.choice).add()
            .append(new KeyedCodec<>("Frame",Codec.STRING),(d,v)->d.frame=v,d->d.frame).add().build();
        String action,choice,frame;
    }
}
