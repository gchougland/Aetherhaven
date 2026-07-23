package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.ClaimedTerritoryChunkRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hexvane.aetherhaven.tourist.TownPortalTravelColor;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sends per-player painted map tiles for town territory borders without touching the shared map cache.
 * Only overlays map chunks the client has already received.
 */
public final class TownBorderMapOverlayService {
  private static final long TICK_MS = 750L;
  private static final int MAX_CHUNKS_PER_PLAYER_PER_TICK = 10;
  private static final long MAX_TICK_BUDGET_NS = 12_000_000L;
  private static final int MAX_SORTED_CANDIDATES = 80;

  private static final ConcurrentHashMap<String, ScheduledFuture<?>> WORLD_TASKS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, LongSet> LAST_OVERLAY_CHUNKS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, Long2ObjectMap<CachedTile>> PAINT_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, WorldBorderState> WORLD_BORDER_STATE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, AtomicInteger> WORLD_TICK_COUNTER = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, AtomicInteger> WORLD_PLAYER_CURSOR = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Long2ObjectMap<CachedGeometry>> GEOMETRY_CACHE =
      new ConcurrentHashMap<>();

  private TownBorderMapOverlayService() {}

  private static final class CachedTile {
    final MapImage painted;
    final int baseIdentity;
    final long townsSignature;
    @Nullable
    final UUID viewerTownId;
    int lastSentBaseIdentity;

    CachedTile(MapImage painted, int baseIdentity, long townsSignature, @Nullable UUID viewerTownId) {
      this.painted = painted;
      this.baseIdentity = baseIdentity;
      this.townsSignature = townsSignature;
      this.viewerTownId = viewerTownId;
      this.lastSentBaseIdentity = Integer.MIN_VALUE;
    }

    boolean matches(int baseIdentity, long townsSignature, @Nullable UUID viewerTownId) {
      return this.baseIdentity == baseIdentity
          && this.townsSignature == townsSignature
          && Objects.equals(this.viewerTownId, viewerTownId);
    }

    boolean needsSend(int baseIdentity) {
      return this.lastSentBaseIdentity != baseIdentity;
    }

    void markSent(int baseIdentity) {
      this.lastSentBaseIdentity = baseIdentity;
    }
  }

  private static final class CachedGeometry {
    final TownBorderMapRenderer.BorderGeometry geometry;
    final long townsSignature;
    final int width;
    final int height;

    CachedGeometry(
        TownBorderMapRenderer.BorderGeometry geometry, long townsSignature, int width, int height) {
      this.geometry = geometry;
      this.townsSignature = townsSignature;
      this.width = width;
      this.height = height;
    }

    boolean matches(long townsSignature, int width, int height) {
      return this.townsSignature == townsSignature && this.width == width && this.height == height;
    }
  }

  private static final class WorldBorderState {
    long townsSignature = Long.MIN_VALUE;
    long[] perimeterChunks = new long[0];
    List<TownRecord> towns = List.of();
    Long2ObjectMap<int[]> townsByPerimeterChunk = new Long2ObjectOpenHashMap<>();
  }

  private static final class BorderPlayer {
    final Player player;
    final PlayerRef playerRef;
    final UUID playerUuid;

    BorderPlayer(Player player, PlayerRef playerRef, UUID playerUuid) {
      this.player = player;
      this.playerRef = playerRef;
      this.playerUuid = playerUuid;
    }
  }

  public static void startWorld(@Nonnull World world) {
    WORLD_TICK_COUNTER.putIfAbsent(world.getName(), new AtomicInteger());
    WORLD_PLAYER_CURSOR.putIfAbsent(world.getName(), new AtomicInteger());
    WORLD_TASKS.computeIfAbsent(
        world.getName(),
        name ->
            HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                  if (!world.isAlive()) {
                    stopWorld(world);
                    return;
                  }
                  world.execute(() -> tickWorld(world));
                },
                TICK_MS,
                TICK_MS,
                TimeUnit.MILLISECONDS));
  }

  public static void stopWorld(@Nonnull World world) {
    ScheduledFuture<?> task = WORLD_TASKS.remove(world.getName());
    if (task != null) {
      task.cancel(false);
    }
    WORLD_TICK_COUNTER.remove(world.getName());
    WORLD_PLAYER_CURSOR.remove(world.getName());
    GEOMETRY_CACHE.remove(world.getName());
    // PlayerRef uuid is available off-thread; Store.getComponent is not (RemoveWorldEvent).
    for (PlayerRef pref : world.getPlayerRefs()) {
      UUID uuid = pref.getUuid();
      LAST_OVERLAY_CHUNKS.remove(uuid);
      PAINT_CACHE.remove(uuid);
    }
    WORLD_BORDER_STATE.remove(world.getName());
  }

  public static void refreshPlayer(@Nonnull World world, @Nonnull UUID playerUuid) {
    world.execute(
        () -> {
          Player player = findPlayer(world, playerUuid);
          if (player == null) {
            return;
          }
          Ref<EntityStore> ref = player.getReference();
          if (ref == null || !ref.isValid()) {
            return;
          }
          Store<EntityStore> store = ref.getStore();
          PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
          PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
          if (playerRef == null) {
            return;
          }
          if (journal == null || !journal.isShowTownBordersOnMap()) {
            clearOverlays(world, player, playerRef, playerUuid);
          } else {
            restorePristineOverlays(world, player, playerRef, playerUuid);
            PAINT_CACHE.remove(playerUuid);
            WorldBorderState borderState = worldBorderState(world);
            if (borderState.towns.isEmpty()) {
              return;
            }
            updatePlayerOverlays(world, player, playerRef, playerUuid, borderState);
          }
        });
  }

  /** Clears cached painted tiles and restores base map imagery for every viewer in this world. */
  public static void invalidateOverlaysForWorld(@Nonnull World world) {
    world.execute(
        () -> {
          for (PlayerRef pref : world.getPlayerRefs()) {
            UUID uuid = pref.getUuid();
            if (uuid == null) {
              continue;
            }
            Ref<EntityStore> ref = pref.getReference();
            if (ref == null || !ref.isValid()) {
              continue;
            }
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player == null) {
              continue;
            }
            PlayerTownJournalState journal =
                ref.getStore().getComponent(ref, PlayerTownJournalState.getComponentType());
            if (journal == null || !journal.isShowTownBordersOnMap()) {
              continue;
            }
            restorePristineOverlays(world, player, pref, uuid);
            PAINT_CACHE.remove(uuid);
          }
        });
  }

  private static void restorePristineOverlays(
      @Nonnull World world,
      @Nonnull Player player,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid) {
    LongSet previous = LAST_OVERLAY_CHUNKS.remove(playerUuid);
    if (previous == null || previous.isEmpty()) {
      return;
    }
    if (!playerRef.getPacketHandler().getChannel(NetworkChannel.WorldMap).isWritable()) {
      return;
    }
    WorldMapManager mapManager = world.getWorldMapManager();
    LongSet loaded = WorldMapTrackerCompat.getLoadedChunks(player);
    List<MapChunk> toSend = new ArrayList<>();
    for (long index : previous) {
      if (!loaded.contains(index)) {
        continue;
      }
      int cx = ChunkUtil.xOfChunkIndex(index);
      int cz = ChunkUtil.zOfChunkIndex(index);
      MapImage base = resolveMapImage(mapManager, cx, cz);
      if (base == null) {
        continue;
      }
      MapImage pristine = TownMapImagePixels.cloneImage(base);
      if (pristine != null) {
        toSend.add(new MapChunk(cx, cz, pristine));
      }
    }
    if (!toSend.isEmpty()) {
      playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(toSend.toArray(MapChunk[]::new), null, null));
    }
  }

  private static void tickWorld(@Nonnull World world) {
    long tickStart = System.nanoTime();

    List<BorderPlayer> borderPlayers = collectBorderPlayers(world);
    if (borderPlayers.isEmpty()) {
      return;
    }

    AetherhavenPlugin plugin = AetherhavenPlugin.get();
    if (plugin == null) {
      return;
    }

    TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
    WorldBorderState borderState = ensureWorldBorderState(world.getName(), townManager, world.getName());
    if (borderState.towns.isEmpty()) {
      return;
    }

    AtomicInteger tickCounter = WORLD_TICK_COUNTER.computeIfAbsent(world.getName(), ignored -> new AtomicInteger());
    tickCounter.incrementAndGet();

    AtomicInteger cursor =
        WORLD_PLAYER_CURSOR.computeIfAbsent(world.getName(), ignored -> new AtomicInteger());
    int start = cursor.get();
    int count = borderPlayers.size();

    for (int i = 0; i < count; i++) {
      if (System.nanoTime() - tickStart > MAX_TICK_BUDGET_NS) {
        cursor.set((start + i) % count);
        return;
      }
      BorderPlayer bp = borderPlayers.get((start + i) % count);
      updatePlayerOverlays(world, bp.player, bp.playerRef, bp.playerUuid, borderState);
    }
    cursor.set((start + count) % count);
  }

  @Nonnull
  private static List<BorderPlayer> collectBorderPlayers(@Nonnull World world) {
    List<BorderPlayer> result = new ArrayList<>();
    for (PlayerRef pref : world.getPlayerRefs()) {
      Ref<EntityStore> ref = pref.getReference();
      if (ref == null || !ref.isValid()) {
        continue;
      }
      Store<EntityStore> store = ref.getStore();
      Player player = store.getComponent(ref, Player.getComponentType());
      PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
      UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
      if (player == null || playerRef == null || uc == null) {
        continue;
      }
      UUID playerUuid = uc.getUuid();
      PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
      if (journal == null || !journal.isShowTownBordersOnMap()) {
        if (LAST_OVERLAY_CHUNKS.containsKey(playerUuid)) {
          clearOverlays(world, player, playerRef, playerUuid);
        }
        continue;
      }
      result.add(new BorderPlayer(player, playerRef, playerUuid));
    }
    return result;
  }

  @Nonnull
  private static WorldBorderState worldBorderState(@Nonnull World world) {
    AetherhavenPlugin plugin = AetherhavenPlugin.get();
    if (plugin == null) {
      return new WorldBorderState();
    }
    TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
    return ensureWorldBorderState(world.getName(), townManager, world.getName());
  }

  @Nonnull
  private static WorldBorderState ensureWorldBorderState(
      @Nonnull String worldName, @Nonnull TownManager townManager, @Nonnull String filterWorld) {
    WorldBorderState state = WORLD_BORDER_STATE.computeIfAbsent(worldName, ignored -> new WorldBorderState());
    List<TownRecord> towns = townsInWorld(townManager, filterWorld);
    long signature = computeTownsSignature(towns);
    synchronized (state) {
      if (state.townsSignature != signature) {
        state.towns = List.copyOf(towns);
        state.townsSignature = signature;
        LongSet perimeter = new LongOpenHashSet();
        TownBorderMapRenderer.collectPerimeterChunkIndices(towns, perimeter);
        state.perimeterChunks = perimeter.toLongArray();
        state.townsByPerimeterChunk = TownBorderMapRenderer.buildTownsByPerimeterChunk(towns);
        GEOMETRY_CACHE.remove(worldName);
        PAINT_CACHE.clear();
      }
      return state;
    }
  }

  @Nonnull
  private static List<TownRecord> townsInWorld(@Nonnull TownManager townManager, @Nonnull String worldName) {
    List<TownRecord> all = townManager.allTowns();
    List<TownRecord> filtered = new ArrayList<>();
    for (TownRecord town : all) {
      if (worldName.equals(town.getWorldName())) {
        filtered.add(town);
      }
    }
    return filtered;
  }

  private static void updatePlayerOverlays(
      @Nonnull World world,
      @Nonnull Player player,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid,
      @Nonnull WorldBorderState borderState) {
    if (!playerRef.getPacketHandler().getChannel(NetworkChannel.WorldMap).isWritable()) {
      return;
    }

    AetherhavenPlugin plugin = AetherhavenPlugin.get();
    if (plugin == null) {
      return;
    }

    if (borderState.towns.isEmpty()) {
      return;
    }

    TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
    TownRecord viewerTown = townManager.findTownForPlayerInWorld(playerUuid);
    UUID viewerTownId = viewerTown != null ? viewerTown.getTownId() : null;

    LongSet loaded = WorldMapTrackerCompat.getLoadedChunks(player);
    long[] allCandidates = intersectCandidates(loaded, borderState.perimeterChunks);
    if (allCandidates.length == 0) {
      return;
    }

    long[] workQueue = prioritizeNearestCandidates(player, allCandidates, MAX_SORTED_CANDIDATES);

    WorldMapManager mapManager = world.getWorldMapManager();
    long townsSignature = borderState.townsSignature;

    LongSet previous = LAST_OVERLAY_CHUNKS.get(playerUuid);
    LongSet current = new LongOpenHashSet();
    List<MapChunk> toSend = new ArrayList<>();
    Long2ObjectMap<CachedTile> tileCache =
        PAINT_CACHE.computeIfAbsent(playerUuid, ignored -> new Long2ObjectOpenHashMap<>());
    Long2ObjectMap<CachedGeometry> geometryCache =
        GEOMETRY_CACHE.computeIfAbsent(world.getName(), ignored -> new Long2ObjectOpenHashMap<>());

    LongOpenHashSet candidateSet = new LongOpenHashSet(allCandidates);
    int sentThisTick = 0;

    for (long index : workQueue) {
      if (sentThisTick >= MAX_CHUNKS_PER_PLAYER_PER_TICK) {
        break;
      }

      int mapChunkX = ChunkUtil.xOfChunkIndex(index);
      int mapChunkZ = ChunkUtil.zOfChunkIndex(index);
      MapImage base = resolveMapImage(mapManager, mapChunkX, mapChunkZ);
      if (base == null || !TownMapImagePixels.hasPixelData(base)) {
        tileCache.remove(index);
        continue;
      }

      current.add(index);
      int baseIdentity = System.identityHashCode(base);
      CachedTile cached = tileCache.get(index);
      if (cached == null || !cached.matches(baseIdentity, townsSignature, viewerTownId)) {
        MapImage painted = paintChunk(world.getName(), base, mapChunkX, mapChunkZ, borderState, viewerTownId, geometryCache);
        if (painted == null) {
          tileCache.remove(index);
          continue;
        }
        cached = new CachedTile(painted, baseIdentity, townsSignature, viewerTownId);
        tileCache.put(index, cached);
      }

      if (!cached.needsSend(baseIdentity)) {
        continue;
      }

      toSend.add(new MapChunk(mapChunkX, mapChunkZ, cached.painted));
      cached.markSent(baseIdentity);
      sentThisTick++;
    }

    for (long staleIndex : new LongOpenHashSet(tileCache.keySet())) {
      if (!candidateSet.contains(staleIndex)) {
        tileCache.remove(staleIndex);
      }
    }

    if (previous != null && sentThisTick < MAX_CHUNKS_PER_PLAYER_PER_TICK) {
      for (long removed : previous) {
        if (sentThisTick >= MAX_CHUNKS_PER_PLAYER_PER_TICK) {
          break;
        }
        if (current.contains(removed) || !loaded.contains(removed)) {
          continue;
        }
        int cx = ChunkUtil.xOfChunkIndex(removed);
        int cz = ChunkUtil.zOfChunkIndex(removed);
        MapImage base = resolveMapImage(mapManager, cx, cz);
        if (base != null) {
          MapImage pristine = TownMapImagePixels.cloneImage(base);
          if (pristine != null) {
            toSend.add(new MapChunk(cx, cz, pristine));
            sentThisTick++;
          }
        }
      }
    }

    if (!toSend.isEmpty()) {
      playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(toSend.toArray(MapChunk[]::new), null, null));
    }

    if (current.isEmpty()) {
      LAST_OVERLAY_CHUNKS.remove(playerUuid);
    } else {
      LAST_OVERLAY_CHUNKS.put(playerUuid, current);
    }
  }

  @Nonnull
  private static long[] intersectCandidates(@Nonnull LongSet loaded, @Nonnull long[] perimeter) {
    if (loaded.isEmpty() || perimeter.length == 0) {
      return new long[0];
    }
    LongArrayList out = new LongArrayList();
    if (loaded.size() <= perimeter.length) {
      LongOpenHashSet perimeterSet = new LongOpenHashSet(perimeter);
      for (long index : loaded) {
        if (perimeterSet.contains(index)) {
          out.add(index);
        }
      }
    } else {
      for (long index : perimeter) {
        if (loaded.contains(index)) {
          out.add(index);
        }
      }
    }
    return out.toLongArray();
  }

  private static long[] prioritizeNearestCandidates(
      @Nonnull Player player, @Nonnull long[] candidates, int maxCount) {
    if (candidates.length <= maxCount) {
      sortCandidatesByDistance(player, candidates);
      return candidates;
    }
    int playerChunkX = 0;
    int playerChunkZ = 0;
    Ref<EntityStore> ref = player.getReference();
    if (ref != null && ref.isValid()) {
      TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
      if (transform != null) {
        playerChunkX = ChunkUtil.chunkCoordinate((int) transform.getPosition().x);
        playerChunkZ = ChunkUtil.chunkCoordinate((int) transform.getPosition().z);
      }
    }
    long[] nearest = new long[maxCount];
    int[] bestDist = new int[maxCount];
    Arrays.fill(bestDist, Integer.MAX_VALUE);
    int filled = 0;
    for (long index : candidates) {
      int dx = ChunkUtil.xOfChunkIndex(index) - playerChunkX;
      int dz = ChunkUtil.zOfChunkIndex(index) - playerChunkZ;
      int dist = dx * dx + dz * dz;
      if (filled < maxCount) {
        nearest[filled] = index;
        bestDist[filled] = dist;
        filled++;
        continue;
      }
      int worstSlot = 0;
      for (int i = 1; i < maxCount; i++) {
        if (bestDist[i] > bestDist[worstSlot]) {
          worstSlot = i;
        }
      }
      if (dist >= bestDist[worstSlot]) {
        continue;
      }
      nearest[worstSlot] = index;
      bestDist[worstSlot] = dist;
    }
    long[] out = filled < maxCount ? Arrays.copyOf(nearest, filled) : nearest;
    sortCandidatesByDistance(player, out);
    return out;
  }

  private static void sortCandidatesByDistance(@Nonnull Player player, @Nonnull long[] candidates) {
    int playerChunkX = 0;
    int playerChunkZ = 0;
    Ref<EntityStore> ref = player.getReference();
    if (ref != null && ref.isValid()) {
      TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
      if (transform != null) {
        playerChunkX = ChunkUtil.chunkCoordinate((int) transform.getPosition().x);
        playerChunkZ = ChunkUtil.chunkCoordinate((int) transform.getPosition().z);
      }
    }
    final int pcx = playerChunkX;
    final int pcz = playerChunkZ;
    Long[] boxed = new Long[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      boxed[i] = candidates[i];
    }
    Arrays.sort(
        boxed,
        Comparator.comparingLong(
            idx -> {
              int dx = ChunkUtil.xOfChunkIndex(idx) - pcx;
              int dz = ChunkUtil.zOfChunkIndex(idx) - pcz;
              return (long) dx * dx + (long) dz * dz;
            }));
    for (int i = 0; i < candidates.length; i++) {
      candidates[i] = boxed[i];
    }
  }

  private static long computeTownsSignature(@Nonnull List<TownRecord> towns) {
    long hash = 1L;
    for (TownRecord town : towns) {
      hash = 31L * hash + town.getTownId().getLeastSignificantBits();
      hash = 31L * hash + town.getTownId().getMostSignificantBits();
      hash = 31L * hash + town.getCharterX();
      hash = 31L * hash + town.getCharterZ();
      hash = 31L * hash + town.getTerritoryChunkRadius();
      TownTerritoryClaims.migrateIfNeeded(town);
      for (ClaimedTerritoryChunkRecord c : town.getClaimedTerritoryChunks()) {
        hash = 31L * hash + c.getChunkX();
        hash = 31L * hash + c.getChunkZ();
      }
      hash = 31L * hash + TownPortalTravelColor.resolveHex(town).hashCode();
    }
    return hash;
  }

  private static void clearOverlays(
      @Nonnull World world,
      @Nonnull Player player,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid) {
    PAINT_CACHE.remove(playerUuid);
    LongSet previous = LAST_OVERLAY_CHUNKS.remove(playerUuid);
    if (previous == null || previous.isEmpty()) {
      return;
    }
    if (!playerRef.getPacketHandler().getChannel(NetworkChannel.WorldMap).isWritable()) {
      return;
    }
    WorldMapManager mapManager = world.getWorldMapManager();
    LongSet loaded = WorldMapTrackerCompat.getLoadedChunks(player);
    List<MapChunk> toSend = new ArrayList<>();
    int sent = 0;
    for (long index : previous) {
      if (sent >= MAX_CHUNKS_PER_PLAYER_PER_TICK) {
        break;
      }
      if (!loaded.contains(index)) {
        continue;
      }
      int cx = ChunkUtil.xOfChunkIndex(index);
      int cz = ChunkUtil.zOfChunkIndex(index);
      MapImage base = resolveMapImage(mapManager, cx, cz);
      if (base == null) {
        continue;
      }
      MapImage pristine = TownMapImagePixels.cloneImage(base);
      if (pristine != null) {
        toSend.add(new MapChunk(cx, cz, pristine));
        sent++;
      }
    }
    if (!toSend.isEmpty()) {
      playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(toSend.toArray(MapChunk[]::new), null, null));
    }
  }

  @Nullable
  private static MapImage resolveMapImage(@Nonnull WorldMapManager mapManager, int mapChunkX, int mapChunkZ) {
    return mapManager.getImageIfInMemory(mapChunkX, mapChunkZ);
  }

  @Nullable
  private static MapImage paintChunk(
      @Nonnull String worldName,
      @Nonnull MapImage base,
      int mapChunkX,
      int mapChunkZ,
      @Nonnull WorldBorderState borderState,
      @Nullable UUID viewerTownId,
      @Nonnull Long2ObjectMap<CachedGeometry> geometryCache) {
    long chunkIndex = ChunkUtil.indexChunk(mapChunkX, mapChunkZ);
    int[] townIndices = borderState.townsByPerimeterChunk.get(chunkIndex);
    if (townIndices == null || townIndices.length == 0) {
      return null;
    }

    long townsSignature = borderState.townsSignature;
    CachedGeometry cachedGeometry = geometryCache.get(chunkIndex);
    if (cachedGeometry == null || !cachedGeometry.matches(townsSignature, base.width, base.height)) {
      TownBorderMapRenderer.BorderGeometry geometry =
          TownBorderMapRenderer.collectBorderGeometry(
              mapChunkX, mapChunkZ, base.width, base.height, borderState.towns, townIndices);
      cachedGeometry = new CachedGeometry(geometry, townsSignature, base.width, base.height);
      geometryCache.put(chunkIndex, cachedGeometry);
    }

    if (cachedGeometry.geometry.isEmpty()) {
      return TownMapImagePixels.cloneImage(base);
    }

    int[] colors =
        TownBorderMapRenderer.colorsForGeometry(cachedGeometry.geometry, borderState.towns, viewerTownId);
    return TownMapImagePixels.applySparsePixelColors(
        base, cachedGeometry.geometry.pixelIndices, colors);
  }

  @Nullable
  private static Player findPlayer(@Nonnull World world, @Nonnull UUID playerUuid) {
    for (PlayerRef pref : world.getPlayerRefs()) {
      Ref<EntityStore> ref = pref.getReference();
      if (ref == null || !ref.isValid()) {
        continue;
      }
      UUIDComponent uc = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
      if (uc != null && playerUuid.equals(uc.getUuid())) {
        return ref.getStore().getComponent(ref, Player.getComponentType());
      }
    }
    return null;
  }
}
