package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import static com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation.canPlayerInteractWithEntity;

/** Daily affection belongs to the animal, so multiple players cannot repeat the reward. */
public final class BarnPetInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<BarnPetInteraction> CODEC=BuilderCodec.builder(BarnPetInteraction.class,BarnPetInteraction::new,SimpleInstantInteraction.CODEC).build();
    // The native UseEntity interaction already supplies the target. A SimpleInteraction
    // has no client implementation that can provide another round of target data.
    @Override public WaitForDataFrom getWaitForDataFrom(){return WaitForDataFrom.Server;}
    @Override public boolean needsRemoteSync(){return true;}
    @Override protected void simulateFirstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {}
    @Override protected void firstRun(InteractionType type,InteractionContext context,CooldownHandler cooldown) {
        var buffer=context.getCommandBuffer();var player=context.getEntity();
        if(type!=InteractionType.Use||buffer==null||player==null||!player.isValid()){context.getState().state=InteractionState.Failed;return;}
        Ref<EntityStore> candidate=context.getTargetEntity();
        if((candidate==null||!candidate.isValid())&&context.getClientState()!=null)
            candidate=buffer.getStore().getExternalData().getRefFromNetworkId(context.getClientState().entityId);
        if(candidate==null||!candidate.isValid())candidate=TargetUtil.getTargetEntity(player,5,buffer.getStore());
        final var target=candidate;
        if(target==null||!target.isValid()||!canPlayerInteractWithEntity(player,buffer,context.getHeldItem(),target)){
            context.getState().state=InteractionState.Failed;return;
        }
        buffer.run(store->{
            if(!player.isValid()||!target.isValid())return;
            var binding=store.getComponent(target,BarnAnimalBinding.getComponentType());var pr=store.getComponent(player,PlayerRef.getComponentType());
            var p=store.getComponent(player,TransformComponent.getComponentType());var t=store.getComponent(target,TransformComponent.getComponentType());
            if(binding==null||pr==null||p==null||t==null||p.getPosition().distanceSquared(t.getPosition())>25)return;
            var plugin=AetherhavenPlugin.get();if(plugin==null)return;
            var manager=AetherhavenWorldRegistries.getOrCreateTownManager(store.getExternalData().getWorld(),plugin);var town=manager.getTown(binding.town);
            if(town==null||!town.hasMemberOrOwner(pr.getUuid())||!NativeHerdService.barn(plugin,town.findPlotById(binding.plot)))return;
            var state=town.getOrCreatePlotProduction(binding.plot);var animal=state.herd().animal(binding.animal);if(animal==null)return;
            boolean changed=state.herd().pet(binding.animal,Math.floorDiv(NativeHerdService.minute(store),1440),WorkplaceProductionUpgrades.slotCount(state));
            if(changed){
                manager.updateTown(town);
                com.hexvane.aetherhaven.villager.gift.VillagerGiftService.playLoveGiftParticles(target,store);
            }
            pr.sendMessage(Message.raw(changed?animal.name()+" enjoyed the attention.":"You already pet "+animal.name()+" today."));
        });
    }
}
