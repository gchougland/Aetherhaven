package com.hexvane.aetherhaven.villagercosmetic;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Merges town cosmetic overrides onto villager models and applies them to live entities. */
public final class VillagerCosmeticAppearanceService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String HAIRCUT_MODEL_PREFIX = "Characters/Haircuts/";

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
        return buildModelWithOverrides(modelAssetId, modelScale, slotOverrides, catalog, null);
    }

    @Nullable
    public static Model buildModelWithOverrides(
        @Nonnull String modelAssetId,
        @Nullable Float modelScale,
        @Nonnull Map<String, String> slotOverrides,
        @Nonnull VillagerCosmeticCatalog catalog,
        @Nullable Model currentModel
    ) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelAssetId);
        if (asset == null) {
            return null;
        }
        float scale = resolveScale(asset, modelScale, currentModel);
        Map<String, String> randomAttachmentIds = baseLookIds(modelAssetId, currentModel);
        Model base =
            randomAttachmentIds != null
                ? Model.createScaledModel(asset, scale, randomAttachmentIds)
                : Model.createScaledModel(asset, scale);
        if (base == null) {
            return null;
        }
        // The base look always comes from the model asset, never from the live entity: the asset carries the hats and
        // accessories authored in the model JSON, while the live attachments carry whatever cosmetic was applied last.
        // Merging onto the asset is what makes "Default" in every slot put the villager back the way they spawned.
        ModelAttachment[] baseLook = orEmpty(base.getAttachments());
        if (slotOverrides == null || slotOverrides.isEmpty()) {
            return copyWithAttachments(base, baseLook);
        }
        return copyWithAttachments(base, mergeAttachments(baseLook, slotOverrides, catalog));
    }

    private static float resolveScale(
        @Nonnull ModelAsset asset,
        @Nullable Float modelScale,
        @Nullable Model currentModel
    ) {
        if (modelScale != null && modelScale > 0f) {
            return modelScale;
        }
        if (currentModel != null && currentModel.getScale() > 0f) {
            return currentModel.getScale();
        }
        return asset.generateRandomScale();
    }

    /** Random attachment ids of the live model, but only when it was built from the same asset. */
    @Nullable
    private static Map<String, String> baseLookIds(@Nonnull String modelAssetId, @Nullable Model currentModel) {
        if (currentModel == null || !modelAssetId.equalsIgnoreCase(currentModel.getModelAssetId())) {
            return null;
        }
        Map<String, String> ids = currentModel.getRandomAttachmentIds();
        return ids != null && !ids.isEmpty() ? ids : null;
    }

    @Nonnull
    private static ModelAttachment[] orEmpty(@Nullable ModelAttachment[] attachments) {
        return attachments != null ? attachments : new ModelAttachment[0];
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
        VillagerCosmeticDefinition headDef = null;
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
            String gradientSet = def.gradientSet().isBlank() ? null : def.gradientSet();
            String gradientId = def.gradientId().isBlank() ? null : def.gradientId();
            list.add(new ModelAttachment(def.model(), def.texture(), gradientSet, gradientId, 1.0));
            if (VillagerCosmeticDefinition.SLOT_HEAD_ACCESSORY.equalsIgnoreCase(slot)) {
                headDef = def;
            }
        }
        if (headDef != null) {
            applyHeadAccessoryHairRules(list, headDef.headAccessoryType());
        }
        return list.toArray(ModelAttachment[]::new);
    }

    /**
     * Matches player hat hair rules for NPC attachment lists: fully covering hides haircuts, half covering swaps
     * styles that require a generic base, simple leaves hair alone.
     */
    private static void applyHeadAccessoryHairRules(
        @Nonnull List<ModelAttachment> list,
        @Nonnull VillagerCosmeticHeadAccessoryType type
    ) {
        if (type == VillagerCosmeticHeadAccessoryType.Simple) {
            return;
        }
        if (type == VillagerCosmeticHeadAccessoryType.FullyCovering) {
            list.removeIf(a -> isHaircutModel(a.getModel()));
            return;
        }
        CosmeticRegistry registry = resolveCosmeticRegistry();
        if (registry == null) {
            return;
        }
        Map<String, PlayerSkinPart> haircuts = registry.getHaircuts();
        for (int i = 0; i < list.size(); i++) {
            ModelAttachment current = list.get(i);
            if (!isHaircutModel(current.getModel())) {
                continue;
            }
            PlayerSkinPart haircutPart = findHaircutByModel(haircuts, current.getModel());
            if (haircutPart == null
                || !haircutPart.doesRequireGenericHaircut()
                || haircutPart.getHairType() == null) {
                continue;
            }
            PlayerSkinPart generic = haircuts.get("Generic" + haircutPart.getHairType().name());
            if (generic == null || generic.getModel() == null || generic.getModel().isBlank()) {
                continue;
            }
            String texture =
                generic.getGreyscaleTexture() != null && !generic.getGreyscaleTexture().isBlank()
                    ? generic.getGreyscaleTexture()
                    : current.getTexture();
            String gradientSet =
                generic.getGradientSet() != null && !generic.getGradientSet().isBlank()
                    ? generic.getGradientSet()
                    : current.getGradientSet();
            list.set(
                i,
                new ModelAttachment(
                    generic.getModel(),
                    texture,
                    gradientSet,
                    current.getGradientId(),
                    current.getWeight()
                )
            );
        }
    }

    private static boolean isHaircutModel(@Nullable String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return false;
        }
        String path = modelPath.trim();
        return path.regionMatches(true, 0, HAIRCUT_MODEL_PREFIX, 0, HAIRCUT_MODEL_PREFIX.length());
    }

    @Nullable
    private static PlayerSkinPart findHaircutByModel(
        @Nonnull Map<String, PlayerSkinPart> haircuts,
        @Nonnull String modelPath
    ) {
        String needle = modelPath.trim();
        for (PlayerSkinPart part : haircuts.values()) {
            if (part == null || part.getModel() == null) {
                continue;
            }
            if (needle.equalsIgnoreCase(part.getModel().trim())) {
                return part;
            }
        }
        String stem = haircutStem(needle);
        if (stem.isEmpty()) {
            return null;
        }
        PlayerSkinPart byId = haircuts.get(stem);
        if (byId != null) {
            return byId;
        }
        for (Map.Entry<String, PlayerSkinPart> e : haircuts.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(stem)) {
                return e.getValue();
            }
        }
        return null;
    }

    @Nonnull
    private static String haircutStem(@Nonnull String modelPath) {
        String path = modelPath.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        String lower = file.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".blockymodel")) {
            file = file.substring(0, file.length() - ".blockymodel".length());
        }
        return file.trim();
    }

    @Nullable
    private static CosmeticRegistry resolveCosmeticRegistry() {
        try {
            CosmeticsModule module = CosmeticsModule.get();
            return module != null ? module.getRegistry() : null;
        } catch (Exception e) {
            LOGGER.atFine().withCause(e).log("Cosmetic registry unavailable for villager hair covering");
            return null;
        }
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
        ModelComponent existing = store.getComponent(npcRef, ModelComponent.getComponentType());
        Model currentModel = existing != null ? existing.getModel() : null;
        Float scale = currentModel != null ? currentModel.getScale() : null;
        Model merged =
            buildModelWithOverrides(
                modelAssetId,
                scale,
                overrides,
                plugin.getVillagerCosmeticCatalog(),
                currentModel
            );
        if (merged == null) {
            LOGGER.atWarning().log("Failed to build cosmetic model for %s on %s", residentKey, modelAssetId);
            return;
        }
        applyModel(npcRef, store, merged);
    }

    /**
     * Re-applies saved cosmetics to every live town NPC. Defers one frame so model packets flush after town data is saved.
     */
    public static void refreshAllTownBoundNpcs(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        List<Ref<EntityStore>> npcRefs = collectTownNpcRefs(store, town);
        world.execute(
            () -> {
                for (Ref<EntityStore> npcRef : npcRefs) {
                    if (npcRef != null && npcRef.isValid()) {
                        applySavedCosmetics(npcRef, store, town);
                    }
                }
            }
        );
    }

    /**
     * Every NPC the wardrobe covers: townsfolk, guards and tourists bound to the town, plus the festival NPCs standing
     * on the square, which carry no town binding of their own.
     */
    @Nonnull
    public static List<Ref<EntityStore>> collectTownNpcRefs(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        UUID townId = town.getTownId();
        Set<String> festivalNpcUuids = new HashSet<>();
        for (String raw : town.getActiveFestivalNpcEntityUuids()) {
            if (raw != null && !raw.isBlank()) {
                festivalNpcUuids.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<Ref<EntityStore>> refs = new ArrayList<>();
        Query<EntityStore> q = Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(
            q,
            (ArchetypeChunk<EntityStore> archetypeChunk, CommandBuffer<EntityStore> commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    TownVillagerBinding binding =
                        archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                    UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    boolean bound = binding != null && townId.equals(binding.getTownId());
                    boolean festival =
                        binding == null
                            && uc != null
                            && festivalNpcUuids.contains(uc.getUuid().toString().toLowerCase(Locale.ROOT));
                    if (!bound && !festival) {
                        continue;
                    }
                    Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                    if (npcRef != null && npcRef.isValid()) {
                        refs.add(npcRef);
                    }
                }
            }
        );
        return refs;
    }

    public static void applyModel(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull Model model
    ) {
        runStoreWrite(
            store,
            () -> {
                if (!npcRef.isValid()) {
                    return;
                }
                // Merged attachments live on ModelComponent. Model.toReference() / PersistentModel cannot store
                // custom attachments, so never resyncFromPersistentModel after this or the look is wiped.
                store.putComponent(npcRef, ModelComponent.getComponentType(), new ModelComponent(model));
                store.putComponent(npcRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (npc != null && npc.getRole() != null) {
                    npc.getRole().updateMotionControllers(npcRef, model, model.getBoundingBox(), store);
                }
            }
        );
    }

    @SuppressWarnings("deprecation") // Store.isProcessing() is the only way to detect mid-tick writes
    private static void runStoreWrite(@Nonnull Store<EntityStore> store, @Nonnull Runnable write) {
        if (store.isProcessing()) {
            World world = store.getExternalData().getWorld();
            if (world != null) {
                world.execute(write);
            }
        } else {
            write.run();
        }
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
