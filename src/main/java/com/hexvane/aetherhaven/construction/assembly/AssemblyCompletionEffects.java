package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Quest-style event title + banner sting when passive/staff assembly finishes. */
public final class AssemblyCompletionEffects {
    private AssemblyCompletionEffects() {}

    public static void tryNotifyFinisher(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull UUID finisherUuid,
        @Nonnull PlotInstance plot
    ) {
        Ref<EntityStore> playerEntityRef = entityStore.getExternalData().getRefFromUUID(finisherUuid);
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }
        PlayerRef pr = entityStore.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        String buildName = def != null ? def.getDisplayName() : plot.getConstructionId();
        EventTitleUtil.showEventTitleToPlayer(
            pr,
            Message.translation("aetherhaven_misc.aetherhaven.banner.assembly.complete.secondary").param("name", buildName),
            Message.translation("aetherhaven_misc.aetherhaven.banner.assembly.complete.primary"),
            true,
            null,
            4.0F,
            0.7F,
            0.9F
        );
        int sfx = SoundEvent.getAssetMap().getIndex(AetherhavenConstants.EVENT_TITLE_SHORT_SUCCESS_SOUND_ID);
        if (sfx != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2d(playerEntityRef, sfx, SoundCategory.UI, entityStore);
        }
        pr.sendMessage(Message.translation("aetherhaven_misc.aetherhaven.assembly.complete.chat").param("name", buildName));
    }
}
