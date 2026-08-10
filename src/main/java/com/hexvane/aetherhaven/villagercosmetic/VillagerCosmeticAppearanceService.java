package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Merges town cosmetic overrides onto villager models and applies them to live entities. */
public final class VillagerCosmeticAppearanceService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private VillagerCosmeticAppearanceService() {}

    @Nullable
    public static Model buildModelWithOverrides(
        @Nonnull String modelAssetId,
        @Nullable Float modelScale,
        @Nonnull TownRecord town,
        @Nonnull String residentKey,
        @Nonnull VillagerCosmeticCatalog catalog
    ) {
        return buildModelWithOverrides(
            modelAssetId,
            modelScale,
            town.getVillagerCosmeticOverridesForResident(residentKey),
            catalog
        );
    }

    @Nullable
    public static Model buildModelWithOverrides(
        @Nonnull String modelAssetId,
        @Nullable Float modelScale,
        @Nonnull Map<String, String> slotOverrides,
        @Nonnull VillagerCosmeticCatalog catalog
    ) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelAssetId);
        if (asset == null) {
            return null;
        }
        float scale = modelScale != null && modelScale > 0f ? modelScale : asset.generateRandomScale();
        Model base = Model.createScaledModel(asset, scale);
        if (base == null) {
            return null;
        }
        if (slotOverrides == null || slotOverrides.isEmpty()) {
            return base;
        }
        ModelAttachment[] merged = mergeAttachments(base.getAttachments(), slotOverrides, catalog);
        return copyWithAttachments(base, merged);
    }

    @Nonnull
    public static ModelAttachment[] mergeAttachments(
        @Nullable ModelAttachment[] source,
        @Nonnull Map<String, String> slotOverrides,
        @Nonnull VillagerCosmeticCatalog catalog
    ) {
        List<ModelAttachment> list = new ArrayList<>();
        if (source != null) {
            for (ModelAttachment a : source) {
                if (a != null) {
                    list.add(a);
                }
            }
        }
        Map<String, String> effective = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : slotOverrides.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String slot = e.getKey().trim();
            String cosmeticId = e.getValue().trim();
            if (slot.isEmpty() || cosmeticId.isEmpty()) {
                continue;
            }
            effective.put(slot, cosmeticId);
        }
        for (Map.Entry<String, String> e : effective.entrySet()) {
            String slot = e.getKey();
            String cosmeticId = e.getValue();
            list.removeIf(a -> catalog.belongsToSlot(slot, a.getModel()));
            if (VillagerCosmeticDefinition.DEFAULT_ID.equalsIgnoreCase(cosmeticId)) {
                continue;
            }
            VillagerCosmeticDefinition def = catalog.byId(cosmeticId);
            if (def == null) {
                continue;
            }
            list.add(new ModelAttachment(def.model(), def.texture(), null, null, 1.0));
        }
        return list.toArray(ModelAttachment[]::new);
    }

    @Nonnull
    public static Model copyWithAttachments(@Nonnull Model template, @Nonnull ModelAttachment[] attachments) {
        return new Model(
            template.getModelAssetId(),
            template.getScale(),
            template.getRandomAttachmentIds(),
            attachments,
            template.getBoundingBox(),
            template.getModel(),
            template.getTexture(),
            template.getGradientSet(),
            template.getGradientId(),
            template.getEyeHeight(),
            template.getCrouchOffset(),
            template.getSittingOffset(),
            template.getSleepingOffset(),
            template.getAnimationSetMap(),
            template.getCamera(),
            template.getLight(),
            template.getParticles(),
            template.getTrails(),
            template.getPhysicsValues(),
            template.getDetailBoxes(),
            template.getPhobia(),
            template.getPhobiaModelAssetId()
        );
    }

    /** Applies saved town cosmetics to a live NPC. Call from the world thread outside store tick processing. */
    public static void applySavedCosmetics(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        String residentKey = VillagerCosmeticKeys.resolve(npcRef, store);
        if (residentKey == null) {
            return;
        }
        Map<String, String> overrides = town.getVillagerCosmeticOverridesForResident(residentKey);
        String modelAssetId = resolveBaseModelAssetId(npcRef, store);
        if (modelAssetId == null || modelAssetId.isBlank()) {
            return;
        }
        Float scale = null;
        ModelComponent existing = store.getComponent(npcRef, ModelComponent.getComponentType());
        if (existing != null && existing.getModel() != null) {
            scale = existing.getModel().getScale();
        }
        Model merged =
            buildModelWithOverrides(modelAssetId, scale, overrides, plugin.getVillagerCosmeticCatalog());
        if (merged == null) {
            LOGGER.atWarning().log("Failed to build cosmetic model for %s on %s", residentKey, modelAssetId);
            return;
        }
        // Always write when overrides exist, or when clearing back to catalog defaults after a wardrobe save.
        applyModel(npcRef, store, merged);
    }

    public static void applyModel(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Model model
    ) {
        // Merged attachments live on ModelComponent. Model.toReference() / PersistentModel cannot store
        // custom attachments, so never resyncFromPersistentModel after this or the look is wiped.
        store.putComponent(npcRef, ModelComponent.getComponentType(), new ModelComponent(model));
        store.putComponent(npcRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
    }

    @Nullable
    public static String resolveBaseModelAssetId(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store
    ) {
        TownsfolkCharacterBinding tb = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
        if (tb != null) {
            String id = tb.getModelAssetId();
            if (id != null && !id.isBlank()) {
                return id.trim();
            }
        }
        ModelComponent mc = store.getComponent(npcRef, ModelComponent.getComponentType());
        if (mc != null && mc.getModel() != null) {
            String id = mc.getModel().getModelAssetId();
            if (id != null && !id.isBlank()) {
                return id.trim();
            }
        }
        PersistentModel persistent = store.getComponent(npcRef, PersistentModel.getComponentType());
        if (persistent != null && persistent.getModelReference() != null) {
            String id = persistent.getModelReference().getModelAssetId();
            if (id != null && !id.isBlank()) {
                return id.trim();
            }
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc != null && npc.getRole() != null) {
            String appearance = npc.getRole().getAppearanceName();
            if (appearance != null && !appearance.isBlank()) {
                return appearance.trim();
            }
        }
        return null;
    }

    @Nullable
    public static Float resolveModelScale(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        ModelComponent mc = store.getComponent(npcRef, ModelComponent.getComponentType());
        if (mc != null && mc.getModel() != null) {
            return mc.getModel().getScale();
        }
        return null;
    }

    @Nonnull
    public static Map<String, String> normalizeOverrides(@Nonnull Map<String, String> draft) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : draft.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String slot = e.getKey().trim();
            String id = e.getValue().trim();
            if (slot.isEmpty() || id.isEmpty() || VillagerCosmeticDefinition.DEFAULT_ID.equalsIgnoreCase(id)) {
                continue;
            }
            out.put(slot, id);
        }
        return out;
    }

    public static boolean hasAnyOverride(@Nonnull TownRecord town, @Nonnull String residentKey) {
        return !town.getVillagerCosmeticOverridesForResident(residentKey).isEmpty();
    }
}
