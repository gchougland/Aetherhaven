package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunityCatalogService;
import com.hexvane.aetherhaven.community.CommunityDownloadService;
import com.hexvane.aetherhaven.community.CommunityManifestEntry;
import com.hexvane.aetherhaven.community.CommunityRequiredMods;
import com.hexvane.aetherhaven.plot.PlotBuildingStyles;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownBuildingStyleShowcase;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Gallery of curated town looks; sets {@link TownRecord#getPreferredBuildingStyleId()}. */
public final class TownStylePickerPage extends AetherhavenInteractiveCustomUIPage<TownStylePickerPage.PageData> {
    private static final String MSG = "aetherhaven_town_style.aetherhaven.townStyle";
    private static final String CARDS = "#StyleCards";

    @Nonnull
    private final UUID townId;
    private boolean templateAppended;
    private final AtomicBoolean downloadInFlight = new AtomicBoolean(false);

    public TownStylePickerPage(@Nonnull PlayerRef playerRef, @Nonnull UUID townId) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.townId = townId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        if (!templateAppended) {
            commandBuilder.append("Aetherhaven/TownStylePickerPage.ui");
            templateAppended = true;
        }
        commandBuilder.set("#PageTitleText.TextSpans", Message.translation(MSG + ".title"));
        commandBuilder.set("#StepHint.TextSpans", Message.translation(MSG + ".hint"));
        commandBuilder.set("#PageCloseButton.TextSpans", Message.translation(MSG + ".button.close"));
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PageCloseButton",
            EventData.of("Action", "Close"),
            false
        );
        bindCards(commandBuilder, eventBuilder, store);
    }

    private void bindCards(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        CommunityCatalogService community = plugin != null ? plugin.getCommunityCatalogService() : null;
        commandBuilder.clear(CARDS);
        List<TownBuildingStyleShowcase.Entry> entries = TownBuildingStyleShowcase.entries();
        for (int i = 0; i < entries.size(); i++) {
            TownBuildingStyleShowcase.Entry entry = entries.get(i);
            commandBuilder.append(CARDS, "Aetherhaven/TownStyleCard.ui");
            String card = CARDS + "[" + i + "]";
            commandBuilder.set(card + " #ShowcaseImage.AssetPath", entry.imageAssetPath());
            commandBuilder.set(card + " #StyleName.TextSpans", Message.translation(entry.nameLangKey()));
            commandBuilder.set(card + " #StyleDesc.TextSpans", Message.translation(entry.descLangKey()));
            if (entry.marketplace()) {
                commandBuilder.set(card + " #StyleAuthor.Visible", true);
                commandBuilder.set(
                    card + " #StyleAuthor.TextSpans",
                    Message.translation(MSG + ".byAuthor").param("name", authorLabel(entry.styleId()))
                );
            } else {
                commandBuilder.set(card + " #StyleAuthor.Visible", false);
            }
            boolean ready = isStyleReady(entry, community);
            boolean busy = downloadInFlight.get();
            String action = ready ? "Choose" : "Download";
            commandBuilder.set(
                card + " #ActionButton.TextSpans",
                Message.translation(MSG + ".button." + (ready ? "choose" : "download"))
            );
            commandBuilder.set(card + " #ActionButton.Disabled", busy);
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                card + " #ActionButton",
                EventData.of("Action", action).append("StyleId", entry.styleId()),
                false
            );
        }
    }

    @Nonnull
    private static String authorLabel(@Nonnull String styleId) {
        return switch (styleId) {
            case "jimmys village" -> "Jimmy G";
            case "fairy tale" -> "ppm";
            case "coastal ruins" -> "Mertie";
            case "slate fjord" -> "Quinny";
            default -> "";
        };
    }

    private static boolean isStyleReady(
        @Nonnull TownBuildingStyleShowcase.Entry entry,
        @Nullable CommunityCatalogService community
    ) {
        if (!entry.marketplace()) {
            return true;
        }
        if (community == null || !community.isEnabled()) {
            return false;
        }
        List<CommunityManifestEntry> needed = listStyleEntries(community, entry.styleId());
        if (needed.isEmpty()) {
            return false;
        }
        for (CommunityManifestEntry manifestEntry : needed) {
            if (!CommunityRequiredMods.isSatisfied(manifestEntry.getRequiredMods())) {
                continue;
            }
            if (!community.isInstalled(manifestEntry.getId())) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private static List<CommunityManifestEntry> listStyleEntries(
        @Nonnull CommunityCatalogService community,
        @Nonnull String styleId
    ) {
        String wanted = PlotBuildingStyles.normalize(styleId);
        List<CommunityManifestEntry> out = new ArrayList<>();
        if (wanted == null) {
            return out;
        }
        for (CommunityManifestEntry entry : community.getEntries()) {
            if (wanted.equals(PlotBuildingStyles.normalize(entry.getStyleId()))) {
                out.add(entry);
            }
        }
        return out;
    }

    @Nonnull
    private static List<CommunityManifestEntry> listDownloadTargets(
        @Nonnull CommunityCatalogService community,
        @Nonnull String styleId
    ) {
        List<CommunityManifestEntry> out = new ArrayList<>();
        for (CommunityManifestEntry entry : listStyleEntries(community, styleId)) {
            if (community.isInstalled(entry.getId())) {
                continue;
            }
            if (CommunityRequiredMods.isSatisfied(entry.getRequiredMods())) out.add(entry);
        }
        return out;
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
        switch (data.action) {
            case "Close" -> close();
            case "Choose" -> chooseStyle(ref, store, data.styleId);
            case "Download" -> tryDownloadStyle(ref, store, data.styleId);
            default -> {
            }
        }
    }

    private void chooseStyle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String styleId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || styleId == null || styleId.isBlank()) {
            return;
        }
        TownBuildingStyleShowcase.Entry entry = TownBuildingStyleShowcase.findByStyleId(styleId);
        if (entry == null) {
            return;
        }
        CommunityCatalogService community = plugin.getCommunityCatalogService();
        if (!isStyleReady(entry, community)) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".needDownload"),
                NotificationStyle.Warning
            );
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) {
            NotificationUtil.sendNotification(
                pr.getPacketHandler(),
                Message.translation(MSG + ".townMissing"),
                NotificationStyle.Danger
            );
            close();
            return;
        }
        town.setPreferredBuildingStyleId(entry.styleId());
        tm.updateTown(town);
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation(MSG + ".chosen").param("name", Message.translation(entry.nameLangKey())),
            NotificationStyle.Success
        );
        close();
    }

    private void tryDownloadStyle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String styleId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null || styleId == null || styleId.isBlank()) return;
        TownBuildingStyleShowcase.Entry entry = TownBuildingStyleShowcase.findByStyleId(styleId);
        if (entry == null || !entry.marketplace() || !downloadInFlight.compareAndSet(false, true)) return;
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.getTown(townId);
        if (town == null) { downloadInFlight.set(false); close(); return; }
        var progress = CommunityDownloadHud.begin(pr.getUuid(), "Style: " + entry.styleId());
        if (progress == null) {
            downloadInFlight.set(false);
            pr.sendMessage(Message.raw("A style download is already running. Follow its progress on your HUD."));
            close();
            return;
        }
        town.setPreferredBuildingStyleId(entry.styleId());
        tm.updateTown(town);
        close();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) CommunityDownloadHud.refresh(player, pr);
        CommunityCatalogService community = plugin.getCommunityCatalogService();
        CompletableFuture.runAsync(() -> {
            try {
                if (community.isEnabled() && (community.isCacheEmpty() || community.isCacheStale())
                    && !community.refreshFromApi(pr.getUuid())) {
                    progress.fail("Could not refresh the marketplace. Please try again.");
                    return;
                }
                CommunityDownloadService.installBatch(plugin, listDownloadTargets(community, entry.styleId()),
                    null, pr.getUuid(), progress);
            } catch (RuntimeException e) {
                java.util.logging.Logger.getLogger(TownStylePickerPage.class.getName())
                    .log(java.util.logging.Level.WARNING, "Style download failed", e);
                progress.fail("Could not finish downloading or loading this style. Check the server log and try again.");
            } finally {
                downloadInFlight.set(false);
                plugin.scheduleOnWorld(world, () -> {
                    if (ref.isValid() && !isDismissed()) refresh(ref, store);
                }, 1L);
            }
        });
    }

    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        cmd.set("#PageTitleText.TextSpans", Message.translation(MSG + ".title"));
        cmd.set("#StepHint.TextSpans", Message.translation(MSG + ".hint"));
        cmd.set("#PageCloseButton.TextSpans", Message.translation(MSG + ".button.close"));
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PageCloseButton",
            EventData.of("Action", "Close"),
            false
        );
        bindCards(cmd, events, store);
        sendUpdate(cmd, events, false);
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("StyleId", Codec.STRING), (d, v) -> d.styleId = v, d -> d.styleId)
                .add()
                .build();

        private String action;
        private String styleId;
    }
}
