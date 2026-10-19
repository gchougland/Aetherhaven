package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.community.CommunityDownloadProgress;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** World ticks own HUD registration; workers send packet-only progress updates during resource loading. */
public final class CommunityDownloadHud extends CustomUIHud {
    private static final String KEY = "aetherhaven-community-download";
    private static final Map<UUID, CommunityDownloadProgress> JOBS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_CLEANUP = new AtomicLong();
    private CommunityDownloadProgress job;
    private CommunityDownloadProgress.Snapshot rendered;
    private long nextRefresh;
    private int sentErrors;
    private final java.util.function.Consumer<CommunityDownloadProgress.Snapshot> progressListener = this::render;

    public CommunityDownloadHud(PlayerRef player) { super(player, KEY, 0); }

    /** A player can close/reopen any menu without starting a competing batch. */
    public static CommunityDownloadProgress begin(UUID player, String title) {
        if (player == null) return null;
        var created = new CommunityDownloadProgress(title);
        return JOBS.compute(player, (id, old) -> old == null || old.terminal() ? created : old) == created ? created : null;
    }

    public static void refresh(Player player, PlayerRef ref) {
        long now = System.currentTimeMillis();
        long cleanup = NEXT_CLEANUP.get();
        if (now >= cleanup && NEXT_CLEANUP.compareAndSet(cleanup, now + 30_000)) {
            JOBS.entrySet().removeIf(e -> e.getValue().expired(now));
        }
        var job = JOBS.get(ref.getUuid());
        if (job != null && job.expired(now)) {
            JOBS.remove(ref.getUuid(), job);
            job = null;
        }
        var existing = player.getHudManager().getCustomHud(KEY);
        if (job == null) {
            if (existing != null) player.getHudManager().removeCustomHud(ref, KEY);
            return;
        }
        CommunityDownloadHud hud;
        if (existing instanceof CommunityDownloadHud current) {
            hud = current;
        } else {
            hud = new CommunityDownloadHud(ref);
            player.getHudManager().addCustomHud(ref, hud);
        }
        if (hud.job != job) {
            if (hud.job != null) hud.job.unlisten(hud.progressListener);
            hud.job = job;
            hud.rendered = null;
            hud.sentErrors = 0;
            hud.nextRefresh = 0;
            job.listen(hud.progressListener);
        }
        job.publish();
    }

    /** Called under the job lock, including during long catalog reloads between world ticks. */
    private void render(CommunityDownloadProgress.Snapshot snapshot) {
        long now = System.currentTimeMillis();
        if (snapshot.equals(rendered)) return;
        // Never coalesce loading steps or phase changes into a later tick: the reload may stall that tick.
        if (rendered != null && snapshot.phase() == rendered.phase()
            && snapshot.phase() == CommunityDownloadProgress.Phase.DOWNLOADING && now < nextRefresh) return;
        nextRefresh = now + 250;
        for (int i = sentErrors; i < snapshot.errors().size(); i++) {
            getPlayerRef().sendMessage(Message.raw("Style download: " + snapshot.errors().get(i)).color("#efaaa0"));
        }
        update(false, commands(snapshot));
        // In-world connections normally flush at the end of a world tick. Loading must not wait for it.
        getPlayerRef().getPacketHandler().tryFlush();
        sentErrors = snapshot.errors().size();
        rendered = snapshot;
    }

    @Override
    protected void onRemove() {
        if (job != null) job.unlisten(progressListener);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        builder.append("Aetherhaven/CommunityDownloadHud.ui");
    }

    public static UICommandBuilder commands(CommunityDownloadProgress.Snapshot snapshot) {
        var b = new UICommandBuilder();
        b.set("#DownloadTitle.Text", abbreviate(snapshot.title(), 48));
        b.set("#DownloadPhase.Text", snapshot.heading());
        b.set("#DownloadDetail.Text", snapshot.detail());
        b.set("#DownloadCount.Text", snapshot.total() == 0
            ? (snapshot.terminal() ? "No buildings downloaded" : "Checking available files")
            : snapshot.done() + " / " + snapshot.total()
                + (snapshot.phase() == CommunityDownloadProgress.Phase.LOADING ? " loading steps" : " buildings")
                + " • " + snapshot.remaining() + " remaining");
        int fill = (int) Math.round(snapshot.fraction() * 1000);
        b.set("#DownloadFill.FlexWeight", Math.max(1, fill));
        b.set("#DownloadRemainder.FlexWeight", Math.max(1, 1000 - fill));
        b.set("#DownloadFill.Visible", fill > 0);
        b.set("#DownloadRemainder.Visible", fill < 1000);
        b.set("#DownloadErrorPanel.Visible", !snapshot.errors().isEmpty());
        b.set("#DownloadErrors.Text", snapshot.errors().isEmpty() ? ""
            : snapshot.errors().size() + " error(s): " + abbreviate(snapshot.errors().getLast(), 135)
                + "\nFull details are also in chat.");
        return b;
    }

    private static String abbreviate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit - 3) + "..." : text;
    }
}
