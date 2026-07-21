package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownResidentDisplay;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Rows for the tourist portal town records visitor list. */
public final class TouristVisitManifest {
    public record Row(@Nonnull String label, @Nonnull String portraitPath, @Nonnull ManifestKind kind) {}

    public enum ManifestKind {
        /** Day visitor; included when clearing visitors. */
        VISITING,
        /** Invited to stay; kept when clearing visitors. */
        INVITED,
        /** Housed in town; kept when clearing visitors. */
        HOUSED
    }

    private TouristVisitManifest() {}

    @Nonnull
    public static List<Row> listRows(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        List<Row> out = new ArrayList<>();
        Set<String> seenCharacterIds = new HashSet<>();
        for (TouristRecord rec : town.getTouristRecords()) {
            if (rec.isCitizen()) {
                continue;
            }
            String characterId = rec.getCharacterId();
            if (!characterId.isBlank()) {
                String key = characterId.toLowerCase();
                if (!seenCharacterIds.add(key)) {
                    continue;
                }
            }
            UUID entityUuid = rec.getEntityUuid();
            TownResidentDisplay.Resolved resolved = resolveDisplay(store, plugin, rec.getCharacterId(), entityUuid);
            ManifestKind kind = classify(rec, town, entityUuid, catalog);
            out.add(new Row(resolved.displayName(), resolved.portraitPath(), kind));
        }
        out.sort(Comparator.comparing(Row::label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Nonnull
    private static ManifestKind classify(
        @Nonnull TouristRecord rec,
        @Nonnull TownRecord town,
        @Nullable UUID entityUuid,
        @Nonnull ConstructionCatalog catalog
    ) {
        if (rec.isInvitedToStay()) {
            return ManifestKind.INVITED;
        }
        if (entityUuid != null && town.isNpcHomeResidentOnHousePlot(entityUuid, catalog)) {
            return ManifestKind.HOUSED;
        }
        return ManifestKind.VISITING;
    }

    @Nonnull
    private static TownResidentDisplay.Resolved resolveDisplay(
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId,
        @Nullable UUID entityUuid
    ) {
        if (entityUuid != null) {
            Ref<EntityStore> entityRef = store.getExternalData().getRefFromUUID(entityUuid);
            if (entityRef != null && entityRef.isValid()) {
                NPCEntity npc = store.getComponent(entityRef, NPCEntity.getComponentType());
                String roleId =
                    npc != null && npc.getRoleName() != null && !npc.getRoleName().isBlank()
                        ? npc.getRoleName()
                        : "Aetherhaven_Townsfolk";
                return TownResidentDisplay.resolveFromEntity(store, entityRef, roleId, plugin);
            }
        }
        return TownResidentDisplay.resolveOffline(plugin, "Aetherhaven_Townsfolk", characterId, null);
    }
}
