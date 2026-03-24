package com.hexvane.aetherhaven.npc;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueResolver;
import com.hexvane.aetherhaven.dialogue.DialogueWorldView;
import com.hexvane.aetherhaven.ui.DialoguePage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ActionOpenAetherhavenDialogue extends ActionBase {
    @Nullable
    private final String dialogueId;
    @Nonnull
    private final String villagerKind;

    public ActionOpenAetherhavenDialogue(@Nonnull BuilderActionOpenAetherhavenDialogue builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.dialogueId = builder.dialogueId;
        this.villagerKind = builder.villagerKind != null && !builder.villagerKind.isBlank()
            ? builder.villagerKind.trim()
            : "test_villager";
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, role, sensorInfo, dt, store) && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);
        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) {
            return false;
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return false;
        }
        Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
        if (playerComponent == null) {
            return false;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        DialogueCatalog catalog = plugin.getDialogueCatalog();
        DialogueResolver resolver = plugin.getDialogueResolver();
        DialogueResolver.ResolvedDialogue resolved = resolver.resolve(this.dialogueId, this.villagerKind, ref, playerRef, store);
        DialogueWorldView worldView = plugin.getDialogueWorldView();
        Ref<EntityStore> npcRef = ref;
        Ref<EntityStore> playerEntityRef = playerRef;
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!playerEntityRef.isValid()) {
                    return;
                }
                Player player = store.getComponent(playerEntityRef, Player.getComponentType());
                if (player == null) {
                    return;
                }
                PlayerRef pr = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
                if (pr == null) {
                    return;
                }
                player.getPageManager()
                    .openCustomPage(
                        playerEntityRef,
                        store,
                        new DialoguePage(pr, catalog, worldView, resolved.treeId(), resolved.entryNodeId(), npcRef)
                    );
            }
        );
        return true;
    }
}
