/**
 * Offline check for prefab entity transforms: rasterizes the Cozy Crossing town hall
 * clock face in the XY plane so rotation/scale conventions can be compared to the
 * in-game screenshot without running the site.
 *
 * Usage: node scripts/debug-clock-transform.mjs
 */
import fs from "node:fs";
import * as THREE from "three";

const PREFAB = "web/debug/cozy_town_hall.prefab.json";

const doc = JSON.parse(fs.readFileSync(PREFAB, "utf8"));
const entities = [];
for (const entity of doc.Entities || doc.entities || []) {
  const comps = entity.Components || {};
  const transform = comps.Transform || {};
  if (!transform.Position) {
    continue;
  }
  entities.push({
    id: comps.Item?.Item?.Id || comps.Model?.Model?.Id || "",
    pos: transform.Position,
    rot: transform.Rotation || {},
    scale: Number(comps.EntityScale?.Scale ?? 2),
  });
}

/** Blockymodel extents in blocks plus the node origin offset along local +Y. */
function modelInfo(id) {
  if (/Half/.test(id)) {
    return { size: new THREE.Vector3(1, 0.5, 1), nodeY: 8 / 32 };
  }
  if (/Beam/.test(id)) {
    return { size: new THREE.Vector3(0.25, 1, 0.25), nodeY: 16 / 32 };
  }
  return null;
}

function quatFor(rot, negateRoll) {
  const pitch = Number(rot.Pitch || 0);
  const yaw = Number(rot.Yaw || 0);
  const roll = Number(rot.Roll || 0);
  return new THREE.Quaternion().setFromEuler(
    new THREE.Euler(pitch, yaw, negateRoll ? -roll : roll, "YXZ")
  );
}

function render({ negateRoll, scaleDiv, pivot }) {
  const W = 78;
  const H = 34;
  const minX = 7.6;
  const maxX = 11.9;
  const minY = 6.9;
  const maxY = 10.2;
  const grid = Array.from({ length: H }, () => Array(W).fill("."));

  for (const item of entities) {
    const info = modelInfo(item.id);
    if (!info) {
      continue;
    }
    const scale = item.scale / scaleDiv;
    const quat = quatFor(item.rot, negateRoll);
    const half = info.size.clone().multiplyScalar(0.5 * scale);
    const center = new THREE.Vector3(item.pos.X, item.pos.Y, item.pos.Z);
    center
      .add(new THREE.Vector3(0, pivot, 0))
      .add(new THREE.Vector3(0, info.nodeY * scale - pivot, 0).applyQuaternion(quat));
    const glyph = /Beam/.test(item.id) ? "#" : "O";

    for (let a = -1; a <= 1.0001; a += 0.04) {
      for (let b = -1; b <= 1.0001; b += 0.08) {
        const world = new THREE.Vector3(a * half.x, b * half.y, 0)
          .applyQuaternion(quat)
          .add(center);
        const gx = Math.round(((world.x - minX) / (maxX - minX)) * (W - 1));
        const gy = Math.round((1 - (world.y - minY) / (maxY - minY)) * (H - 1));
        if (gx >= 0 && gx < W && gy >= 0 && gy < H) {
          if (grid[gy][gx] === "." || glyph === "#") {
            grid[gy][gx] = glyph;
          }
        }
      }
    }
  }
  return grid.map((row) => row.join("")).join("\n");
}

/**
 * The clock frame was built symmetric, so the pivot can be solved for instead of guessed:
 * scan the pivot height in world blocks above the entity position and keep the value that
 * makes every mirrored pair of slabs line up.
 */
function fitPivotShift() {
  const byKey = new Map();
  for (const item of entities) {
    if (!/Half/.test(item.id)) {
      continue;
    }
    byKey.set(`${item.pos.X.toFixed(2)},${item.pos.Y.toFixed(2)}`, item);
  }
  const at = (x, y) => byKey.get(`${x.toFixed(2)},${y.toFixed(2)}`);

  const mirrorX = [
    [at(8.44, 7.97), at(10.83, 7.83)],
    [at(8.44, 8.51), at(10.83, 8.42)],
    [at(8.81, 9.24), at(11.11, 9.21)],
    [at(9.27, 7.58), at(10.73, 7.6)],
    [at(9.55, 9.54), at(10.36, 9.54)],
    [at(9.55, 7.45), at(10.35, 7.45)],
  ];
  const mirrorY = [
    [at(9.55, 9.54), at(9.55, 7.45)],
    [at(10.36, 9.54), at(10.35, 7.45)],
    [at(8.81, 9.24), at(9.27, 7.58)],
    [at(11.11, 9.21), at(10.73, 7.6)],
    [at(8.44, 8.51), at(8.44, 7.97)],
    [at(10.83, 8.42), at(10.83, 7.83)],
  ];
  if ([...mirrorX, ...mirrorY].some((pair) => pair.some((p) => !p))) {
    throw new Error("clock slab lookup failed");
  }

  const centreOf = (item, pivot) => {
    const info = modelInfo(item.id);
    const scale = item.scale / 2;
    const quat = quatFor(item.rot, false);
    return new THREE.Vector3(item.pos.X, item.pos.Y, item.pos.Z)
      .add(new THREE.Vector3(0, pivot, 0))
      .add(new THREE.Vector3(0, info.nodeY * scale - pivot, 0).applyQuaternion(quat));
  };

  return (pivot) => {
    const xs = mirrorX.map(([a, b]) => (centreOf(a, pivot).x + centreOf(b, pivot).x) / 2);
    const ys = mirrorY.map(([a, b]) => (centreOf(a, pivot).y + centreOf(b, pivot).y) / 2);
    const cx = xs.reduce((s, v) => s + v, 0) / xs.length;
    const cy = ys.reduce((s, v) => s + v, 0) / ys.length;
    let residual = 0;
    for (const [a, b] of mirrorX) {
      const pa = centreOf(a, pivot);
      const pb = centreOf(b, pivot);
      residual += ((pa.x + pb.x) / 2 - cx) ** 2 + (pa.y - pb.y) ** 2;
    }
    for (const [a, b] of mirrorY) {
      const pa = centreOf(a, pivot);
      const pb = centreOf(b, pivot);
      residual += ((pa.y + pb.y) / 2 - cy) ** 2 + (pa.x - pb.x) ** 2;
    }
    return { residual, cx, cy };
  };
}

const symmetry = fitPivotShift();
const fitResidual = (pivot) => symmetry(pivot).residual;

/**
 * Independent check: the two clock hands (a different model and scale from the frame)
 * should meet at the middle, so their inner tips give a second read on the same shift.
 */
function handGap(pivot) {
  const beams = entities.filter((e) => /Beam/.test(e.id));
  const tips = beams.map((beam) => {
    const info = modelInfo(beam.id);
    const scale = beam.scale / 2;
    const quat = quatFor(beam.rot, false);
    const axis = new THREE.Vector3(0, 1, 0).applyQuaternion(quat);
    const centre = new THREE.Vector3(beam.pos.X, beam.pos.Y, beam.pos.Z)
      .add(new THREE.Vector3(0, pivot, 0))
      .add(axis.clone().multiplyScalar(info.nodeY * scale - pivot));
    const half = axis.clone().multiplyScalar(0.5 * scale);
    return [centre.clone().sub(half), centre.clone().add(half)];
  });
  let best = Infinity;
  for (const a of tips[0]) {
    for (const b of tips[1]) {
      best = Math.min(best, a.distanceTo(b));
    }
  }
  return best;
}

console.log("pivot | frame residual | hand gap");
for (let pivot = 0.3; pivot <= 0.75001; pivot += 0.05) {
  console.log(
    `${pivot.toFixed(2).padStart(5)} | ${fitResidual(pivot).toFixed(4).padStart(14)} | ${handGap(pivot).toFixed(3)}`
  );
}

let best = null;
for (let pivot = -0.5; pivot <= 1.5001; pivot += 0.005) {
  const { residual, cx, cy } = symmetry(pivot);
  if (!best || residual < best.residual) {
    best = { pivot, residual, cx, cy };
  }
}
console.log(
  `best world pivot = ${best.pivot.toFixed(3)} blocks (residual ${best.residual.toFixed(4)}), ` +
    `clock centre = ${best.cx.toFixed(3)}, ${best.cy.toFixed(3)}`
);

const variants = [
  { label: "pivot 0.500 (block centre)", negateRoll: false, scaleDiv: 2, pivot: 0.5 },
  { label: "pivot 0.400", negateRoll: false, scaleDiv: 2, pivot: 0.4 },
];

for (const variant of variants) {
  console.log(`\n===== ${variant.label} =====`);
  console.log(render(variant));
}
