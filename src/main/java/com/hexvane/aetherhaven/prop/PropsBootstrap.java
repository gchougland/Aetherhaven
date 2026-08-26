package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.command.AetherhavenPropCommand;
import com.hexvane.aetherhaven.ui.PropPlacementPage;
import com.hexvane.aetherhaven.ui.PropPrefabBrowserPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import javax.annotation.Nonnull;

/**
 * Wires the props system into the plugin.
 *
 * <p>Still needed from the parent plugin (not done here, see class docs on {@link PropConstants} /
 * {@link PropItemMetadata} / {@link PropBoundsUtil} for the exact hardcoded values to promote):
 * <ul>
 *   <li>Add {@code PROP_ITEM_ID}, {@code PACKAGING_WAND_ITEM_ID}, {@code PAGE_PROP_PLACEMENT},
 *       {@code PAGE_PROP_PREFAB_BROWSER}, {@code PROP_BOUNDS_PADDING}, {@code PERMISSION_PROP_BREAK} to
 *       {@code AetherhavenConstants} and switch the prop package over to them.</li>
 *   <li>Add a {@code PropCatalog} field + {@code getPropCatalog()} accessor to {@link AetherhavenPlugin},
 *       loaded via {@link PropCatalog#loadFromAssetPacksOrClasspath} in plugin startup (mirrors
 *       {@code getFestivalCatalog()} / {@code getConstructionCatalog()}).</li>
 *   <li>Call {@link #registerAssetCodecs} / {@link #register} from {@code AetherhavenCoreBootstrap}.</li>
 *   <li>Item JSON for {@code Aetherhaven_Prop_Item} (Use -&gt; OpenCustomUI {@code AetherhavenPropPlacement}) and
 *       {@code Aetherhaven_Packaging_Wand} (Use -&gt; {@code AetherhavenPackageProp}; optional secondary Use -&gt;
 *       OpenCustomUI {@code AetherhavenPropPrefabBrowser} to create new props from prefabs).</li>
 *   <li>Lang entries under {@code aetherhaven_props.lang} / {@code aetherhaven_items.lang} for all the
 *       {@code aetherhaven_props.aetherhaven.*} and {@code aetherhaven_items.items.Aetherhaven_Prop_Item.*} keys
 *       referenced by this package.</li>
 *   <li>Plot teardown / relocation hooks should call {@link PropPlotTeardown#packageIntersecting} before clearing
 *       or moving a plot's footprint, and {@link PropWorldRegistries#saveAll()} on world unload / shutdown
 *       (alongside {@code PoiPersistence} saves).</li>
 * </ul>
 */
public final class PropsBootstrap {
    private PropsBootstrap() {}

    public static void registerAssetCodecs(@Nonnull AetherhavenPlugin core) {
        core
            .getCodecRegistry(Interaction.CODEC)
            .register(
                "AetherhavenPackageProp",
                AetherhavenPackagePropInteraction.class,
                AetherhavenPackagePropInteraction.CODEC
            );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            core,
            PropPlacementPage.class,
            PropConstants.PAGE_PROP_PLACEMENT,
            (ref, componentAccessor, playerRef, context) -> PropPlacementOpenHelper.tryOpen(ref, componentAccessor, playerRef, context)
        );
        OpenCustomUIInteraction.registerSimple(
            core,
            PropPrefabBrowserPage.class,
            PropConstants.PAGE_PROP_PREFAB_BROWSER,
            PropPrefabBrowserPage::new
        );
    }

    public static void register(@Nonnull AetherhavenPlugin core, @Nonnull JavaPlugin plugin) {
        PropLootExclusions.ensureDefaultFile(core);
        PropPrefabCache.invalidateCatalog(core.getPropCatalog());
        AetherhavenPlacedInstance.register(plugin.getEntityStoreRegistry());
        plugin.getEntityStoreRegistry().registerSystem(new PropBreakBlockSystem(core));
        plugin.getEntityStoreRegistry().registerSystem(new PropPackagingWandTickSystem(core));
        core.registerAetherhavenSubcommand(new AetherhavenPropCommand());
    }
}
