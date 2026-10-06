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
import java.util.Iterator;
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
 * {@link PlayerSkinPart#doesRequireGenericHaircut()} exports as {@code Generic{HairType}} only (same hair gradient id).
 * Without a half-covering hat, export only the chosen style. The style blockymodel already includes a
 * {@code HairBase} mesh; duplicating {@code Generic{HairType}} as a second attachment z-fights on NPC models.
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
    private static final String EARS1_BLOCKYMODEL = "Characters/Body_Attachments/Ears/Ears1.blockymodel";
    private static final String EAR_DEFAULT_NPC_GREYSCALE =
        "Characters/Body_Attachments/Ears/Ears1_Textures/Ears1_Greyscale_Texture.png";
    private static final String LEGACY_EAR_GREYSCALE = "Characters/Body_Attachments/Ears/Ears.png";
    private static final String PLAYER_BLOCKYMODEL = "Characters/Player.blockymodel";
    private static final String GENERIC_SHORT_HAIR = "Characters/Haircuts/GenericShort.blockymodel";
    private static final String GENERIC_MEDIUM_HAIR = "Characters/Haircuts/GenericMedium.blockymodel";
    private static final String GENERIC_LONG_HAIR = "Characters/Haircuts/GenericLong.blockymodel";

    private PlayerSkinModelExporter() {}

    @Nonnull
    public static JsonObject toModelJson(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        List<ModelAttachment> list = buildAttachmentList(skin, registry);
        String rootModel = null;
        String rootTexture = null;
        String rootGradientSet = null;
        String rootGradientId = null;
        for (Iterator<ModelAttachment> it = list.iterator(); it.hasNext(); ) {
            ModelAttachment body = it.next();
            if (PLAYER_BLOCKYMODEL.equals(body.getModel())) {
                rootModel = body.getModel();
                rootTexture = body.getTexture();
                rootGradientSet = body.getGradientSet();
                rootGradientId = body.getGradientId();
                it.remove();
                break;
            }
        }
        sanitizeAttachmentList(list);

        JsonArray attachments = new JsonArray();
        for (ModelAttachment ma : list) {
            attachments.add(modelAttachmentToJson(ma));
        }
        JsonObject root = new JsonObject();
        root.addProperty("Parent", PARENT_PLAYER);
        if (rootModel != null) {
            root.addProperty("Model", rootModel);
            root.addProperty("Texture", rootTexture);
            if (rootGradientSet != null && !rootGradientSet.isEmpty()) {
                root.addProperty("GradientSet", rootGradientSet);
                root.addProperty("GradientId", rootGradientId);
            }
        }
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
        List<ModelAttachment> list = buildAttachmentList(skin, registry);
        sanitizeAttachmentList(list);
        return list.toArray(ModelAttachment[]::new);
    }

    @Nonnull
    private static List<ModelAttachment> buildAttachmentList(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        List<ModelAttachment> list = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            String raw;
            if (slot == Slot.HAIRCUT) {
                raw = effectiveHaircutIdForHeadAccessory(skin, registry);
            } else if (slot == Slot.FACE) {
                raw = effectiveFaceIdForFacialHair(skin);
            } else {
                raw = slot.getter.apply(skin);
            }
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ModelAttachment one = resolveSlot(slot.label, raw, slot.map.apply(registry), registry, skin);
            if (one != null) {
                list.add(one);
            }
        }
        return list;
    }

    /**
     * Body mesh from the skin's body characteristic (e.g. muscular), before NPC sanitization drops the body attachment.
     */
    @Nullable
    public static String findPlayerBodyModel(@Nonnull PlayerSkin skin, @Nonnull CosmeticRegistry registry) {
        for (ModelAttachment attachment : buildAttachmentList(skin, registry)) {
            if (PLAYER_BLOCKYMODEL.equals(attachment.getModel())) {
                return attachment.getModel();
            }
        }
        return null;
    }

    /**
     * Drops NPC-problematic attachments: duplicate {@code Generic*} hair bases when a styled haircut is present, and
     * {@code Player.blockymodel} body meshes (the {@code Player} parent already provides the base mesh).
     */
    public static void sanitizeAttachmentList(@Nonnull List<ModelAttachment> list) {
        removeRedundantGenericHaircuts(list);
        list.removeIf(a -> PLAYER_BLOCKYMODEL.equals(a.getModel()));
        // Exported player skins need the same real earring anchors as static resident models.
        list.replaceAll(a -> EARS1_BLOCKYMODEL.equals(a.getModel())
            ? new ModelAttachment("Characters/Aetherhaven/Resident_Ears.blockymodel",
                a.getTexture(),a.getGradientSet(),a.getGradientId(),a.getWeight()) : a);
    }

    private static void removeRedundantGenericHaircuts(@Nonnull List<ModelAttachment> list) {
        boolean hasStyledHaircut =
            list.stream().anyMatch(a -> a.getModel() != null && isStyledHaircutModel(a.getModel()));
        if (!hasStyledHaircut) {
            return;
        }
        list.removeIf(a -> a.getModel() != null && isGenericHaircutModel(a.getModel()));
    }

    private static boolean isGenericHaircutModel(@Nonnull String model) {
        return GENERIC_SHORT_HAIR.equals(model) || GENERIC_MEDIUM_HAIR.equals(model) || GENERIC_LONG_HAIR.equals(model);
    }

    private static boolean isStyledHaircutModel(@Nonnull String model) {
        return model.startsWith("Characters/Haircuts/") && !isGenericHaircutModel(model);
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
                npcEarGreyscaleTexture(modelPath, greyscale),
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

    /** Character Creator lists {@link #LEGACY_EAR_GREYSCALE} for default ears; static NPC models need {@link #EAR_DEFAULT_NPC_GREYSCALE}. */
    @Nonnull
    private static String npcEarGreyscaleTexture(@Nonnull String modelPath, @Nonnull String greyscale) {
        if (EARS1_BLOCKYMODEL.equals(modelPath) && LEGACY_EAR_GREYSCALE.equals(greyscale)) {
            return EAR_DEFAULT_NPC_GREYSCALE;
        }
        return greyscale;
    }

    /**
     * {@code Face_Stubble} paints chin stubble on the detached face texture; with a beard attachment that overlaps
     * and smears on NPC {@code DefaultAttachments}. Use neutral face when facial hair is equipped.
     */
    @Nullable
    private static String effectiveFaceIdForFacialHair(@Nonnull PlayerSkin skin) {
        String faceId = skin.face;
        if (faceId == null || faceId.isEmpty()) {
            return null;
        }
        if (skin.facialHair == null || skin.facialHair.isEmpty()) {
            return faceId;
        }
        String[] parts = faceId.split("\\.");
        if (!"Face_Stubble".equals(parts[0])) {
            return faceId;
        }
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder("Face_Neutral");
            for (int i = 1; i < parts.length; i++) {
                sb.append('.').append(parts[i]);
            }
            return sb.toString();
        }
        return "Face_Neutral";
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
