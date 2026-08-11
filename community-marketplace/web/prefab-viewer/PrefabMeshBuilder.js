/**
 * Build a Three.js Object3D from a Hytale prefab JSON document.
 */
import * as THREE from "three";
import {
  assetUrl,
  getBlockDef,
  getModelDef,
  resolveCubeFaces,
} from "./BlockCatalog.js?v=7";
import { loadBlockyModel } from "./BlockyModelLoader.js?v=7";

/** @type {Map<string, THREE.Texture>} */
const cubeTexCache = new Map();
const textureLoader = new THREE.TextureLoader();

/**
 * Prefab entity Transform.Rotation matches Rotation3f (pitch/yaw/roll).
 * In-game exports use radians; some legacy asset prefabs store degrees.
 * Quaternion matches Rotation3f.getQuaternion → JOML rotationYXZ(yaw, pitch, roll).
 * @param {any} rot
 * @returns {THREE.Quaternion}
 */
export function entityRotationToQuaternion(rot) {
  let pitch = Number(rot?.Pitch ?? rot?.pitch ?? 0);
  let yaw = Number(rot?.Yaw ?? rot?.yaw ?? 0);
  let roll = Number(rot?.Roll ?? rot?.roll ?? 0);
  if (!Number.isFinite(pitch)) {
    pitch = 0;
  }
  if (!Number.isFinite(yaw)) {
    yaw = 0;
  }
  if (!Number.isFinite(roll)) {
    roll = 0;
  }
  const vals = [pitch, yaw, roll];
  const maxAbs = Math.max(...vals.map((v) => Math.abs(v)));
  // Legacy degrees: any |angle| past a full turn, or near-integer angles beyond π.
  const looksLikeDegrees =
    maxAbs > Math.PI * 2 + 0.05 ||
    (maxAbs > Math.PI + 0.01 && vals.every((v) => Math.abs(v - Math.round(v)) < 1e-3));
  if (looksLikeDegrees) {
    const toRad = Math.PI / 180;
    pitch *= toRad;
    yaw *= toRad;
    roll *= toRad;
  }
  // JOML Quaterniond.rotationYXZ(yaw, pitch, roll) closed form.
  const sx = Math.sin(pitch * 0.5);
  const cx = Math.cos(pitch * 0.5);
  const sy = Math.sin(yaw * 0.5);
  const cy = Math.cos(yaw * 0.5);
  const sz = Math.sin(roll * 0.5);
  const cz = Math.cos(roll * 0.5);
  const x = cy * sx;
  const y = sy * cx;
  const z = sy * sx;
  const w = cy * cx;
  return new THREE.Quaternion(
    x * cz + y * sz,
    y * cz - x * sz,
    w * sz - z * cz,
    w * cz + z * sz
  ).normalize();
}

/**
 * Decode RotationTuple index: (roll*16)+(pitch*4)+yaw, each axis 0..3 → 0/90/180/270°.
 * Hytale applies R = Ry(yaw) * Rx(pitch) * Rz(roll) (see RotationTuple / Rotation3f.rotationYXZ).
 * Three.js Euler order for that composition is "YXZ" (not "ZYX").
 * @param {number} index
 * @returns {THREE.Quaternion}
 */
export function rotationTupleToQuaternion(index) {
  const i = Number(index) || 0;
  const yaw = (i % 4) * (Math.PI / 2);
  const pitch = (Math.floor(i / 4) % 4) * (Math.PI / 2);
  const roll = (Math.floor(i / 16) % 4) * (Math.PI / 2);
  return entityRotationToQuaternion({ Pitch: pitch, Yaw: yaw, Roll: roll });
}

/**
 * @param {number} index
 * @returns {THREE.Euler}
 */
export function rotationTupleToEuler(index) {
  const i = Number(index) || 0;
  const yaw = (i % 4) * (Math.PI / 2);
  const pitch = (Math.floor(i / 4) % 4) * (Math.PI / 2);
  const roll = (Math.floor(i / 16) % 4) * (Math.PI / 2);
  return new THREE.Euler(pitch, yaw, roll, "YXZ");
}

/**
 * Block/prop entity renderer uses EntityScale 2 as 1:1 with 32px blockymodels
 * (see BlockEntitySystems default). Character/NPC models use scale 1 as 1:1 with 64px.
 * @param {any} comps
 * @param {string|null} modelPath
 */
export function entityWorldScale(comps, modelPath = null) {
  const hasBlockStyle = Boolean(
    comps?.BlockEntity || comps?.Prop || comps?.Item || comps?.blockEntity || comps?.prop || comps?.item
  );
  let entityScale = Number(comps?.EntityScale?.Scale ?? comps?.entityScale?.Scale);
  if (!Number.isFinite(entityScale) || entityScale <= 0) {
    entityScale = hasBlockStyle ? 2 : 1;
  }
  const defScale = hasBlockStyle ? entityScale / 2 : entityScale;
  // Character-density models are authored at 64 units/block; our loader uses 1/32.
  const characterDensity = /^(NPC|Characters)\//i.test(String(modelPath || ""));
  return defScale * (characterDensity ? 0.5 : 1);
}

/**
 * @param {any} pos
 * @returns {THREE.Vector3}
 */
export function entityPositionToVector(pos) {
  return new THREE.Vector3(
    Number(pos?.X ?? pos?.x ?? 0) || 0,
    Number(pos?.Y ?? pos?.y ?? 0) || 0,
    Number(pos?.Z ?? pos?.z ?? 0) || 0
  );
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
                holder.quaternion.copy(rotationTupleToQuaternion(b.rotation));
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
              holder.quaternion.copy(rotationTupleToQuaternion(b.rotation));
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
        const transform = comps.Transform || comps.transform || {};
        const pos = transform.Position || transform.position || {};
        const rot = transform.Rotation || transform.rotation || {};

        const holder = new THREE.Group();
        holder.position.copy(entityPositionToVector(pos));
        holder.quaternion.copy(entityRotationToQuaternion(rot));

        let placed = false;
        let modelPath = null;
        let customModelScale = 1;

        const modelId = comps.Model?.Model?.Id || comps.Model?.Id;
        if (modelId) {
          const mdef = getModelDef(modelId);
          if (mdef?.model) {
            const model = await getModel(mdef.model, mdef.texture);
            if (model) {
              modelPath = mdef.model;
              holder.add(model);
              placed = true;
            }
          }
        }

        const itemId = comps.Item?.Item?.Id || comps.Item?.Id;
        if (!placed && itemId) {
          const idef = getBlockDef(itemId);
          customModelScale = Number(idef?.customModelScale);
          if (!Number.isFinite(customModelScale) || customModelScale <= 0) {
            customModelScale = 1;
          }
          if (idef?.itemModel) {
            const model = await getModel(idef.itemModel, idef.itemTexture || null);
            if (model) {
              modelPath = idef.itemModel;
              holder.add(model);
              placed = true;
            }
          } else if (idef?.customModel) {
            const model = await getModel(idef.customModel, idef.customModelTexture || null);
            if (model) {
              modelPath = idef.customModel;
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

        holder.scale.setScalar(entityWorldScale(comps, modelPath) * customModelScale);

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
