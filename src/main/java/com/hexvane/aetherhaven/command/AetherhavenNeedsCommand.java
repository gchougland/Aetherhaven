package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.VillagerNeedsTargetResolver;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

public final class AetherhavenNeedsCommand extends AbstractCommandCollection {
    public AetherhavenNeedsCommand() {
        super("needs", "server.commands.aetherhaven.needs.desc");
        this.addSubCommand(new InspectCommand());
        this.addSubCommand(new SetCommand());
    }

    private static final class InspectCommand extends AbstractPlayerCommand {
        InspectCommand() {
            super("inspect", "server.commands.aetherhaven.needs.inspect.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(
                () -> {
                    String lang = playerRef.getLanguage() != null ? playerRef.getLanguage() : "en-US";
                    String resolvedNo =
                        I18nModule.get().getMessage(lang, "server.aetherhaven.debug.needs.noHandle");
                    final String noHandle =
                        resolvedNo != null && !resolvedNo.isEmpty() ? resolvedNo : "(no handle)";
                    playerRef.sendMessage(
                        Message.translation("server.aetherhaven.debug.needs.inspectIntro")
                    );
                    es.forEachChunk(
                        Query.and(VillagerNeeds.getComponentType(), UUIDComponent.getComponentType()),
                        (archetypeChunk, commandBuffer) -> {
                            for (int i = 0; i < archetypeChunk.size(); i++) {
                                VillagerNeeds vn = archetypeChunk.getComponent(i, VillagerNeeds.getComponentType());
                                UUIDComponent id = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                                if (vn == null || id == null) {
                                    continue;
                                }
                                AetherhavenVillagerHandle h = archetypeChunk.getComponent(
                                    i,
                                    AetherhavenVillagerHandle.getComponentType()
                                );
                                String handleStr =
                                    h != null && !h.getHandle().isEmpty() ? h.getHandle() : noHandle;
                                playerRef.sendMessage(
                                    Message.translation("server.aetherhaven.debug.needs.inspectLine")
                                        .param("handle", handleStr)
                                        .param("uuid", id.getUuid().toString())
                                        .param("hunger", String.format(Locale.US, "%.1f", vn.getHunger()))
                                        .param("energy", String.format(Locale.US, "%.1f", vn.getEnergy()))
                                        .param("fun", String.format(Locale.US, "%.1f", vn.getFun()))
                                        .param("max", String.valueOf((int) VillagerNeeds.MAX))
                                );
                            }
                        }
                    );
                }
            );
        }
    }

    private static final class SetCommand extends AbstractPlayerCommand {
        @Nonnull
        private final RequiredArg<String> targetArg =
            this.withRequiredArg("target", "server.commands.aetherhaven.needs.target.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<String> whichArg =
            this.withRequiredArg("which", "server.commands.aetherhaven.needs.which.desc", ArgTypes.STRING);
        @Nonnull
        private final RequiredArg<Float> valueArg =
            this.withRequiredArg("value", "server.commands.aetherhaven.needs.value.desc", ArgTypes.FLOAT);

        SetCommand() {
            super("set", "server.commands.aetherhaven.needs.set.desc");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            if (!AetherhavenDebugUtil.requireDebug(plugin, playerRef)) {
                return;
            }
            String targetToken = context.get(targetArg).trim();
            String which = context.get(whichArg).trim().toLowerCase();
            float v = Math.max(0f, Math.min(VillagerNeeds.MAX, context.get(valueArg)));
            if (!which.equals("hunger") && !which.equals("energy") && !which.equals("fun")) {
                playerRef.sendMessage(
                    Message.translation("server.aetherhaven.debug.needs.whichInvalid")
                        .param("max", String.valueOf((int) VillagerNeeds.MAX))
                );
                return;
            }
            Store<EntityStore> es = world.getEntityStore().getStore();
            world.execute(
                () -> {
                    VillagerNeedsTargetResolver.Result res =
                        VillagerNeedsTargetResolver.resolve(targetToken, store, ref, world, plugin, es);
                    switch (res.problem()) {
                        case NO_TOWN -> {
                            playerRef.sendMessage(Message.translation("server.aetherhaven.debug.needs.elderNoTown"));
                            return;
                        }
                        case NO_ELDER -> {
                            playerRef.sendMessage(Message.translation("server.aetherhaven.debug.needs.noElderUuid"));
                            return;
                        }
                        case NOT_FOUND -> {
                            playerRef.sendMessage(Message.translation("server.aetherhaven.debug.needs.noMatch"));
                            return;
                        }
                        case AMBIGUOUS -> {
                            playerRef.sendMessage(Message.translation("server.aetherhaven.debug.needs.multipleMatch"));
                            return;
                        }
                        default -> {
                        }
                    }
                    UUID target = res.entityUuid();
                    if (target == null) {
                        return;
                    }
                    AtomicReference<Ref<EntityStore>> foundRef = new AtomicReference<>();
                    AtomicReference<VillagerNeeds> updated = new AtomicReference<>();
                    es.forEachChunk(
                        Query.and(VillagerNeeds.getComponentType(), UUIDComponent.getComponentType()),
                        (archetypeChunk, commandBuffer) -> {
                            if (foundRef.get() != null) {
                                return;
                            }
                            for (int i = 0; i < archetypeChunk.size(); i++) {
                                UUIDComponent id = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                                if (id == null || !target.equals(id.getUuid())) {
                                    continue;
                                }
                                VillagerNeeds vn = archetypeChunk.getComponent(i, VillagerNeeds.getComponentType());
                                if (vn == null) {
                                    continue;
                                }
                                VillagerNeeds clone = (VillagerNeeds) vn.clone();
                                if (clone == null) {
                                    continue;
                                }
                                switch (which) {
                                    case "hunger" -> clone.setHunger(v);
                                    case "energy" -> clone.setEnergy(v);
                                    case "fun" -> clone.setFun(v);
                                }
                                foundRef.set(archetypeChunk.getReferenceTo(i));
                                updated.set(clone);
                                return;
                            }
                        }
                    );
                    if (foundRef.get() == null || updated.get() == null) {
                        playerRef.sendMessage(
                            Message.translation("server.aetherhaven.debug.needs.entityNotLoaded")
                                .param("id", String.valueOf(target))
                        );
                        return;
                    }
                    es.putComponent(foundRef.get(), VillagerNeeds.getComponentType(), updated.get());
                    playerRef.sendMessage(
                        Message.translation("server.aetherhaven.debug.needs.set")
                            .param("which", which)
                            .param("value", String.valueOf(v))
                            .param("label", targetToken)
                            .param("id", String.valueOf(target))
                    );
                }
            );
        }
    }
}
