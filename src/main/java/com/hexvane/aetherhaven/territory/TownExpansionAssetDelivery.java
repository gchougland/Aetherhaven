package com.hexvane.aetherhaven.territory;

import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.AssetFinalize;
import com.hypixel.hytale.protocol.packets.setup.AssetInitialize;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Pushes runtime common assets during gameplay. {@link CommonAssetModule#sendAssetsToPlayer} must not be used in
 * world — it emits {@code WorldLoadProgress}, which disconnects clients already in the playing stage.
 */
public final class TownExpansionAssetDelivery {
    private TownExpansionAssetDelivery() {}

    public static void pushInGame(@Nonnull PlayerRef playerRef, @Nonnull List<CommonAsset> assets) {
        if (assets.isEmpty()) {
            return;
        }
        PacketHandler handler = playerRef.getPacketHandler();
        List<CommonAsset> unique = dedupeByName(assets);
        for (CommonAsset asset : unique) {
            writeAssetWithoutLoadProgress(handler, asset);
        }
        handler.writeNoCache(new RequestCommonAssetsRebuild());
    }

    @Nonnull
    private static List<CommonAsset> dedupeByName(@Nonnull List<CommonAsset> assets) {
        List<CommonAsset> out = new ArrayList<>(assets.size());
        for (CommonAsset asset : assets) {
            boolean seen = false;
            for (CommonAsset existing : out) {
                if (existing.getName().equals(asset.getName())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(asset);
            }
        }
        return out;
    }

    private static void writeAssetWithoutLoadProgress(@Nonnull PacketHandler handler, @Nonnull CommonAsset asset) {
        byte[] allBytes = asset.getBlob().join();
        byte[][] parts = ArrayUtil.split(allBytes, CommonAssetModule.MAX_FRAME);
        ToClientPacket[] packets = new ToClientPacket[2 + parts.length];
        packets[0] = new AssetInitialize(asset.toPacket(), allBytes.length);
        for (int partIndex = 0; partIndex < parts.length; partIndex++) {
            packets[1 + partIndex] = new AssetPart(parts[partIndex]);
        }
        packets[packets.length - 1] = new AssetFinalize();
        handler.write(packets);
    }
}
