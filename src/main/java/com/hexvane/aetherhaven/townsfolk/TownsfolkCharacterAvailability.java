package com.hexvane.aetherhaven.townsfolk;

import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkOptionalPluginRequirement;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import javax.annotation.Nonnull;

/** Whether a townsfolk character may be drawn from the world pool for new spawns. */
public final class TownsfolkCharacterAvailability {
    private TownsfolkCharacterAvailability() {}

    /**
     * True when the character has no plugin requirement, or the required plugin is present and enabled.
     * Checked-out characters are not affected by this gate.
     */
    public static boolean isEligibleForPoolDraw(@Nonnull TownsfolkCharacterDefinition def) {
        TownsfolkOptionalPluginRequirement req = def.getRequiresOptionalPlugin();
        if (req == null || !req.isComplete()) {
            return true;
        }
        return isOptionalPluginLoaded(req);
    }

    public static boolean isOptionalPluginLoaded(@Nonnull TownsfolkOptionalPluginRequirement req) {
        PluginManager manager = PluginManager.get();
        if (manager == null) {
            return false;
        }
        String wantGroup = req.getGroup();
        String wantName = req.getName();
        if (wantGroup == null || wantName == null) {
            return false;
        }
        PluginBase direct = manager.getPlugin(new PluginIdentifier(wantGroup, wantName));
        if (direct != null && direct.isEnabled()) {
            return true;
        }
        for (PluginBase plugin : manager.getPlugins()) {
            if (plugin == null || plugin.getManifest() == null) {
                continue;
            }
            PluginIdentifier loaded = new PluginIdentifier(plugin.getManifest());
            if (wantName.equalsIgnoreCase(loaded.getName()) && wantGroup.equalsIgnoreCase(loaded.getGroup())) {
                return plugin.isEnabled();
            }
        }
        return false;
    }
}
