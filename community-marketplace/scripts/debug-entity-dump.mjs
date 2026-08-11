/**
 * Dump prefab entities matching a name pattern with every rotation-bearing component,
 * so viewer orientation bugs can be traced to the source data.
 *
 * Usage: node scripts/debug-entity-dump.mjs <prefab> <name regex>
 */
import fs from "node:fs";

const prefabPath = process.argv[2];
const pattern = new RegExp(process.argv[3] || ".", "i");
const doc = JSON.parse(fs.readFileSync(prefabPath, "utf8"));
const entities = doc.entities || doc.Entities || [];

const idOf = (comps) =>
  comps.Item?.Item?.Id ||
  comps.Item?.Id ||
  comps.Model?.Model?.Id ||
  comps.Model?.Id ||
  comps.Prop?.Prop?.Id ||
  "(unknown)";

const rows = [];
for (const ent of entities) {
  const comps = ent.Components || ent.components || {};
  const id = idOf(comps);
  if (!pattern.test(id)) {
    continue;
  }
  rows.push({
    id,
    pos: comps.Transform?.Position || comps.transform?.position,
    transformRot: comps.Transform?.Rotation || comps.transform?.rotation,
    headRot: comps.HeadRotation || comps.headRotation,
    scale: comps.EntityScale || comps.entityScale,
    others: Object.keys(comps).filter(
      (k) => !["Transform", "transform", "HeadRotation", "headRotation", "EntityScale", "entityScale"].includes(k)
    ),
  });
}

console.log(`${rows.length} of ${entities.length} entities match /${pattern.source}/\n`);
const byId = new Map();
for (const r of rows) {
  byId.set(r.id, [...(byId.get(r.id) || []), r]);
}
for (const [id, group] of byId) {
  console.log(`${id}  (x${group.length})  components: ${[...new Set(group.flatMap((g) => g.others))].join(", ")}`);
  for (const r of group) {
    const fmt = (o) =>
      o
        ? Object.entries(o)
            .map(([k, v]) => `${k}=${typeof v === "number" ? v.toFixed(3) : JSON.stringify(v)}`)
            .join(" ")
        : "-";
    console.log(`   pos[${fmt(r.pos)}]`);
    console.log(`     transform.rot[${fmt(r.transformRot)}]  head[${fmt(r.headRot)}]  scale[${fmt(r.scale)}]`);
  }
  console.log("");
}
