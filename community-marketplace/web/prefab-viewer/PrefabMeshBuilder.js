/**
 * Build a Three.js Object3D from a Hytale prefab JSON document.
 */
import * as THREE from "three";
import {
  assetUrl,
  getBlockDef,
  getModelDef,
  resolveCubeFaces,
} from "./BlockCatalog.js?v=27";
import { loadBlockyModel } from "./BlockyModelLoader.js?v=27";

/** Bump when transform math changes — shown in the viewer so we can confirm the live build. */
export const PREFAB_VIEWER_TRANSFORM_REV = "xform-27";

/** @type {Map<string, THREE.Texture>} */
const cubeTexCache = new Map();
/** @type {Map<string, THREE.MeshLambertMaterial>} */
const cubeMatCache = new Map();
const sharedCubeGeometry = new THREE.BoxGeometry(1, 1, 1);
const textureLoader = new THREE.TextureLoader();

/**
 * @param {any} value catalog customModelTexture (string or weighted array)
 * @returns {string|null}
 */
function resolveModelTexturePath(value) {
  if (!value) {
    return null;
  }
  if (typeof value === "string") {
    return value;
  }
  if (Array.isArray(value)) {
    for (const entry of value) {
      const tex = entry?.Texture || entry?.texture;
      if (typeof tex === "string" && tex) {
        return tex;
      }
    }
  }
  if (typeof value === "object" && typeof value.Texture === "string") {
    return value.Texture;
  }
  return null;
}

/**
 * Parse Pitch/Yaw/Roll from a prefab Transform.Rotation.
 * Live exports use radians; some legacy asset prefabs store degrees.
 * @param {any} rot
 * @returns {{ pitch: number, yaw: number, roll: number }}
 */
export function parseEntityEuler(rot) {
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
  return { pitch, yaw, roll };
}

/**
 * Entity / prop model orientation.
 *
 * Matches Rotation3f.getQuaternion → JOML rotationYXZ(yaw, pitch, roll), which is
 * Three.js Euler order "YXZ" with the stored roll sign.
 *
 * @param {any} rot
 * @returns {THREE.Quaternion}
 */
export function entityRotationToQuaternion(rot) {
  const { pitch, yaw, roll } = parseEntityEuler(rot);
  return new THREE.Quaternion().setFromEuler(new THREE.Euler(pitch, yaw, roll, "YXZ"));
}

/**
 * Blockymodels are authored standing on their origin, so a block entity turns around a
 * point above that origin: the centre of its block, exactly like the grid path, which
 * rotates a model lowered by a flat half block about the block centre.
 *
 * This is a world distance and does not scale with the entity, which is what the Cozy
 * Crossing town hall clock measures: its frame is symmetric and its two hands meet in
 * the middle, and both line up at 0.625 model units at that clock's 0.8 entity scale.
 * See scripts/debug-clock-transform.mjs.
 */
export const BLOCK_ENTITY_PIVOT = 0.5;

/**
 * A placed block only counts as one if it carries BlockEntity. Props and pinned dropped
 * items (the fish shop's hanging fish) look similar in a prefab, but the server treats
 * them as ordinary item entities: no doubled spawn scale, no block pivot, no block model
 * facing, and their body rotation rather than a leftover head rotation.
 * @param {any} comps
 */
export function isBlockEntity(comps) {
  return Boolean(comps?.BlockEntity || comps?.blockEntity);
}

/**
 * Block entities used to store facing in HeadRotation; on load BlockEntitySystems copies
 * that over Transform and drops HeadRotation, so it wins for them. Item entities get no
 * such migration and keep the roll that hangs them on a rope, which HeadRotation lacks.
 *
 * @param {any} comps
 * @returns {any}
 */
export function resolveEntityRotationSource(comps) {
  const head = comps?.HeadRotation?.Rotation || comps?.headRotation?.Rotation || comps?.headRotation?.rotation;
  if (isBlockEntity(comps) && head) {
    return head;
  }
  const transform = comps?.Transform || comps?.transform || {};
  return transform.Rotation || transform.rotation || {};
}

/**
 * Decode RotationTuple index: (roll*16)+(pitch*4)+yaw, each axis 0..3 → 0/90/180/270°.
 * Block renderer expects R = Ry(yaw)*Rx(pitch)*Rz(roll) (see BuilderToolsPlugin /
 * RotationTuple). Same Three.js "YXZ" as entities.
 * @param {number} index
 * @returns {THREE.Quaternion}
 */
export function rotationTupleToQuaternion(index) {
  const i = Number(index) || 0;
  const yaw = (i % 4) * (Math.PI / 2);
  const pitch = (Math.floor(i / 4) % 4) * (Math.PI / 2);
  const roll = (Math.floor(i / 16) % 4) * (Math.PI / 2);
  return new THREE.Quaternion().setFromEuler(new THREE.Euler(pitch, yaw, roll, "YXZ"));
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
 * World scale for prefab entities.
 * Block entity EntityScale is identity-at-2 (EntitySpawnPage multiplies the UI value by
 * BLOCK_ENTITY_BASE_SCALE = 2, and BlockEntitySystems defaults to 2), so render scale
 * is the stored value halved. Item entities store the UI value as-is.
 * @param {any} comps
 * @param {string|null} modelPath
 */
export function entityWorldScale(comps, modelPath = null) {
  const hasBlockStyle = isBlockEntity(comps);
  let entityScale = Number(comps?.EntityScale?.Scale ?? comps?.entityScale?.Scale);
  if (!Number.isFinite(entityScale) || entityScale <= 0) {
    entityScale = hasBlockStyle ? 2 : 1;
  }
  // Character-density models are authored at 64 units/block; our loader uses 1/32.
  const characterDensity = /^(NPC|Characters)\//i.test(String(modelPath || ""));
  const blockEntityScale = hasBlockStyle ? entityScale / 2 : entityScale;
  return blockEntityScale * (characterDensity ? 0.5 : 1);
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
  const key = `${path || ""}|${tintHex || ""}`;
  const hit = cubeMatCache.get(key);
  if (hit) {
    return hit;
  }
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
  cubeMatCache.set(key, mat);
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
  return new THREE.Mesh(sharedCubeGeometry, materials);
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
    const name = String(f?.name || "");
    if (!name || name === "Empty") {
      continue;
    }
    if (!getBlockDef(name)) {
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
    const tex = resolveModelTexturePath(texturePath) || (typeof texturePath === "string" ? texturePath : null);
    const key = `${modelPath}|${tex || ""}|${tintHex || ""}`;
    if (localModelCache.has(key)) {
      const base = localModelCache.get(key);
      return base ? base.clone(true) : null;
    }
    const loaded = await loadBlockyModel(modelPath, tex, tintHex);
    localModelCache.set(key, loaded);
    return loaded ? loaded.clone(true) : null;
  }

  /**
   * @returns {Promise<{ model: THREE.Group, path: string }|null>}
   */
  async function getModelForDef(def, texture, tintHex = null) {
    const paths = [def?.customModel].filter(Boolean);
    for (const path of paths) {
      const model = await getModel(path, texture, tintHex);
      if (model) {
        return { model, path };
      }
    }
    return null;
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
            const tex = resolveModelTexturePath(def.customModelTexture) || "";
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
            const model = (await getModelForDef(def, def.customModelTexture || null, tint))?.model;
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
        const rot = resolveEntityRotationSource(comps);
        const isBlockStyle = isBlockEntity(comps);

        const holder = new THREE.Group();
        holder.position.copy(entityPositionToVector(pos));
        holder.quaternion.copy(entityRotationToQuaternion(rot));

        let placed = false;
        let modelPath = null;
        let customModelScale = 1;

        /** @param {THREE.Object3D} model */
        const addEntityModel = (model) => {
          // An entity faces -Z at yaw 0 but blockymodels are authored facing +Z, for props
          // and creatures alike: a wall panel's front is its +Z side and a fish's head is
          // its +Z end. Without this they face backwards, so a fish hung by the mouth hangs
          // by the tail instead.
          model.rotateY(Math.PI);
          holder.add(model);
        };

        const modelId = comps.Model?.Model?.Id || comps.Model?.Id;
        if (modelId) {
          const mdef = getModelDef(modelId);
          if (mdef?.model) {
            const model = await getModel(mdef.model, mdef.texture);
            if (model) {
              modelPath = mdef.model;
              addEntityModel(model);
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
              addEntityModel(model);
              placed = true;
            }
          } else if (idef?.customModel) {
            const resolved = await getModelForDef(idef, idef.customModelTexture || null);
            if (resolved) {
              modelPath = resolved.path;
              addEntityModel(resolved.model);
              placed = true;
            }
          } else if (idef?.textures) {
            const cube = await buildCubeMesh(idef);
            // Cube geometry is centred, but blockymodels start at their base, so lift it
            // half a block to match before the shared pivot shift is applied.
            cube.position.y += 0.5;
            addEntityModel(cube);
            placed = true;
          }
        }

        const worldScale = entityWorldScale(comps, modelPath) * customModelScale;
        holder.scale.setScalar(worldScale);
        if (isBlockStyle) {
          // Drop the models onto the pivot, then lift the holder by the same amount so the
          // pivot only changes what the entity turns around: unrotated, it still stands on
          // its stored position. Model space is scaled, the world offset is not.
          for (const child of holder.children) {
            child.position.y -= BLOCK_ENTITY_PIVOT / worldScale;
          }
          holder.position.y += BLOCK_ENTITY_PIVOT;
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
    // Keep the tab responsive on entity-heavy prefabs (tea house ~5k cells).
    if (done % 100 === 0) {
      await new Promise((r) => setTimeout(r, 0));
    }
  }

  let bounds;
  try {
    bounds = new THREE.Box3().setFromObject(root);
  } catch (err) {
    console.warn("Prefab bounds failed", err);
    bounds = new THREE.Box3();
  }
  if (!bounds || bounds.isEmpty() || !Number.isFinite(bounds.min.x) || !Number.isFinite(bounds.max.x)) {
    bounds = new THREE.Box3(new THREE.Vector3(-1, 0, -1), new THREE.Vector3(1, 2, 1));
  }
  return { root, bounds };
}

export function disposeObject3D(object) {
  // Geometries, materials, and textures are shared via model/texture caches.
  // Disposing them here would leave later clones grey / broken.
  object.traverse(() => {});
}
