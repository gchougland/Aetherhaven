package com.hexvane.aetherhaven.map;

import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import java.lang.reflect.Field;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reads {@link WorldMapTracker} loaded-chunk state (same field BetterMap uses for exploration gating). */
public final class WorldMapTrackerCompat {
  private static final boolean BETTERMAP_PRESENT = detectBetterMapPresent();

  @Nullable
  private static final Field LOADED_FIELD;

  @Nullable
  private static final Field LOADED_LOCK_FIELD;

  static {
    Field loaded = null;
    Field lock = null;
    try {
      loaded = WorldMapTracker.class.getDeclaredField("loaded");
      loaded.setAccessible(true);
      lock = WorldMapTracker.class.getDeclaredField("loadedLock");
      lock.setAccessible(true);
    } catch (ReflectiveOperationException ignored) {
      // Fall back to view-radius checks only.
    }
    LOADED_FIELD = loaded;
    LOADED_LOCK_FIELD = lock;
  }

  private WorldMapTrackerCompat() {}

  public static boolean isBetterMapPresent() {
    return BETTERMAP_PRESENT;
  }

  /**
   * True when the vanilla tracker has already sent this map chunk to the player.
   * Required for BetterMap compatibility so we do not push unexplored tiles.
   */
  public static boolean hasChunkLoadedOnClient(@Nonnull Player player, long chunkIndex) {
    HLongSet loaded = getLoadedSet(player);
    if (loaded != null) {
      return loaded.contains(chunkIndex);
    }
    return false;
  }

  @Nullable
  private static HLongSet getLoadedSet(@Nonnull Player player) {
    if (LOADED_FIELD == null || LOADED_LOCK_FIELD == null) {
      return null;
    }
    WorldMapTracker tracker = player.getWorldMapTracker();
    ReentrantReadWriteLock lock;
    try {
      lock = (ReentrantReadWriteLock) LOADED_LOCK_FIELD.get(tracker);
    } catch (IllegalAccessException e) {
      return null;
    }
    lock.readLock().lock();
    try {
      return (HLongSet) LOADED_FIELD.get(tracker);
    } catch (IllegalAccessException e) {
      return null;
    } finally {
      lock.readLock().unlock();
    }
  }

  private static boolean detectBetterMapPresent() {
    try {
      Class.forName("dev.ninesliced.BetterMap");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
