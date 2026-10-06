package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class WorkplaceModePage extends AetherhavenInteractiveCustomUIPage<WorkplaceModePage.Data> {
    public WorkplaceModePage(PlayerRef player) { super(player, CustomPageLifetime.CanDismiss, Data.CODEC); }
    @Override public void build(Ref<EntityStore> ref, UICommandBuilder c, UIEventBuilder e, Store<EntityStore> store) {
        c.append("Aetherhaven/WorkplaceModePage.ui");
        var selected = WorkplaceToolState.obtain(ref, store).mode;
        for (var mode : WorkplaceToolState.Mode.values()) {
            c.set("#" + mode.name() + " #Active.Visible", mode == selected);
            e.addEventBinding(CustomUIEventBindingType.Activating,
                "#" + mode.name(), EventData.of("Mode", mode.name()), false);
        }
    }
    @Override public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
        WorkplaceToolState.Mode mode; try { mode = WorkplaceToolState.Mode.valueOf(data.mode); } catch (RuntimeException e) { return; }
        var hand = com.hypixel.hytale.server.core.inventory.InventoryComponent.getItemInHand(store, ref);
        if (hand == null || !WorkplaceToolInteraction.isTool(hand.getItemId())) { close(); return; }
        var state = WorkplaceToolState.obtain(ref, store); state.choose(mode, ref, store);
        close();
    }
    public static final class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("Mode",Codec.STRING),(d,v)->d.mode=v,d->d.mode).add().build();
        String mode;
    }
}
