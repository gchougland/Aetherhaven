package com.hexvane.aetherhaven.pathtool;

/**
 * Spike (server-only): native Entity-tool gizmos are not exposed for arbitrary mod items.
 *
 * <p>Evidence from Hytale source: builder tool packets in {@code BuilderToolsPacketHandler} are permission-gated
 * ({@code hytale.editor.builderTools}, {@code hytale.editor.*}); transform packets are part of the editor pipeline.
 * {@code BuilderToolInteraction} is client-driven. A new {@code BuilderTool} id still needs a matching client.
 * Conclusion: the path tool uses per-player {@code DisplayDebug} and click interactions; no 1:1 stock gizmo binding
 * without a client mod or new engine API.
 */
public final class PathToolGizmoSpike {
    private PathToolGizmoSpike() {}
}
