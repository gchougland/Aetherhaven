package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService;
import com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticCatalog;
import com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticDefinition;
import com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticPreviewSession;
import com.hexvane.aetherhaven.villagercosmetic.WardrobeResidentDirectory;
import com.hexvane.aetherhaven.villagercosmetic.WardrobeResidentDirectory.WardrobeResidentRow;
import com.hexvane.aetherhaven.villager.NpcModelSpawnUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Slot picker + world preview for one villager's cosmetics. */
public final class VillagerWardrobeCustomizePage extends AetherhavenInteractiveCustomUIPage<VillagerWardrobeCustomizePage.PageData> {
    private static final String SLOTS = "#SlotSections";
    private static final String LANG = "aetherhaven_villager_cosmetics.aetherhaven.ui.villagerWardrobe";

    private final UUID townId;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final UUID entityUuid;
    private final String residentKey;
    private final String displayName;
    private final String modelAssetId;
    private final Map<String, String> draftOverrides = new LinkedHashMap<>();
    private final VillagerCosmeticPreviewSession previewSession = new VillagerCosmeticPreviewSession();
    private boolean previewStarted;
    private boolean draftLoaded;

    public VillagerWardrobeCustomizePage(
        @Nonnull PlayerRef playerRef,
        @Nonnull UUID townId,
        int blockX,
        int blockY,
        int blockZ,
        @Nonnull UUID entityUuid,
        @Nonnull String residentKey,
        @Nonnull String displayName,
        @Nonnull String modelAssetId
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.townId = townId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.entityUuid = entityUuid;
        this.residentKey = residentKey;
        this.displayName = displayName;
        this.modelAssetId = modelAssetId != null ? modelAssetId : "";
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        // rebuild() clears the client tree, so the template must be appended on every build.
        commandBuilder.append("Aetherhaven/VillagerWardrobeCustomize.ui");
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ApplyToAllButton",
            EventData.of("Action", "ApplyToAll"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveButton",
            EventData.of("Action", "Save"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#WardrobeBackButton",
            EventData.of("Action", "Back"),
            false
        );
        commandBuilder.set("#WardrobeTitleText.TextSpans", Message.translation(LANG + ".customizeTitle"));
        commandBuilder.set("#ResidentName.TextSpans", Message.raw(displayName));
        commandBuilder.set("#Hint.TextSpans", Message.translation(LANG + ".customizeHint"));
        commandBuilder.set("#ApplyToAllButton.TextSpans", Message.translation(LANG + ".applyToAll"));
        commandBuilder.set("#SaveButton.TextSpans", Message.translation(LANG + ".save"));
        commandBuilder.set("#WardrobeBackButton.TextSpans", Message.translation(LANG + ".back"));

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        commandBuilder.clear(SLOTS);
        if (plugin == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        if (!draftLoaded) {
            draftOverrides.clear();
            draftOverrides.putAll(town.getVillagerCosmeticOverridesForResident(residentKey));
            draftLoaded = true;
        }
        VillagerCosmeticCatalog catalog = plugin.getVillagerCosmeticCatalog();
        Set<String> unlocked = town.getUnlockedVillagerCosmeticIds();
        Set<String> slots = catalog.slotsWithUnlocks(unlocked);
        if (slots.isEmpty()) {
            commandBuilder.set("#Hint.TextSpans", Message.translation(LANG + ".noUnlocks"));
        }
        int slotIndex = 0;
        for (String slot : slots) {
            commandBuilder.append(SLOTS, "Aetherhaven/VillagerWardrobeSlotSection.ui");
            String section = SLOTS + "[" + slotIndex + "]";
            List<Choice> choices = choicesForSlot(catalog, unlocked, slot);
            Choice selected = selectedChoice(choices, draftOverrides.get(slot));
            commandBuilder.set(section + " #SlotTitle.TextSpans", slotTitle(slot));
            commandBuilder.set(section + " #CosmeticName.TextSpans", selected.label());
            boolean canCycle = choices.size() > 1;
            commandBuilder.set(section + " #PrevCosmetic.Disabled", !canCycle);
            commandBuilder.set(section + " #NextCosmetic.Disabled", !canCycle);
            if (canCycle) {
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    section + " #PrevCosmetic",
                    new EventData().append("Action", "CyclePrev").append("Slot", slot),
                    false
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    section + " #NextCosmetic",
                    new EventData().append("Action", "CycleNext").append("Slot", slot),
                    false
                );
            }
            slotIndex++;
        }

        if (!previewStarted) {
            previewStarted = true;
            Model model = buildPreviewModel(plugin, town, store);
            if (model != null) {
                previewSession.begin(ref, store, playerRef, blockX, blockY, blockZ, model);
            }
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        if (data.action == null) {
            return;
        }
        String action = data.action.trim();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        World world = store.getExternalData().getWorld();
        if (plugin == null || world == null) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            return;
        }
        switch (action) {
            case "CyclePrev", "CycleNext" -> {
                if (data.slot == null) {
                    return;
                }
                String slot = data.slot.trim();
                int delta = "CycleNext".equals(action) ? 1 : -1;
                if (!cycleSlot(plugin, town, slot, delta)) {
                    return;
                }
                Model model = buildPreviewModel(plugin, town, store);
                if (model != null) {
                    previewSession.updateModel(store, model);
                }
                // Only refresh labels; full rebuild clears the page and breaks selectors.
                UICommandBuilder labelUpdate = new UICommandBuilder();
                refreshSlotLabels(labelUpdate, plugin.getVillagerCosmeticCatalog(), town.getUnlockedVillagerCosmeticIds());
                sendUpdate(labelUpdate, null, false);
            }
            case "Save" -> {
                town.replaceVillagerCosmeticOverrides(residentKey, draftOverrides);
                tm.updateTown(town);
                applyToLiveNpc(store, town);
                NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.translation(LANG + ".saved"),
                    NotificationStyle.Success
                );
                UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WORKBENCH_CRAFT);
                goBack(ref, store);
            }
            case "ApplyToAll" -> {
                Map<String, String> overrides =
                    new LinkedHashMap<>(VillagerCosmeticAppearanceService.normalizeOverrides(draftOverrides));
                world.execute(
                    () -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        TownRecord liveTown = tm.getTown(townId);
                        if (liveTown == null) {
                            return;
                        }
                        applyToAllResidents(store, liveTown, plugin, overrides);
                        tm.updateTown(liveTown);
                        VillagerCosmeticAppearanceService.refreshWardrobeNpcs(world, store, liveTown, plugin);
                        NotificationUtil.sendNotification(
                            playerRef.getPacketHandler(),
                            Message.translation(LANG + ".applyToAllSuccess"),
                            NotificationStyle.Success
                        );
                        UiSoundEffects.play2dUi(ref, store, AetherhavenConstants.SFX_WORKBENCH_CRAFT);
                    }
                );
            }
            case "Back" -> goBack(ref, store);
            default -> {}
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        previewSession.cleanup(ref, store, playerRef);
        super.onDismiss(ref, store);
    }

    private void refreshSlotLabels(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull VillagerCosmeticCatalog catalog,
        @Nonnull Set<String> unlocked
    ) {
        int slotIndex = 0;
        for (String slot : catalog.slotsWithUnlocks(unlocked)) {
            String section = SLOTS + "[" + slotIndex + "]";
            List<Choice> choices = choicesForSlot(catalog, unlocked, slot);
            Choice selected = selectedChoice(choices, draftOverrides.get(slot));
            commandBuilder.set(section + " #CosmeticName.TextSpans", selected.label());
            slotIndex++;
        }
    }

    private boolean cycleSlot(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull String slot,
        int delta
    ) {
        VillagerCosmeticCatalog catalog = plugin.getVillagerCosmeticCatalog();
        List<Choice> choices = choicesForSlot(catalog, town.getUnlockedVillagerCosmeticIds(), slot);
        if (choices.size() <= 1) {
            return false;
        }
        int index = indexOfChoice(choices, draftOverrides.get(slot));
        int next = Math.floorMod(index + delta, choices.size());
        String cosmeticId = choices.get(next).id();
        if (VillagerCosmeticDefinition.DEFAULT_ID.equalsIgnoreCase(cosmeticId)) {
            draftOverrides.remove(slot);
        } else {
            draftOverrides.put(slot, cosmeticId);
        }
        return true;
    }

    @Nonnull
    private static List<Choice> choicesForSlot(
        @Nonnull VillagerCosmeticCatalog catalog,
        @Nonnull Set<String> unlocked,
        @Nonnull String slot
    ) {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(VillagerCosmeticDefinition.DEFAULT_ID, Message.translation(LANG + ".defaultLook")));
        for (VillagerCosmeticDefinition def : catalog.unlockedInSlots(unlocked, slot)) {
            choices.add(new Choice(def.id(), Message.translation(def.displayNameKey())));
        }
        return choices;
    }

    @Nonnull
    private static Choice selectedChoice(@Nonnull List<Choice> choices, @Nullable String selectedId) {
        int index = indexOfChoice(choices, selectedId);
        return choices.get(index);
    }

    private static int indexOfChoice(@Nonnull List<Choice> choices, @Nullable String selectedId) {
        if (selectedId == null || selectedId.isBlank()
            || VillagerCosmeticDefinition.DEFAULT_ID.equalsIgnoreCase(selectedId)) {
            return 0;
        }
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).id().equalsIgnoreCase(selectedId)) {
                return i;
            }
        }
        return 0;
    }

    private void goBack(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        previewSession.cleanup(ref, store, playerRef);
        previewStarted = false;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            close();
            return;
        }
        player
            .getPageManager()
            .openCustomPage(
                ref,
                store,
                new VillagerWardrobeResidentPage(playerRef, townId, blockX, blockY, blockZ)
            );
    }

    private void applyToAllResidents(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Map<String, String> overrides
    ) {
        for (WardrobeResidentRow row : WardrobeResidentDirectory.list(store, town, plugin)) {
            town.replaceVillagerCosmeticOverrides(row.residentKey(), overrides);
        }
    }

    private void applyToLiveNpc(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        World world = store.getExternalData().getWorld();
        if (world == null) {
            return;
        }
        final UUID targetUuid = entityUuid;
        world.execute(
            () -> {
                Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(targetUuid);
                if (npcRef == null || !npcRef.isValid()) {
                    return;
                }
                VillagerCosmeticAppearanceService.applySavedCosmetics(npcRef, store, town);
            }
        );
    }

    @Nullable
    private String resolveModelAssetId(@Nonnull AetherhavenPlugin plugin) {
        if (modelAssetId != null && !modelAssetId.isBlank()) {
            return modelAssetId.trim();
        }
        if (residentKey.startsWith("character:")) {
            String characterId = residentKey.substring("character:".length());
            TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
            if (def != null) {
                return def.getModelAssetId();
            }
        }
        return null;
    }

    /**
     * Builds the preview the same way the world does: from the villager's live model, so the menu shows the villager
     * the player is standing in front of rather than a freshly rolled one.
     */
    @Nullable
    private Model buildPreviewModel(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> liveRef = store.getExternalData().getRefFromUUID(entityUuid);
        if (liveRef != null && !liveRef.isValid()) {
            liveRef = null;
        }
        Model currentModel = null;
        if (liveRef != null) {
            ModelComponent mc = store.getComponent(liveRef, ModelComponent.getComponentType());
            currentModel = mc != null ? mc.getModel() : null;
        }
        String assetId = resolveModelAssetId(plugin);
        if ((assetId == null || assetId.isBlank()) && liveRef != null) {
            assetId = VillagerCosmeticAppearanceService.resolveBaseModelAssetId(liveRef, store);
        }
        if (assetId == null || assetId.isBlank()) {
            return null;
        }
        Float scale = currentModel != null && currentModel.getScale() > 0f ? currentModel.getScale() : null;
        Map<String, String> overrides = VillagerCosmeticAppearanceService.normalizeOverrides(draftOverrides);
        Model merged =
            VillagerCosmeticAppearanceService.buildModelWithOverrides(
                assetId,
                scale,
                overrides,
                plugin.getVillagerCosmeticCatalog(),
                currentModel
            );
        if (merged != null) {
            return merged;
        }
        return NpcModelSpawnUtil.buildScaledModel(assetId, scale);
    }

    @Nonnull
    private static Message slotTitle(@Nonnull String slot) {
        String key = LANG + ".slot." + slot.toLowerCase(Locale.ROOT);
        return Message.translation(key);
    }

    private record Choice(@Nonnull String id, @Nonnull Message label) {}

    public static final class PageData {
        @Nullable
        public String action;
        @Nullable
        public String slot;

        @Nonnull
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (o, v) -> o.action = v, o -> o.action)
                .add()
                .append(new KeyedCodec<>("Slot", Codec.STRING), (o, v) -> o.slot = v, o -> o.slot)
                .add()
                .build();
    }
}
