/**
 * The tea house's cloth wall panels are authored off-centre inside their block, so their
 * rendered plane depends on the entity yaw convention. The timber posts framing each bay
 * use centred models we already trust, so whichever convention puts the panels in the
 * plane of their posts is the correct one.
 *
 * Usage: node scripts/debug-panel-plane.mjs
 */
import fs from "node:fs";
import * as THREE from "three";

const PREFAB = "../src/main/resources/Server/Prefabs/Hytinys_Japanese_Tea_House.prefab.json";
const POST_IDS = new Set(["Wood_Hardwood_Beam", "Wood_Oak_Branch_Long"]);
// Geometry centres read straight out of each .blockymodel, in blocks from the origin.
const Z_BACK = new THREE.Vector3(0, 0, -1);
const X_LEFT = new THREE.Vector3(-1, 0, 0);
const SUBJECTS = [
  { id: "Cloth_Roof_White_Vertical", local: new THREE.Vector3(0, 16 / 32, -15 / 32), axis: Z_BACK },
  { id: "Cloth_Roof_White_Flap", local: new THREE.Vector3(0, 16 / 32, -15 / 32), axis: Z_BACK },
  { id: "Furniture_Temple_Wind_Sign", local: new THREE.Vector3(0, 13 / 32, -14 / 32), axis: Z_BACK },
  { id: "Bench_Trough", local: new THREE.Vector3(-16 / 32, 16 / 32, 0), axis: X_LEFT },
];

const doc = JSON.parse(fs.readFileSync(PREFAB, "utf8"));

const entities = [];
for (const ent of doc.entities || []) {
  const c = ent.Components || {};
  const id = c.Item?.Item?.Id || c.Item?.Id || "";
  const p = c.Transform?.Position;
  const r = c.HeadRotation?.Rotation || c.Transform?.Rotation || {};
  if (!p) {
    continue;
  }
  entities.push({
    id,
    pos: new THREE.Vector3(p.X, p.Y, p.Z),
    rot: r,
    scale: (Number(c.EntityScale?.Scale) || 2) / 2,
  });
}

const quatFor = (rot, flipModel) => {
  const q = new THREE.Quaternion().setFromEuler(
    new THREE.Euler(Number(rot.Pitch) || 0, Number(rot.Yaw) || 0, Number(rot.Roll) || 0, "YXZ")
  );
  if (flipModel) {
    q.multiply(new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI));
  }
  return q;
};

const posts = entities.filter((e) => POST_IDS.has(e.id));

function evaluate(subject, flipModel) {
  const panels = entities.filter((e) => e.id === subject.id);
  const residuals = [];
  for (const panel of panels) {
    const quat = quatFor(panel.rot, flipModel);
    // Block entities turn around the block centre: a fixed half block, not a scaled one.
    const pivot = new THREE.Vector3(0, 0.5, 0);
    const centre = subject.local
      .clone()
      .multiplyScalar(panel.scale)
      .sub(pivot)
      .applyQuaternion(quat)
      .add(pivot)
      .add(panel.pos);
    const normal = subject.axis.clone().applyQuaternion(quat);

    // Nearest post in the same bay: close in the panel plane, judged along the normal.
    let best = null;
    for (const post of posts) {
      const delta = post.pos.clone().sub(centre);
      const along = Math.abs(delta.dot(normal));
      const inPlane = Math.sqrt(Math.max(delta.lengthSq() - along * along, 0));
      if (inPlane > 1.2 || along > 1.5) {
        continue;
      }
      if (!best || along < best) {
        best = along;
      }
    }
    if (best !== null) {
      residuals.push(best);
    }
  }
  const mean = residuals.reduce((s, v) => s + v, 0) / (residuals.length || 1);
  const within = residuals.filter((v) => v < 0.15).length;
  return { total: panels.length, matched: residuals.length, mean, within };
}

for (const subject of SUBJECTS) {
  console.log(subject.id);
  for (const flip of [false, true]) {
    const r = evaluate(subject, flip);
    console.log(
      `  ${flip ? "model flipped 180" : "as authored     "}  near a post: ${r.matched}/${r.total}` +
        `  mean offset from post plane: ${r.mean.toFixed(3)}  flush (<0.15): ${r.within}`
    );
  }
}
