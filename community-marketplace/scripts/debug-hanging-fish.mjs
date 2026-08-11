/**
 * The fish shop hangs fish from ropes by the mouth, so the nose end of each fish model
 * should land on a rope. That makes the ropes a ground truth for whether item entities
 * are scaled and oriented like the game does.
 *
 * Usage: node scripts/debug-hanging-fish.mjs
 */
import fs from "node:fs";
import * as THREE from "three";

const PREFAB = "../run/mods/Hexvane_Aetherhaven/Community/Server/Prefabs/plot_community_0860c172_fishshop.prefab.json";
const ASSETS = "web/hytale-assets/Common";
const NPC_MODEL_UNITS = 64;

const catalog = JSON.parse(fs.readFileSync("web/hytale-assets/catalog/block_catalog.json", "utf8"));
const doc = JSON.parse(fs.readFileSync(PREFAB, "utf8"));

/** Model extent along local Z, in blocks, ignoring node rotations (good enough for a nose). */
function modelZExtent(modelPath) {
  const file = `${ASSETS}/${modelPath}`;
  if (!fs.existsSync(file)) {
    return null;
  }
  const json = JSON.parse(fs.readFileSync(file, "utf8"));
  let min = Infinity;
  let max = -Infinity;
  const walk = (node, z) => {
    const pos = node.position || {};
    const here = z + (Number(pos.z) || 0);
    const shape = node.shape;
    if (shape?.type) {
      const half = (Number(shape.settings?.size?.z) || 0) / 2;
      const off = Number(shape.offset?.z) || 0;
      min = Math.min(min, here + off - half);
      max = Math.max(max, here + off + half);
    }
    (node.children || []).forEach((c) => walk(c, here));
  };
  (json.nodes || []).forEach((n) => walk(n, 0));
  return Number.isFinite(min) ? { min: min / NPC_MODEL_UNITS, max: max / NPC_MODEL_UNITS } : null;
}

const entities = (doc.entities || []).map((e) => {
  const c = e.Components || {};
  return {
    id: c.Item?.Item?.Id || c.Item?.Id || "",
    isBlock: Boolean(c.BlockEntity),
    pos: new THREE.Vector3(c.Transform?.Position?.X, c.Transform?.Position?.Y, c.Transform?.Position?.Z),
    rot: c.Transform?.Rotation || {},
    scale: Number(c.EntityScale?.Scale ?? 2),
  };
});

const ropes = entities.filter((e) => /Deco_Rope/.test(e.id));
const fish = entities.filter((e) => /^Fish_/.test(e.id) && !e.isBlock);

console.log("fish                        mouth end                 nearest rope   distance");
for (const f of fish) {
  const model = catalog[f.id]?.itemModel;
  const extent = model && modelZExtent(model);
  if (!extent) {
    console.log(`${f.id.padEnd(28)}(no model)`);
    continue;
  }
  const quat = new THREE.Quaternion().setFromEuler(
    new THREE.Euler(Number(f.rot.Pitch) || 0, Number(f.rot.Yaw) || 0, Number(f.rot.Roll) || 0, "YXZ")
  );
  // Entity forward is -Z, so the nose is the min-Z end of the model.
  const worldScale = f.scale * 0.5; // NPC models are authored at twice the block density
  const nose = new THREE.Vector3(0, 0, extent.min * worldScale).applyQuaternion(quat).add(f.pos);
  let best = null;
  for (const rope of ropes) {
    const dist = rope.pos.distanceTo(nose);
    if (!best || dist < best.dist) {
      best = { rope, dist };
    }
  }
  console.log(
    `${f.id.padEnd(28)}(${nose.toArray().map((v) => v.toFixed(2)).join(", ")})` +
      `   (${best.rope.pos.toArray().map((v) => v.toFixed(2)).join(", ")})   ${best.dist.toFixed(2)}`
  );
}
