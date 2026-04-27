package com.hexvane.aetherhaven.autonomy.pathnav;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.pathtool.PathCommitRecord;
import com.hexvane.aetherhaven.pathtool.PathNavPoint;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hexvane.aetherhaven.pathtool.PathToolRegistry;
import com.hypixel.hytale.math.vector.Vector3d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-world path nav: each cemented path is its own ordered polyline. Along a segment you only move between adjacent
 * nav nodes.
 *
 * <p><b>Joining paths:</b> a vertex on path A that is <b>not</b> an endpoint of A never <i>initiates</i> a shortcut
 * onto another path (no mid-A to mid-B). An <b>endpoint</b> of A may connect within {@link
 * AetherhavenPluginConfig#getPathNavJunctionEps} (plus a small soft snap) to <b>any</b> nav node on a different path B
 * (T-junction: side into main midline). Edges are <b>undirected</b>, so travel from main mid toward a side road uses
 * the same link: walk the main polyline to the junction node, cross to the side’s endpoint, then along the side.
 *
 * <p>Routing uses multi-source Dijkstra on the union of all segment vertices. If the NPC is not within {@link
 * AetherhavenPluginConfig#getPathNavEndpointGateRadius} of <b>any</b> path nav point (on any placed segment in the
 * town), no placed-path route is used (the name is historical). Tune the radius, set {@code PathNavPathfindingLog}
 * in config, and use {@code /aetherhaven path navviz} to debug.
 */
public final class PathNavGraphService {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    @Nonnull
    private final Map<UUID, TownPathNetwork> byTown = new HashMap<>();

    public void rebuildAll(@Nonnull PathToolRegistry registry, @Nonnull AetherhavenPluginConfig cfg) {
        byTown.clear();
        Map<UUID, List<PathCommitRecord>> grouped = new HashMap<>();
        for (PathCommitRecord rec : registry.all()) {
            if (rec == null || rec.townId == null || rec.townId.isBlank() || rec.navNodes == null || rec.navNodes.size() < 2) {
                continue;
            }
            try {
                UUID townId = UUID.fromString(rec.townId);
                grouped.computeIfAbsent(townId, k -> new ArrayList<>()).add(rec);
            } catch (IllegalArgumentException ignored) {
                // skip bad id
            }
        }
        for (Map.Entry<UUID, List<PathCommitRecord>> e : grouped.entrySet()) {
            byTown.put(e.getKey(), buildTownNetwork(e.getValue(), cfg));
        }
    }

    /**
     * @param minDistToNetwork horizontal distance to the path (vertices or edge midlines) when the gate failed; {@link
     *     Double#NaN} if not applicable
     */
    public record PathNavFindResult(
        @Nonnull List<Vector3d> waypoints,
        @Nullable String skipReason,
        double directMeters,
        double minDistToNetwork,
        int graphVertexCount
    ) {
        @Nonnull
        public static PathNavFindResult ok(
            @Nonnull List<Vector3d> waypoints,
            double directMeters,
            int graphVertexCount
        ) {
            return new PathNavFindResult(Collections.unmodifiableList(new ArrayList<>(waypoints)), null, directMeters, Double.NaN, graphVertexCount);
        }

        @Nonnull
        public static PathNavFindResult skip(
            @Nonnull String reason,
            double directMeters,
            double minDistToNetwork,
            int graphVertexCount
        ) {
            return new PathNavFindResult(List.of(), reason, directMeters, minDistToNetwork, graphVertexCount);
        }
    }

    @Nonnull
    public List<Vector3d> findRoute(
        @Nonnull UUID townId,
        @Nonnull Vector3d from,
        @Nonnull Vector3d to,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        return findRouteResult(townId, from, to, cfg).waypoints();
    }

    @Nonnull
    public PathNavFindResult findRouteResult(
        @Nonnull UUID townId,
        @Nonnull Vector3d from,
        @Nonnull Vector3d to,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        if (!cfg.isPathNavEnabled()) {
            return PathNavFindResult.skip("PATHNAV_DISABLED", horizontal(from, to), Double.NaN, 0);
        }
        TownPathNetwork net = byTown.get(townId);
        if (net == null || net.vertexCount < 2) {
            return PathNavFindResult.skip("PATHNAV_NO_GRAPH_TOWN", horizontal(from, to), Double.NaN, 0);
        }
        double gateR = cfg.getPathNavEndpointGateRadius();
        double minDist = minHorizontalDistanceToPathNetwork(net, from);
        if (!Double.isFinite(minDist) || minDist > gateR) {
            return PathNavFindResult.skip("PATHNAV_GATE_NETWORK:min=" + String.format("%.2f", minDist) + " gateR=" + String.format("%.2f", gateR), horizontal(from, to), minDist, net.vertexCount);
        }
        double direct = horizontal(from, to);
        double junctionEps = cfg.getPathNavJunctionEps();
        List<List<Neighbor>> adj = net.adjacencyFor(junctionEps);
        int n = net.vertexCount;
        int startV = 0;
        double startSnap = Double.POSITIVE_INFINITY;
        for (int v = 0; v < n; v++) {
            double d = horizontal(from, net.pos.get(v));
            if (d < startSnap) {
                startSnap = d;
                startV = v;
            }
        }
        double[] dist = new double[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        PriorityQueue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(x -> x.cost));
        dist[startV] = 0.0;
        pq.add(new NodeCost(startV, 0.0));
        while (!pq.isEmpty()) {
            NodeCost cur = pq.poll();
            if (cur.cost > dist[cur.node]) {
                continue;
            }
            for (Neighbor nb : adj.get(cur.node)) {
                double nd = cur.cost + nb.w;
                if (nd + 1.0e-9 < dist[nb.to]) {
                    dist[nb.to] = nd;
                    prev[nb.to] = cur.node;
                    pq.add(new NodeCost(nb.to, nd));
                }
            }
        }
        int bestEnd = -1;
        double bestTargetSnap = Double.POSITIVE_INFINITY;
        double bestGraphCost = Double.POSITIVE_INFINITY;
        for (int v = 0; v < n; v++) {
            if (!Double.isFinite(dist[v])) {
                continue;
            }
            double targetSnap = horizontal(net.pos.get(v), to);
            double graphCost = dist[v];
            // End at the network node closest to the target (tie-break by graph cost from start).
            if (targetSnap + 1.0e-6 < bestTargetSnap
                || (Math.abs(targetSnap - bestTargetSnap) <= 1.0e-6 && graphCost < bestGraphCost)) {
                bestTargetSnap = targetSnap;
                bestGraphCost = graphCost;
                bestEnd = v;
            }
        }
        if (bestEnd < 0) {
            return PathNavFindResult.skip("PATHNAV_NO_ROUTE", direct, minDist, n);
        }
        double bestTotal = startSnap + bestGraphCost + bestTargetSnap;
        if (cfg.isPathNavPreferIfShorterOnly() && bestTotal + 1.0e-4 >= direct) {
            return PathNavFindResult.skip("PATHNAV_PREFER_DIRECT:path=" + String.format("%.2f", bestTotal) + " direct=" + String.format("%.2f", direct), direct, minDist, n);
        }
        return PathNavFindResult.ok(rebuildRouteWithRouteOrderedFoot(from, to, bestEnd, net.pos, prev), direct, n);
    }

    public static void logPathfindingSkip(
        @Nonnull AetherhavenPluginConfig cfg,
        @Nonnull String context,
        @Nullable UUID townId,
        @Nullable UUID poiId,
        @Nonnull PathNavFindResult result
    ) {
        if (!cfg.isPathNavPathfindingLog() || result.skipReason() == null) {
            return;
        }
        String tid = townId != null ? townId.toString() : "?";
        String pid = poiId != null ? poiId.toString() : "?";
        if (Double.isNaN(result.minDistToNetwork())) {
            LOG
                .atInfo()
                .log(
                    "[PathNav] %s skip=%s town=%s poi=%s direct=%.2f verts=%d",
                    context,
                    result.skipReason(),
                    tid,
                    pid,
                    result.directMeters(),
                    result.graphVertexCount()
                );
        } else {
            LOG
                .atInfo()
                .log(
                    "[PathNav] %s skip=%s town=%s poi=%s direct=%.2f minDistToNet=%.2f verts=%d",
                    context,
                    result.skipReason(),
                    tid,
                    pid,
                    result.directMeters(),
                    result.minDistToNetwork(),
                    result.graphVertexCount()
                );
        }
    }

    /**
     * Horizontal distance to the path "network" as committed polylines: min of distance to any vertex and distance to
     * any <b>edge</b> between consecutive nav points. Vertex-only distance wrongly fails the gate when the NPC is
     * beside a long, sparsely sampled path segment (nearest vertex far away along the road).
     */
    private static double minHorizontalDistanceToPathNetwork(@Nonnull TownPathNetwork net, @Nonnull Vector3d p) {
        if (net.vertexCount < 1) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Vector3d q : net.pos) {
            best = Math.min(best, horizontal(p, q));
        }
        for (PlacedSegment seg : net.segments) {
            if (seg.nodes.size() < 2) {
                continue;
            }
            for (int i = 0; i + 1 < seg.nodes.size(); i++) {
                Vector3d a = seg.nodes.get(i);
                Vector3d b = seg.nodes.get(i + 1);
                best = Math.min(best, horizontalPointToOpenSegment2d(p.getX(), p.getZ(), a.getX(), a.getZ(), b.getX(), b.getZ()));
            }
        }
        return best;
    }

    /** Min horizontal distance in XZ from P to the segment A–B. */
    private static double horizontalPointToOpenSegment2d(
        double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;
        double abLenSq = abx * abx + abz * abz;
        if (abLenSq < 1.0e-18) {
            return Math.sqrt(apx * apx + apz * apz);
        }
        double t = (apx * abx + apz * abz) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * abx;
        double cz = az + t * abz;
        double dx = px - cx;
        double dz = pz - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Nonnull
    private static List<Vector3d> rebuildRouteWithRouteOrderedFoot(
        @Nonnull Vector3d from,
        @Nonnull Vector3d toWorld,
        int end,
        @Nonnull List<Vector3d> graphPos,
        int[] prev
    ) {
        List<Vector3d> chain = buildDijkstraVertexChain(graphPos, end, prev);
        List<Vector3d> out = reprojectStartOntoDijkstraChain(from, chain);
        if (out.isEmpty() || horizontal(out.get(out.size() - 1), toWorld) > 0.35) {
            out.add(toWorld);
        }
        return out;
    }

    /**
     * Dijkstra path as vertex chain from tree root to {@code end} (excludes the POI/goal), forward travel order, with
     * colocated graph vertices deduplicated.
     */
    @Nonnull
    private static List<Vector3d> buildDijkstraVertexChain(
        @Nonnull List<Vector3d> pos,
        int end,
        int[] prev
    ) {
        ArrayList<Vector3d> rev = new ArrayList<>();
        for (int at = end; at != -1; at = prev[at]) {
            rev.add(pos.get(at));
        }
        ArrayList<Vector3d> out = new ArrayList<>(rev.size());
        for (int i = rev.size() - 1; i >= 0; i--) {
            Vector3d p = rev.get(i);
            if (out.isEmpty() || horizontal(out.get(out.size() - 1), p) > 1.0e-4) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Picks the closest point to {@code from} on the 2D polyline through the Dijkstra chain (in order) and rewrites the
     * start so the villager: (1) walks to that foot of the <b>chosen route</b> first, (2) then follows chain vertices
     * <b>forward</b> toward the goal, dropping any vertices that lie “behind” the foot. This prevents “enter at a random
     * far vertex on the same road” and prevents trimming the real entry in favour of a vertex on the other side of the
     * NPC, which read as walking to the first node then backtracking.
     */
    @Nonnull
    private static List<Vector3d> reprojectStartOntoDijkstraChain(@Nonnull Vector3d from, @Nonnull List<Vector3d> chain) {
        if (chain.isEmpty()) {
            return new ArrayList<>();
        }
        if (chain.size() == 1) {
            return new ArrayList<>(chain);
        }
        double fromX = from.getX();
        double fromZ = from.getZ();
        double bestD2 = Double.POSITIVE_INFINITY;
        int bestI = 0;
        double bestT = 0.0;
        for (int i = 0; i + 1 < chain.size(); i++) {
            Vector3d a = chain.get(i);
            Vector3d b = chain.get(i + 1);
            double t = projectParamOpenSegment2dClamped(fromX, fromZ, a.getX(), a.getZ(), b.getX(), b.getZ());
            Vector3d p = lerp3d(a, b, t);
            double d2 = horizontalDistSq(from, p);
            if (d2 < bestD2) {
                bestD2 = d2;
                bestI = i;
                bestT = t;
            }
        }
        final double endEps = 0.01;
        ArrayList<Vector3d> out = new ArrayList<>(chain.size() + 1);
        if (bestT < endEps) {
            for (int j = bestI; j < chain.size(); j++) {
                out.add(chain.get(j));
            }
        } else if (bestT > 1.0 - endEps) {
            for (int j = bestI + 1; j < chain.size(); j++) {
                out.add(chain.get(j));
            }
        } else {
            Vector3d a = chain.get(bestI);
            Vector3d b = chain.get(bestI + 1);
            out.add(lerp3d(a, b, bestT));
            for (int j = bestI + 1; j < chain.size(); j++) {
                out.add(chain.get(j));
            }
        }
        if (out.size() >= 2) {
            Vector3d a = out.get(0);
            Vector3d c = out.get(1);
            if (horizontalDistSq(a, c) < 0.0004) {
                out.remove(0);
            }
        }
        return out;
    }

    @Nonnull
    private static Vector3d lerp3d(@Nonnull Vector3d a, @Nonnull Vector3d b, double t) {
        return new Vector3d(
            a.getX() + t * (b.getX() - a.getX()),
            a.getY() + t * (b.getY() - a.getY()),
            a.getZ() + t * (b.getZ() - a.getZ())
        );
    }

    private static double projectParamOpenSegment2dClamped(
        double px, double pz, double ax, double az, double bx, double bz
    ) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;
        double abLenSq = abx * abx + abz * abz;
        if (abLenSq < 1.0e-18) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (apx * abx + apz * abz) / abLenSq));
    }

    @Nonnull
    private static List<List<Neighbor>> buildAdjacency(@Nonnull TownPathNetwork net, double junctionEps) {
        int n = net.vertexCount;
        List<List<Neighbor>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int v = 0; v < n; v++) {
            int s = net.segOf.get(v);
            int l = net.localOf.get(v);
            PlacedSegment seg = net.segments.get(s);
            int len = seg.nodes.size();
            if (l > 0) {
                int u = net.vertId(s, l - 1);
                addUndirected(adj, v, u, horizontal(net.pos.get(v), net.pos.get(u)));
            }
            if (l + 1 < len) {
                int u = net.vertId(s, l + 1);
                addUndirected(adj, v, u, horizontal(net.pos.get(v), net.pos.get(u)));
            }
        }
        double jSq = junctionEps * junctionEps;
        int S = net.segments.size();
        boolean[][] strictToSeg = new boolean[n][S];
        for (int s = 0; s < S; s++) {
            PlacedSegment a = net.segments.get(s);
            int la = a.nodes.size();
            if (la < 2) {
                continue;
            }
            int[] aEnds = { 0, la - 1 };
            for (int ea : aEnds) {
                int va = net.vertId(s, ea);
                Vector3d pa = net.pos.get(va);
                for (int t = 0; t < S; t++) {
                    if (t == s) {
                        continue;
                    }
                    PlacedSegment b = net.segments.get(t);
                    int lb = b.nodes.size();
                    if (lb < 2) {
                        continue;
                    }
                    for (int l = 0; l < lb; l++) {
                        int vb = net.vertId(t, l);
                        Vector3d pb = net.pos.get(vb);
                        double dx = pa.getX() - pb.getX();
                        double dz = pa.getZ() - pb.getZ();
                        double d2 = dx * dx + dz * dz;
                        if (d2 <= jSq) {
                            strictToSeg[va][t] = true;
                            double w = Math.sqrt(d2);
                            if (w < 1.0e-6) {
                                w = 1.0e-6;
                            }
                            addUndirected(adj, va, vb, w);
                        }
                    }
                }
            }
        }
        // No "soft" nearest-node stitching: it creates false shortcut edges across nearby-but-disconnected roads,
        // which collapses routes to 1 graph hop (waypoints=2). Segments must touch within junctionEps to connect.
        return adj;
    }

    private static void addUndirected(@Nonnull List<List<Neighbor>> adj, int a, int b, double w) {
        adj.get(a).add(new Neighbor(b, w));
        adj.get(b).add(new Neighbor(a, w));
    }

    @Nonnull
    private static TownPathNetwork buildTownNetwork(@Nonnull List<PathCommitRecord> records, @Nonnull AetherhavenPluginConfig cfg) {
        TownPathNetwork net = new TownPathNetwork();
        int maxNodes = cfg.getPathNavMaxNodesPerTown();
        int used = 0;
        for (PathCommitRecord rec : records) {
            if (rec.navNodes == null || rec.navNodes.size() < 2) {
                continue;
            }
            int need = rec.navNodes.size();
            if (used + need > maxNodes) {
                break;
            }
            List<Vector3d> nodes = new ArrayList<>(need);
            for (PathNavPoint p : rec.navNodes) {
                nodes.add(new Vector3d(p.x, p.y, p.z));
            }
            net.segments.add(new PlacedSegment(nodes));
            used += need;
        }
        net.flatten();
        return net;
    }

    private static double horizontal(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalDistSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static final class PlacedSegment {
        @Nonnull
        final List<Vector3d> nodes;

        PlacedSegment(@Nonnull List<Vector3d> nodes) {
            this.nodes = nodes;
        }
    }

    private static final class TownPathNetwork {
        @Nonnull
        final List<PlacedSegment> segments = new ArrayList<>();
        @Nonnull
        final List<Vector3d> pos = new ArrayList<>();
        @Nonnull
        final List<Integer> segOf = new ArrayList<>();
        @Nonnull
        final List<Integer> localOf = new ArrayList<>();
        int[][] segLocalToVert;
        int vertexCount;
        @Nullable
        private List<List<Neighbor>> cachedAdj;
        private double cachedAdjEps = Double.NaN;

        @Nonnull
        List<List<Neighbor>> adjacencyFor(double junctionEps) {
            if (cachedAdj != null && Math.abs(junctionEps - cachedAdjEps) < 1.0e-6) {
                return cachedAdj;
            }
            cachedAdj = buildAdjacency(this, junctionEps);
            cachedAdjEps = junctionEps;
            return cachedAdj;
        }

        void flatten() {
            pos.clear();
            segOf.clear();
            localOf.clear();
            int S = segments.size();
            segLocalToVert = new int[S][];
            for (int s = 0; s < S; s++) {
                PlacedSegment seg = segments.get(s);
                int len = seg.nodes.size();
                segLocalToVert[s] = new int[len];
                for (int l = 0; l < len; l++) {
                    int vid = pos.size();
                    segLocalToVert[s][l] = vid;
                    pos.add(seg.nodes.get(l));
                    segOf.add(s);
                    localOf.add(l);
                }
            }
            vertexCount = pos.size();
            cachedAdj = null;
            cachedAdjEps = Double.NaN;
        }

        int vertId(int seg, int local) {
            return segLocalToVert[seg][local];
        }
    }

    private record Neighbor(int to, double w) {}

    private record NodeCost(int node, double cost) {}
}
