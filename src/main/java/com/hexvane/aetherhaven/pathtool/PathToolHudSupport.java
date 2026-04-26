package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional integration with Buuz135 MHUD (see {@code MultipleHUD} on GitHub Buuz135/MHUD): registers the path
 * tool status overlay under {@link AetherhavenConstants#PATH_TOOL_MHUD_SLOT} so it can coexist with other custom HUDs.
 * When MHUD is not loaded, falls back to {@link Player#getHudManager()}{@code .setCustomHud}.
 */
public final class PathToolHudSupport {
    private static final String MHUD_CLASS = "com.buuz135.mhud.MultipleHUD";
    private static final String MULTI_HUD_CLASS = "com.buuz135.mhud.MultipleCustomUIHud";

    @Nullable
    private static volatile Object multipleHudInstance;
    @Nullable
    private static volatile Method setCustomHudMethod;
    @Nullable
    private static volatile Method hideCustomHudMethod;
    @Nullable
    private static volatile Method multiGetMethod;
    /** -1 = MHUD class absent; 0 = not yet wired; 1 = reflection ready. */
    private static volatile int mhudInitState;

    private PathToolHudSupport() {}

    private static void ensureMhudResolved() {
        if (mhudInitState < 0 || setCustomHudMethod != null) {
            return;
        }
        synchronized (PathToolHudSupport.class) {
            if (mhudInitState < 0 || setCustomHudMethod != null) {
                return;
            }
            try {
                Class<?> mhud = Class.forName(MHUD_CLASS);
                Class<?> playerClass = Player.class;
                Class<?> prClass = PlayerRef.class;
                Class<?> customClass = CustomUIHud.class;
                Method getInstance = mhud.getMethod("getInstance");
                Object inst = getInstance.invoke(null);
                if (inst == null) {
                    return;
                }
                setCustomHudMethod = mhud.getMethod("setCustomHud", playerClass, prClass, String.class, customClass);
                hideCustomHudMethod = mhud.getMethod("hideCustomHud", playerClass, String.class);
                Class<?> multi = Class.forName(MULTI_HUD_CLASS);
                multiGetMethod = multi.getMethod("get", String.class);
                multipleHudInstance = inst;
                mhudInitState = 1;
            } catch (ClassNotFoundException e) {
                mhudInitState = -1;
                setCustomHudMethod = null;
                hideCustomHudMethod = null;
                multiGetMethod = null;
                multipleHudInstance = null;
            } catch (Throwable ignored) {
                setCustomHudMethod = null;
                hideCustomHudMethod = null;
                multiGetMethod = null;
                multipleHudInstance = null;
            }
        }
    }

    public static boolean isMhudAvailable() {
        ensureMhudResolved();
        return setCustomHudMethod != null && multipleHudInstance != null;
    }

    private static boolean isMultipleCustomHud(@Nullable CustomUIHud root) {
        return root != null && MULTI_HUD_CLASS.equals(root.getClass().getName());
    }

    @Nullable
    private static PathToolStatusHud getChildFromMulti(@Nonnull CustomUIHud root) {
        if (multiGetMethod == null) {
            return null;
        }
        try {
            Object child = multiGetMethod.invoke(root, AetherhavenConstants.PATH_TOOL_MHUD_SLOT);
            return child instanceof PathToolStatusHud ? (PathToolStatusHud) child : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Returns an existing path-tool HUD widget or creates and registers one (MHUD slot or vanilla custom HUD).
     */
    @Nonnull
    public static PathToolStatusHud obtainPathToolHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        ensureMhudResolved();
        if (isMhudAvailable()) {
            CustomUIHud root = player.getHudManager().getCustomHud();
            if (isMultipleCustomHud(root)) {
                PathToolStatusHud existing = getChildFromMulti(root);
                if (existing != null) {
                    return existing;
                }
            }
            PathToolStatusHud created = new PathToolStatusHud(playerRef);
            try {
                setCustomHudMethod.invoke(multipleHudInstance, player, playerRef, AetherhavenConstants.PATH_TOOL_MHUD_SLOT, created);
            } catch (Throwable ignored) {
                player.getHudManager().setCustomHud(playerRef, created);
            }
            return created;
        }
        CustomUIHud root = player.getHudManager().getCustomHud();
        if (root instanceof PathToolStatusHud h) {
            return h;
        }
        PathToolStatusHud created = new PathToolStatusHud(playerRef);
        player.getHudManager().setCustomHud(playerRef, created);
        return created;
    }

    /**
     * Removes the path-tool overlay without clearing other MHUD layers.
     */
    public static void removePathToolHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        ensureMhudResolved();
        if (isMhudAvailable()) {
            CustomUIHud root = player.getHudManager().getCustomHud();
            if (isMultipleCustomHud(root) && getChildFromMulti(root) != null) {
                try {
                    hideCustomHudMethod.invoke(multipleHudInstance, player, AetherhavenConstants.PATH_TOOL_MHUD_SLOT);
                } catch (Throwable ignored) {
                    // leave composite HUD intact
                }
                return;
            }
        }
        if (player.getHudManager().getCustomHud() instanceof PathToolStatusHud) {
            player.getHudManager().setCustomHud(playerRef, null);
        }
    }
}
