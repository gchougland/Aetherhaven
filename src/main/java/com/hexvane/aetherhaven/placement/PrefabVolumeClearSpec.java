package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Prefab anchor and buffer for sparse plot block teardown. */
public record PrefabVolumeClearSpec(
    @Nonnull Vector3i anchor,
    @Nonnull Rotation yaw,
    @Nonnull IPrefabBuffer buffer,
    boolean preserveWater
) {}
