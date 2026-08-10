/**
 * Build a Three.js Object3D from a Hytale prefab JSON document.
 */
import * as THREE from "three";
import {
  assetUrl,
  getBlockDef,
  getModelDef,
  resolveCubeFaces,
} from "./BlockCatalog.js?v=4";
import { loadBlockyModel } from "./BlockyModelLoader.js?v=4";

/** @type {Map<string, THREE.Texture>} */
const cubeTexCache = new Map();
const textureLoader = new THREE.TextureLoader();

/**
 * Decode RotationTuple index: (roll*16)+(pitch*4)+yaw, each axis 0..3 → 0/90/180/270°.
 * Apply order: roll Z → pitch X → yaw Y.
 * @param {number} index
 * @returns {THREE.Euler}
 */
export function rotationTupleToEuler(index) {
  const i = Number(index) || 0;
  const yaw = i % 4;
  const pitch = Math.floor(i / 4) % 4;
  const roll = Math.floor(i / 16) % 4;
  const deg = (n) => (n * Math.PI) / 2;
  return new THREE.Euler(deg(pitch), deg(yaw), deg(roll), "ZYX");
}

/**
 * @param {string} path
 * @returns {Promise<THREE.Texture|null>}
 */
function loadCubeTexture(path) {
  if (!path) {
    return Promise.resolve(null);
  }
  const url = assetUrl(path);
  const hit = cubeTexCache.get(url);
  if (hit) {
    return Promise.resolve(hit);
  }
  return new Promise((resolve) => {
    textureLoader.load(
      url,
      (tex) => {
        tex.colorSpace = THREE.SRGBColorSpace;
        tex.magFilter = THREE.NearestFilter;
        tex.minFilter = THREE.NearestFilter;
        tex.generateMipmaps = false;
        cubeTexCache.set(url, tex);
        resolve(tex);
      },
      undefined,
      () => resolve(null)
    );
  });
}

/**
 * @param {string|null} path
 * @param {string|null} [tintHex]
 */
async function cubeMaterial(path, tintHex = null) {
  const map = await loadCubeTexture(path);
  const mat = new THREE.MeshLambertMaterial({
    map: map || undefined,
    color: tintHex ? new THREE.Color(tintHex) : 0xffffff,
    transparent: Boolean(map),
    alphaTest: 0.05,
  });
  if (!map) {
    mat.color = new THREE.Color(tintHex || "#888888");
  }
  return mat;
}

/**
 * @param {any} def
 */
async function buildCubeMesh(def) {
  const faces = resolveCubeFaces(def);
  const tintUp = def?.tintUp || null;
  const materials = await Promise.all([
    cubeMaterial(faces.east, null), // +X
    cubeMaterial(faces.west, null), // -X
    cubeMaterial(faces.up, tintUp), // +Y
    cubeMaterial(faces.down, null), // -Y
    cubeMaterial(faces.south, null), // +Z
    cubeMaterial(faces.north, null), // -Z
  ]);
  return new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), materials);
}

/**
 * @param {any} def
 * @param {number} level
 */
async function buildFluidMesh(def, level) {
  const maxLevel = Math.max(1, Number(def?.maxFluidLevel) || 8);
  const height = Math.max(0.05, Math.min(1, (Number(level) || 1) / maxLevel));
  const faces = resolveCubeFaces(def);
  const texPath = faces.up || faces.north || faces.east || null;
  const map = await loadCubeTexture(texPath);
  const mat = new THREE.MeshLambertMaterial({
    map: map || undefined,
    color: map ? 0xffffff : 0x3a7bd5,
    transparent: true,
    opacity: 0.55,
    depthWrite: false,
  });
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(1, height, 1), mat);
  mesh.position.y = (height - 1) / 2;
  return mesh;
}

/**
 * @param {object} prefab
 * @param {{ onProgress?: (done:number, total:number) => void }} [options]
 * @returns {Promise<{ root: THREE.Group, bounds: THREE.Box3 }>}
 */
export async function buildPrefabMesh(prefab, options = {}) {
  const root = new THREE.Group();
  root.name = "prefab";

  const blocks = Array.isArray(prefab?.blocks) ? prefab.blocks : [];
  const fluids = Array.isArray(prefab?.fluids) ? prefab.fluids : [];
  const entities = Array.isArray(prefab?.entities) ? prefab.entities : [];

  const work = [];
  for (const b of blocks) {
    if (b.filler != null && Number(b.filler) !== 0) {
      continue;
    }
    // Skip blocks from other mods (not in vanilla/Aetherhaven catalog).
    if (!getBlockDef(b.name)) {
      continue;
    }
    work.push({ kind: "block", data: b });
  }
  for (const f of fluids) {
    if (!getBlockDef(f.name)) {
      continue;
    }
    work.push({ kind: "fluid", data: f });
  }
  for (const e of entities) {
    work.push({ kind: "entity", data: e });
  }

  const total = work.length || 1;
  let done = 0;

  // Unique model keys for reuse within this prefab build
  /** @type {Map<string, THREE.Group|null>} */
  const localModelCache = new Map();

  async function getModel(modelPath, texturePath, tintHex = null) {
    const key = `${modelPath}|${texturePath || ""}|${tintHex || ""}`;
    if (localModelCache.has(key)) {
      const base = localModelCache.get(key);
      return base ? base.clone(true) : null;
    }
    const loaded = await loadBlockyModel(modelPath, texturePath, tintHex);
    localModelCache.set(key, loaded);
    return loaded ? loaded.clone(true) : null;
  }

  for (const item of work) {
    try {
      if (item.kind === "block") {
        const b = item.data;
        const def = getBlockDef(b.name);
        if (def) {
          // Many shaped blocks (roofs, stairs, halves) keep DrawType Cube but still set CustomModel.
          let placed = false;
          if (def.customModel) {
            let tint = def.tint || def.tintUp || null;
            const tex = String(def.customModelTexture || "");
            const modelPath = String(def.customModel || "");
            if (
              !tint &&
              (/_GS\.png$/i.test(tex) ||
                /Plant_Grass|Grassplant|Foliage\/Grass|Foliage\/Plants\/Cross/i.test(
                  `${tex} ${modelPath} ${b.name || ""}`
                ))
            ) {
              tint = "#67b62d";
            }
            const model = await getModel(def.customModel, def.customModelTexture || null, tint);
            if (model) {
              const holder = new THREE.Group();
              holder.position.set(Number(b.x) + 0.5, Number(b.y) + 0.5, Number(b.z) + 0.5);
              if (b.rotation) {
                holder.rotation.copy(rotationTupleToEuler(b.rotation));
              }
              model.position.y = -0.5;
              holder.add(model);
              root.add(holder);
              placed = true;
            }
          }
          if (!placed && def.textures) {
            const cube = await buildCubeMesh(def);
            const holder = new THREE.Group();
            holder.position.set(Number(b.x) + 0.5, Number(b.y) + 0.5, Number(b.z) + 0.5);
            if (b.rotation) {
              holder.rotation.copy(rotationTupleToEuler(b.rotation));
            }
            holder.add(cube);
            root.add(holder);
          }
        }
      } else if (item.kind === "fluid") {
        const f = item.data;
        const def = getBlockDef(f.name);
        if (def) {
          const mesh = await buildFluidMesh(def, f.level);
          const holder = new THREE.Group();
          holder.position.set(Number(f.x) + 0.5, Number(f.y) + 0.5, Number(f.z) + 0.5);
          holder.add(mesh);
          root.add(holder);
        }
      } else if (item.kind === "entity") {
        const comps = item.data?.Components || item.data?.components || {};
        const transform = comps.Transform || {};
        const pos = transform.Position || { X: 0, Y: 0, Z: 0 };
        const rot = transform.Rotation || { Pitch: 0, Yaw: 0, Roll: 0 };
        const scale = Number(comps.EntityScale?.Scale ?? comps.Model?.Model?.Scale ?? 1) || 1;

        const holder = new THREE.Group();
        holder.position.set(Number(pos.X) || 0, Number(pos.Y) || 0, Number(pos.Z) || 0);
        holder.rotation.set(Number(rot.Pitch) || 0, Number(rot.Yaw) || 0, Number(rot.Roll) || 0, "YXZ");
        holder.scale.setScalar(scale);

        let placed = false;

        const modelId = comps.Model?.Model?.Id || comps.Model?.Id;
        if (modelId) {
          const mdef = getModelDef(modelId);
          if (mdef?.model) {
            const model = await getModel(mdef.model, mdef.texture);
            if (model) {
              holder.add(model);
              placed = true;
            }
          }
        }

        const itemId = comps.Item?.Item?.Id || comps.Item?.Id;
        if (!placed && itemId) {
          const idef = getBlockDef(itemId);
          if (idef?.itemModel) {
            const model = await getModel(idef.itemModel, idef.itemTexture || null);
            if (model) {
              holder.add(model);
              placed = true;
            }
          } else if (idef?.customModel) {
            const model = await getModel(idef.customModel, idef.customModelTexture || null);
            if (model) {
              holder.add(model);
              placed = true;
            }
          } else if (idef?.textures) {
            const cube = await buildCubeMesh(idef);
            cube.scale.setScalar(0.35);
            cube.position.y = 0.2;
            holder.add(cube);
            placed = true;
          }
        }

        // Skip entities we cannot resolve from vanilla/Aetherhaven catalogs (no placeholders).
        if (placed) {
          root.add(holder);
        }
      }
    } catch (err) {
      console.warn("Prefab cell render failed", item, err);
    }

    done += 1;
    if (options.onProgress && (done % 25 === 0 || done === total)) {
      options.onProgress(done, total);
    }
  }

  const bounds = new THREE.Box3().setFromObject(root);
  if (bounds.isEmpty()) {
    bounds.set(new THREE.Vector3(-1, 0, -1), new THREE.Vector3(1, 2, 1));
  }
  return { root, bounds };
}

export function disposeObject3D(object) {
  // Geometries, materials, and textures are shared via model/texture caches.
  // Disposing them here would leave later clones grey / broken.
  object.traverse(() => {});
}
