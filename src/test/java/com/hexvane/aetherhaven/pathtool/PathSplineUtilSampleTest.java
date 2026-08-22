package com.hexvane.aetherhaven.pathtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class PathSplineUtilSampleTest {
    private static final double EPS = 1.0e-4;

    @Test
    void threeNodePathSamplesAllNodePositions() {
        List<PathToolNode> nodes =
            List.of(
                node(0.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 5.5, 90.0)
            );
        List<PathSplineUtil.PathSample> samples = PathSplineUtil.sample(nodes, 2);
        for (PathToolNode n : nodes) {
            assertTrue(
                hasSampleNear(samples, n.getX(), n.getZ()),
                "expected sample near node at " + n.getX() + "," + n.getZ()
            );
        }
    }

    @Test
    void twoNodePathSamplesEndpoints() {
        List<PathToolNode> nodes = List.of(node(0.5, 1.5, 0.5, 0.0), node(10.5, 1.5, 0.5, 0.0));
        List<PathSplineUtil.PathSample> samples = PathSplineUtil.sample(nodes, 2);
        assertTrue(hasSampleNear(samples, 0.5, 0.5));
        assertTrue(hasSampleNear(samples, 10.5, 0.5));
    }

    @Test
    void internalJunctionNotDuplicated() {
        List<PathToolNode> nodes =
            List.of(
                node(0.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 5.5, 90.0)
            );
        List<PathSplineUtil.PathSample> samples = PathSplineUtil.sample(nodes, 2);
        int hits = 0;
        for (PathSplineUtil.PathSample s : samples) {
            if (near(s.position.x(), 5.5, EPS) && near(s.position.z(), 0.5, EPS)) {
                hits++;
            }
        }
        assertEquals(1, hits, "internal junction should appear exactly once");
    }

    @Test
    void sharpTurnProducesMoreSamplesThanStraight() {
        List<PathToolNode> straight =
            List.of(
                node(0.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 0.5, 0.0),
                node(10.5, 1.5, 0.5, 0.0)
            );
        List<PathToolNode> corner =
            List.of(
                node(0.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 0.5, 0.0),
                node(5.5, 1.5, 5.5, 90.0)
            );
        int straightCount = PathSplineUtil.sample(straight, 2).size();
        int cornerCount = PathSplineUtil.sample(corner, 2).size();
        assertTrue(cornerCount > straightCount, "sharp turn should yield denser sampling");
    }

    private static boolean hasSampleNear(List<PathSplineUtil.PathSample> samples, double x, double z) {
        for (PathSplineUtil.PathSample s : samples) {
            if (near(s.position.x(), x, EPS) && near(s.position.z(), z, EPS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static PathToolNode node(double x, double y, double z, double yaw) {
        return new PathToolNode(UUID.randomUUID(), new Vector3d(x, y, z), yaw);
    }
}
