package com.hexvane.aetherhaven.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinGradientSet;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPartTexture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds a minimal model JSON (Parent + DefaultAttachments) from a {@link PlayerSkin} by resolving
 * each slot through {@link CosmeticRegistry}, matching {@code CosmeticsModule} validation rules.
 *
 * <p>Haircut + hat: matches {@link com.hypixel.hytale.server.core.cosmetics.CosmeticsModule#isValidHaircutAttachment}
 * a {@link PlayerSkinPart.HeadAccessoryType#HalfCovering} head accessory with a haircut that
 * {@link PlayerSkinPart#doesRequireGenericHaircut()} exports as {@code Generic{HairType}} plus the same hair
 * gradient id as the chosen style (see server validation using {@code Generic} + {@link PlayerSkinPart#getHairType()}).
 *
 * <p>Attachment order (inner → outer draw order): body, underwear, skin feature, face features,
 * hair, lower body, tops, footwear, gloves, head/face/ear accessories, cape.
 *
 * <p><b>Important:</b> {@link com.hypixel.hytale.server.core.cosmetics.CosmeticsModule#createModel(com.hypixel.hytale.protocol.PlayerSkin)}
 * does <strong>not</strong> apply the skin — it only validates and returns {@code Model.createScaledModel(Player)}.
 * Clothing and other cosmetics must be resolved from the registry (this class), not from {@code createModel}.
 *
 * <p>Body characteristics that use the base {@code Player} mesh still export when they use a skin gradient or
 * texture tint (e.g. orc tones); they must not be dropped from {@code DefaultAttachments}.
 */
public final class PlayerSkinModelExporter {
    private static final String PARENT_PLAYER = "Player";
    private static final String HAIR_GRADIENT_SET_ID = "Hair";

    private PlayerSkinModelExporter() {}

    @Nonnull
    public static JsonObject toModelJson(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        JsonArray attachments = new JsonArray();
        for (ModelAttachment ma : toModelAttachments(skin, registry)) {
            attachments.add(modelAttachmentToJson(ma));
        }
        JsonObject root = new JsonObject();
        root.addProperty("Parent", PARENT_PLAYER);
        root.add("DefaultAttachments", attachments);
        return root;
    }

    /**
     * Resolves every equipped cosmetic slot into engine {@link ModelAttachment}s (same rules as JSON export). Use this
     * to build a server-side {@link com.hypixel.hytale.server.core.asset.type.model.config.Model} with the placer's
     * full silhouette — {@link com.hypixel.hytale.server.core.cosmetics.CosmeticsModule#createModel(com.hypixel.hytale.protocol.PlayerSkin)}
     * does not include skin-specific attachments.
     */
    @Nonnull
    public static ModelAttachment[] toModelAttachments(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        List<ModelAttachment> list = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String raw =
                slot == Slot.HAIRCUT ? effectiveHaircutIdForHeadAccessory(skin, registry) : slot.getter.apply(skin);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ModelAttachment one = resolveSlot(slot.label, raw, slot.map.apply(registry), registry, skin);
            if (one != null) {
                list.add(one);
            }
        }
        return list.toArray(ModelAttachment[]::new);
    }

    @Nonnull
    private static JsonObject modelAttachmentToJson(@Nonnull ModelAttachment a) {
        JsonObject o = new JsonObject();
        o.addProperty("Model", a.getModel());
        o.addProperty("Texture", a.getTexture());
        String gs = a.getGradientSet();
        if (gs != null && !gs.isEmpty()) {
            o.addProperty("GradientSet", gs);
            o.addProperty("GradientId", a.getGradientId());
        }
        return o;
    }

    /**
     * @return null when the slot is skipped (e.g. body uses base player mesh only).
     */
    @Nullable
    private static ModelAttachment resolveSlot(
        @Nonnull String slotLabel,
        @Nonnull String id,
        @Nonnull Map<String, PlayerSkinPart> map,
        @Nonnull CosmeticRegistry registry,
        @Nonnull PlayerSkin skin
    ) {
        String[] idParts = id.split("\\.");
        PlayerSkinPart part = map.get(idParts[0]);
        if (part == null) {
            throw new IllegalArgumentException("Unknown " + slotLabel + " asset id: " + idParts[0] + " (full id: " + id + ")");
        }
        String variantId = idParts.length > 2 && !idParts[2].isEmpty() ? idParts[2] : null;
        if (part.getVariants() != null) {
            if (variantId == null) {
                throw new IllegalArgumentException(slotLabel + " requires a variant segment (assetId.selector.variantId): " + id);
            }
            if (!part.getVariants().containsKey(variantId)) {
                throw new IllegalArgumentException(slotLabel + " unknown variant '" + variantId + "' for id: " + id);
            }
        } else {
            variantId = null;
        }

        final String selector;
        if (idParts.length >= 2) {
            selector = idParts[1];
            if (selector.isEmpty()) {
                throw new IllegalArgumentException(slotLabel + " empty selector in id: " + id);
            }
        } else if (CosmeticRegistry.SKIN_GRADIENTSET_ID.equals(part.getGradientSet())) {
            String inherited = inheritSkinGradientSelector(skin, registry);
            if (inherited == null) {
                throw new IllegalArgumentException(
                    slotLabel
                        + " id has no skin tone ("
                        + id
                        + "). Use AssetId.tone (e.g. Face_Tired_Eyes.02) or set body/underwear with a Skin gradient (e.g. Default.02)."
                );
            }
            selector = inherited;
        } else if (HAIR_GRADIENT_SET_ID.equals(part.getGradientSet())) {
            String inherited = inheritHairGradientSelector(skin, registry);
            if (inherited == null) {
                throw new IllegalArgumentException(
                    slotLabel
                        + " id has no hair color ("
                        + id
                        + "). Use AssetId.color (e.g. Morning.Brown) or set eyebrows/facial hair with a Hair gradient."
                );
            }
            selector = inherited;
        } else {
            throw new IllegalArgumentException(
                slotLabel + " id must include a selector after the asset id (e.g. BodyId.gradientOrTexture): " + id
            );
        }

        String modelPath;
        String greyscale;
        Map<String, PlayerSkinPartTexture> textureMap;
        if (variantId != null) {
            PlayerSkinPart.Variant variant = Objects.requireNonNull(part.getVariants()).get(variantId);
            modelPath = variant.getModel();
            greyscale = variant.getGreyscaleTexture();
            textureMap = variant.getTextures();
        } else {
            modelPath = part.getModel();
            greyscale = part.getGreyscaleTexture();
            textureMap = part.getTextures();
        }

        if (modelPath == null || modelPath.isEmpty()) {
            throw new IllegalArgumentException(slotLabel + " missing model path for id: " + id);
        }

        boolean gradientMatch = false;
        if (part.getGradientSet() != null) {
            PlayerSkinGradientSet gradientSet = registry.getGradientSets().get(part.getGradientSet());
            if (gradientSet != null && gradientSet.getGradients().containsKey(selector)) {
                gradientMatch = true;
            }
        }

        if (gradientMatch) {
            if (greyscale == null || greyscale.isEmpty()) {
                throw new IllegalArgumentException(slotLabel + " gradient part missing GreyscaleTexture for id: " + id);
            }
            return new ModelAttachment(
                modelPath,
                greyscale,
                Objects.requireNonNull(part.getGradientSet()),
                selector,
                1.0
            );
        }

        PlayerSkinPartTexture textureEntry = textureMap != null ? textureMap.get(selector) : null;
        if (textureEntry == null) {
            throw new IllegalArgumentException(
                slotLabel + " unknown texture/gradient key '" + selector + "' for id: " + id
            );
        }
        String texPath = textureEntry.getTexture();
        if (texPath == null || texPath.isEmpty()) {
            throw new IllegalArgumentException(slotLabel + " empty texture path for id: " + id);
        }
        return new ModelAttachment(modelPath, texPath, null, null, 1.0);
    }

    /**
     * Auth / clients sometimes store only the face (or similar) asset id without a tone segment; tone still matches
     * {@link CosmeticRegistry#SKIN_GRADIENTSET_ID} on body and underwear.
     */
    @Nullable
    private static String inheritSkinGradientSelector(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        PlayerSkinGradientSet skinGradients = registry.getGradientSets().get(CosmeticRegistry.SKIN_GRADIENTSET_ID);
        if (skinGradients == null) {
            return null;
        }
        for (String raw : new String[] { skin.bodyCharacteristic, skin.underwear }) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String[] p = raw.split("\\.");
            if (p.length >= 2 && !p[1].isEmpty() && skinGradients.getGradients().containsKey(p[1])) {
                return p[1];
            }
        }
        return null;
    }

    /**
     * Second segment from eyebrows, facial hair, or haircut when it is a valid {@link #HAIR_GRADIENT_SET_ID} key.
     */
    @Nullable
    private static String inheritHairGradientSelector(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        PlayerSkinGradientSet hairGradients = registry.getGradientSets().get(HAIR_GRADIENT_SET_ID);
        if (hairGradients == null) {
            return null;
        }
        for (String raw : new String[] { skin.eyebrows, skin.facialHair, skin.haircut }) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String[] p = raw.split("\\.");
            if (p.length >= 2 && !p[1].isEmpty() && hairGradients.getGradients().containsKey(p[1])) {
                return p[1];
            }
        }
        return null;
    }

    /**
     * Mirrors {@code CosmeticsModule.isValidHaircutAttachment}: half-covering hats force {@code Generic{HairType}}
     * for haircuts that require it, keeping the same hair gradient id as the stored style.
     */
    @Nullable
    private static String effectiveHaircutIdForHeadAccessory(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        String haircutId = skin.haircut;
        if (haircutId == null || haircutId.isEmpty()) {
            return null;
        }
        String headAccessoryId = skin.headAccessory;
        if (headAccessoryId == null || headAccessoryId.isEmpty()) {
            return haircutId;
        }
        Map<String, PlayerSkinPart> haircuts = registry.getHaircuts();
        String[] haircutParts = haircutId.split("\\.");
        String haircutAssetId = haircutParts[0];
        String haircutAssetTextureId =
            haircutParts.length > 1 && !haircutParts[1].isEmpty() ? haircutParts[1] : null;

        String[] accParts = headAccessoryId.split("\\.");
        PlayerSkinPart headAccessoryPart = registry.getHeadAccessories().get(accParts[0]);
        if (headAccessoryPart == null) {
            return haircutId;
        }

        if (headAccessoryPart.getHeadAccessoryType() == PlayerSkinPart.HeadAccessoryType.HalfCovering) {
            PlayerSkinPart haircutPart = haircuts.get(haircutAssetId);
            if (haircutPart != null
                && haircutPart.doesRequireGenericHaircut()
                && haircutPart.getHairType() != null) {
                PlayerSkinPart baseHaircutPart = haircuts.get("Generic" + haircutPart.getHairType().name());
                if (baseHaircutPart != null) {
                    String tone =
                        haircutAssetTextureId != null ? haircutAssetTextureId : inheritHairGradientSelector(skin, registry);
                    if (tone != null) {
                        return baseHaircutPart.getId() + "." + tone;
                    }
                }
            }
        }

        return haircutId;
    }

    private enum Slot {
        BODY("bodyCharacteristic", s -> s.bodyCharacteristic, CosmeticRegistry::getBodyCharacteristics),
        UNDERWEAR("underwear", s -> s.underwear, CosmeticRegistry::getUnderwear),
        SKIN_FEATURE("skinFeature", s -> s.skinFeature, CosmeticRegistry::getSkinFeatures),
        FACE("face", s -> s.face, CosmeticRegistry::getFaces),
        EARS("ears", s -> s.ears, CosmeticRegistry::getEars),
        MOUTH("mouth", s -> s.mouth, CosmeticRegistry::getMouths),
        EYES("eyes", s -> s.eyes, CosmeticRegistry::getEyes),
        EYEBROWS("eyebrows", s -> s.eyebrows, CosmeticRegistry::getEyebrows),
        FACIAL_HAIR("facialHair", s -> s.facialHair, CosmeticRegistry::getFacialHairs),
        HAIRCUT("haircut", s -> s.haircut, CosmeticRegistry::getHaircuts),
        PANTS("pants", s -> s.pants, CosmeticRegistry::getPants),
        OVERPANTS("overpants", s -> s.overpants, CosmeticRegistry::getOverpants),
        UNDERTOP("undertop", s -> s.undertop, CosmeticRegistry::getUndertops),
        OVERTOP("overtop", s -> s.overtop, CosmeticRegistry::getOvertops),
        SHOES("shoes", s -> s.shoes, CosmeticRegistry::getShoes),
        GLOVES("gloves", s -> s.gloves, CosmeticRegistry::getGloves),
        HEAD_ACCESSORY("headAccessory", s -> s.headAccessory, CosmeticRegistry::getHeadAccessories),
        FACE_ACCESSORY("faceAccessory", s -> s.faceAccessory, CosmeticRegistry::getFaceAccessories),
        EAR_ACCESSORY("earAccessory", s -> s.earAccessory, CosmeticRegistry::getEarAccessories),
        CAPE("cape", s -> s.cape, CosmeticRegistry::getCapes);

        final String label;
        final Function<PlayerSkin, String> getter;
        final Function<CosmeticRegistry, Map<String, PlayerSkinPart>> map;

        Slot(
            String label,
            Function<PlayerSkin, String> getter,
            Function<CosmeticRegistry, Map<String, PlayerSkinPart>> map
        ) {
            this.label = label;
            this.getter = getter;
            this.map = map;
        }
    }
}
