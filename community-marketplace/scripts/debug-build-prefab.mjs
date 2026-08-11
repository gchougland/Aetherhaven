/**
 * Builds a prefab through the viewer's real buildPrefabMesh in Node and reports what
 * geometry came out, so "nothing is drawn" bugs can be caught without a browser.
 * Textures always fail here (no DOM image loading); only geometry is meaningful.
 *
 * Usage: node scripts/debug-build-prefab.mjs <prefab> [name regex]
 */
import fs from "node:fs";

const ASSET_BASE = "web/hytale-assets";

globalThis.fetch = async (url) => {
  const file = String(url).replace(/^\/+/, "");
  if (!fs.existsSync(file)) {
    return { ok: false, status: 404 };
  }
  return { ok: true, status: 200, json: async () => JSON.parse(fs.readFileSync(file, "utf8")) };
};

// three's TextureLoader needs a DOM image; fail every load so builds still complete.
globalThis.document = {
  createElementNS: () => {
    const listeners = {};
    const el = {
      addEventListener: (t, f) => ((listeners[t] ||= []).push(f)),
      removeEventListener: () => {},
    };
    Object.defineProperty(el, "src", {
      set() {
        setTimeout(() => (listeners.error || []).forEach((f) => f(new Error("no images in node"))), 0);
      },
      get: () => "",
    });
    return el;
  },
};
globalThis.createImageBitmap = undefined;

// The builder imports the catalog with a cache-bust query, and a different specifier is a
// different module instance in ESM, so load the catalog through the exact same one.
const builderSource = fs.readFileSync("web/prefab-viewer/PrefabMeshBuilder.js", "utf8");
const catalogSpecifier = builderSource.match(/from "(\.\/BlockCatalog\.js[^"]*)"/)[1];
const catalog = await import(catalogSpecifier.replace("./", "../web/prefab-viewer/"));
await catalog.loadCatalogs(ASSET_BASE);
const { buildPrefabMesh } = await import("../web/prefab-viewer/PrefabMeshBuilder.js");

const prefabPath = process.argv[2];
const filter = process.argv[3] ? new RegExp(process.argv[3], "i") : null;
const doc = JSON.parse(fs.readFileSync(prefabPath, "utf8"));

const entityId = (e) => {
  const c = e.Components || e.components || {};
  return c.Item?.Item?.Id || c.Item?.Id || c.Model?.Model?.Id || "(unknown)";
};

const subjects = [
  // Filler blocks are the buried interior the prefab tool marks; the viewer skips them.
  ...(doc.blocks || [])
    .filter((b) => !b.filler && (!filter || filter.test(b.name)))
    .map((b) => ({ kind: "block", key: b.name, data: b })),
  ...(doc.entities || []).filter((e) => !filter || filter.test(entityId(e))).map((e) => ({ kind: "entity", key: entityId(e), data: e })),
];

console.log(`${prefabPath}\nbuilding ${subjects.length} matching cells one at a time\n`);

const summary = new Map();
for (const subject of subjects) {
  const slice = {
    ...doc,
    blocks: subject.kind === "block" ? [subject.data] : [],
    fluids: [],
    entities: subject.kind === "entity" ? [subject.data] : [],
  };
  const { root } = await buildPrefabMesh(slice);
  let meshes = 0;
  root.traverse((o) => {
    if (o.isMesh) {
      meshes += 1;
    }
  });
  const key = `${subject.kind} ${subject.key}`;
  const entry = summary.get(key) || { drawn: 0, empty: 0, sample: null };
  if (meshes > 0) {
    entry.drawn += 1;
    if (!entry.sample) {
      const holder = root.children[0];
      holder?.updateMatrixWorld(true);
      entry.sample = holder
        ? `pos=(${holder.position.toArray().map((v) => v.toFixed(3)).join(", ")})` +
          ` scale=${holder.scale.x.toFixed(3)} meshes=${meshes}`
        : `meshes=${meshes}`;
    }
  } else {
    entry.empty += 1;
  }
  summary.set(key, entry);
}

for (const [key, entry] of [...summary].sort((a, b) => b[1].empty - a[1].empty)) {
  const flag = entry.empty > 0 ? "NOT DRAWN" : "drawn    ";
  console.log(`${flag}  ${key}  drawn=${entry.drawn} empty=${entry.empty}`);
  if (entry.sample) {
    console.log(`           ${entry.sample}`);
  }
}
