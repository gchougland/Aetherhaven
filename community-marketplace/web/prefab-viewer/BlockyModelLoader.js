/**
 * Load .blockymodel JSON into a Three.js Object3D.
 * Geometry rules follow Hytale's BlockyModelBoundsParser (scale 1/32, node quats, box/quad shapes).
 * UV layout matches the official Blockbench codec (pixel offset + face size from settings.size, not stretch).
 */
import * as THREE from "three";
import { assetUrl } from "./BlockCatalog.js?v=29";

const BLOCK_SCALE = 1 / 32;

/** @type {Map<string, THREE.Texture>} */
const textureCache = new Map();
/** @type {Map<string, Promise<THREE.Group|null>>} */
const modelCache = new Map();

const textureLoader = new THREE.TextureLoader();

/**
 * @param {string} url
 * @returns {Promise<THREE.Texture>}
 */
function loadTexture(url) {
  const hit = textureCache.get(url);
  if (hit) {
    return Promise.resolve(hit);
  }
  return new Promise((resolve, reject) => {
    textureLoader.load(
      url,
      (tex) => {
        tex.colorSpace = THREE.SRGBColorSpace;
        tex.magFilter = THREE.NearestFilter;
        tex.minFilter = THREE.NearestFilter;
        tex.generateMipmaps = false;
        tex.wrapS = THREE.ClampToEdgeWrapping;
        tex.wrapT = THREE.ClampToEdgeWrapping;
        textureCache.set(url, tex);
        resolve(tex);
      },
      undefined,
      reject
    );
  });
}

function vec3(obj, dx = 0, dy = 0, dz = 0) {
  if (!obj) {
    return new THREE.Vector3(dx, dy, dz);
  }
  return new THREE.Vector3(
    Number(obj.x ?? dx),
    Number(obj.y ?? dy),
    Number(obj.z ?? dz)
  );
}

function quat(obj) {
  if (!obj) {
    return new THREE.Quaternion();
  }
  return new THREE.Quaternion(
    Number(obj.x || 0),
    Number(obj.y || 0),
    Number(obj.z || 0),
    Number(obj.w ?? 1)
  ).normalize();
}

function switchIndices(arr, i1, i2) {
  const t = arr[i1];
  arr[i1] = arr[i2];
  arr[i2] = t;
}

/**
 * Compute Blockbench-style face UV rectangle in pixel space: [x0, y0, x1, y1]
 * (texture origin top-left, +Y down), then map to Three.js UVs for verts
 * ordered TL, TR, BL, BR → (matches BoxGeometry / PlaneGeometry).
 *
 * @param {any} layout
 * @param {number} faceW settings.size component for face width (not stretch)
 * @param {number} faceH settings.size component for face height (not stretch)
 * @param {number} denomW
 * @param {number} denomH
 * @returns {Array<[number, number]>} four [u,v] for TL,TR,BL,BR
 */
function faceUvsThree(layout, faceW, faceH, denomW, denomH) {
  const ox = Number(layout?.offset?.x || 0);
  const oy = Number(layout?.offset?.y || 0);
  let uvSize = [Math.abs(faceW) || 0.01, Math.abs(faceH) || 0.01];
  let uvMirror = [layout?.mirror?.x ? -1 : 1, layout?.mirror?.y ? -1 : 1];
  const angle = Number(layout?.angle || 0);

  /** @type {[number, number, number, number]} */
  let result;
  switch (angle) {
    case 90: {
      switchIndices(uvSize, 0, 1);
      switchIndices(uvMirror, 0, 1);
      uvMirror[0] *= -1;
      result = [
        ox,
        oy + uvSize[1] * uvMirror[1],
        ox + uvSize[0] * uvMirror[0],
        oy,
      ];
      break;
    }
    case 270: {
      switchIndices(uvSize, 0, 1);
      switchIndices(uvMirror, 0, 1);
      uvMirror[1] *= -1;
      result = [
        ox + uvSize[0] * uvMirror[0],
        oy,
        ox,
        oy + uvSize[1] * uvMirror[1],
      ];
      break;
    }
    case 180: {
      uvMirror[0] *= -1;
      uvMirror[1] *= -1;
      result = [
        ox + uvSize[0] * uvMirror[0],
        oy + uvSize[1] * uvMirror[1],
        ox,
        oy,
      ];
      break;
    }
    default: {
      result = [
        ox,
        oy,
        ox + uvSize[0] * uvMirror[0],
        oy + uvSize[1] * uvMirror[1],
      ];
      break;
    }
  }

  const [x0, y0, x1, y1] = result;
  const toUv = (x, y) => [x / denomW, 1 - y / denomH];
  return [toUv(x0, y0), toUv(x1, y0), toUv(x0, y1), toUv(x1, y1)];
}

/**
 * Walk model JSON and find how large the UV layout is (for atlas vs texture mismatch).
 * @param {any[]} nodes
 * @returns {{ maxX: number, maxY: number }}
 */
function measureUvLayout(nodes) {
  let maxX = 0;
  let maxY = 0;

  /** @param {any} node */
  function walk(node) {
    const shape = node?.shape;
    if (shape && shape.visible !== false) {
      const size = shape.settings?.size || {};
      const sx = Math.abs(Number(size.x) || 0);
      const sy = Math.abs(Number(size.y) || 0);
      const sz = Math.abs(Number(size.z) || 0);
      const layout = shape.textureLayout || {};
      for (const [key, face] of Object.entries(layout)) {
        if (!face || typeof face !== "object") {
          continue;
        }
        let fw = sx;
        let fh = sy;
        const k = String(key);
        if (k === "top" || k === "bottom" || k === "up" || k === "down") {
          fw = sx;
          fh = sz || sy;
        } else if (k === "left" || k === "right" || k === "east" || k === "west") {
          fw = sz || sx;
          fh = sy;
        } else if (k === "front" || k === "back" || k === "north" || k === "south") {
          fw = sx;
          fh = sy;
        }
        const angle = Number(face.angle || 0);
        if (angle === 90 || angle === 270) {
          const t = fw;
          fw = fh;
          fh = t;
        }
        const ox = Number(face.offset?.x || 0);
        const oy = Number(face.offset?.y || 0);
        // Mirrors can extend "backwards" from the offset.
        const x0 = ox;
        const x1 = ox + (face.mirror?.x ? -fw : fw);
        const y0 = oy;
        const y1 = oy + (face.mirror?.y ? -fh : fh);
        maxX = Math.max(maxX, x0, x1);
        maxY = Math.max(maxY, y0, y1);
      }
    }
    if (Array.isArray(node?.children)) {
      for (const child of node.children) {
        walk(child);
      }
    }
  }

  for (const node of nodes || []) {
    walk(node);
  }
  return { maxX, maxY };
}

/**
 * @param {THREE.Texture} texture
 * @param {any} shape
 * @param {string|null} tintHex
 */
function makeFaceMaterial(texture, shape, tintHex = null) {
  const mat = new THREE.MeshLambertMaterial({
    map: texture,
    color: tintHex ? new THREE.Color(tintHex) : 0xffffff,
    transparent: true,
    alphaTest: 0.05,
    side: shape.doubleSided ? THREE.DoubleSide : THREE.FrontSide,
  });
  if (shape.shadingMode === "fullbright") {
    mat.emissive = new THREE.Color(0xffffff);
    mat.emissiveMap = texture;
    mat.emissiveIntensity = 0.35;
  }
  return mat;
}

/**
 * Create a textured box mesh in model units (pre-scale).
 * @param {any} shape
 * @param {THREE.Texture} texture
 * @param {number} denomW
 * @param {number} denomH
 * @param {string|null} tintHex
 */
function buildBoxMesh(shape, texture, denomW, denomH, tintHex = null) {
  const size = vec3(shape.settings?.size, 1, 1, 1);
  const stretch = vec3(shape.stretch, 1, 1, 1);
  const sx = Math.abs(size.x * stretch.x);
  const sy = Math.abs(size.y * stretch.y);
  const sz = Math.abs(size.z * stretch.z);
  if (sx < 1e-6 && sy < 1e-6 && sz < 1e-6) {
    return null;
  }

  // UV face sizes use settings.size (Blockbench), not stretched geometry size.
  const uvX = Math.abs(size.x) || 0.01;
  const uvY = Math.abs(size.y) || 0.01;
  const uvZ = Math.abs(size.z) || 0.01;

  const layout = shape.textureLayout || {};
  const sharedMat = makeFaceMaterial(texture, shape, tintHex);
  // Blockbench omits faces with no layout (texture=null). Rendering those
  // anyway creates bogus "extra planes" on roofs and similar models.
  const layoutKeys = Object.keys(layout);
  const hideMissingFaces = layoutKeys.length > 0;
  const hiddenMat = hideMissingFaces
    ? new THREE.MeshBasicMaterial({ visible: false })
    : null;

  const geom = new THREE.BoxGeometry(sx || 0.01, sy || 0.01, sz || 0.01);
  const uvAttr = geom.getAttribute("uv");
  const faceLayouts = [
    layout.right || layout.east,
    layout.left || layout.west,
    layout.top || layout.up,
    layout.bottom || layout.down,
    layout.front || layout.south,
    layout.back || layout.north,
  ];
  // Face UV width/height per Hytale direction mapping
  const faceSizes = [
    [uvZ, uvY], // right/+X
    [uvZ, uvY], // left/-X
    [uvX, uvZ], // top/+Y
    [uvX, uvZ], // bottom/-Y
    [uvX, uvY], // front/+Z
    [uvX, uvY], // back/-Z
  ];
  /** @type {THREE.Material[]} */
  const materials = [];
  for (let f = 0; f < 6; f += 1) {
    const faceLayout = faceLayouts[f];
    if (hideMissingFaces && !faceLayout) {
      materials.push(hiddenMat);
      continue;
    }
    materials.push(sharedMat);
    const uvs = faceUvsThree(faceLayout, faceSizes[f][0], faceSizes[f][1], denomW, denomH);
    const base = f * 4;
    // BoxGeometry verts per face: TL, TR, BL, BR
    for (let i = 0; i < 4; i += 1) {
      uvAttr.setXY(base + i, uvs[i][0], uvs[i][1]);
    }
  }
  uvAttr.needsUpdate = true;

  const mesh = new THREE.Mesh(geom, materials);
  if (stretch.x < 0) {
    mesh.scale.x *= -1;
  }
  if (stretch.y < 0) {
    mesh.scale.y *= -1;
  }
  if (stretch.z < 0) {
    mesh.scale.z *= -1;
  }
  return mesh;
}

/**
 * @param {any} shape
 * @param {THREE.Texture} texture
 * @param {number} denomW
 * @param {number} denomH
 * @param {string|null} tintHex
 */
function buildQuadMesh(shape, texture, denomW, denomH, tintHex = null) {
  const size = vec3(shape.settings?.size, 1, 1, 0);
  const stretch = vec3(shape.stretch, 1, 1, 1);
  const normal = String(shape.settings?.normal || "+Z");
  let w = Math.abs(size.x * stretch.x) || 0.01;
  let h = Math.abs(size.y * stretch.y) || 0.01;
  let uvW = Math.abs(size.x) || 0.01;
  let uvH = Math.abs(size.y) || 0.01;
  if (normal.endsWith("X")) {
    w = Math.abs((size.z || size.x) * stretch.z) || w;
    h = Math.abs(size.y * stretch.y) || h;
    uvW = Math.abs(size.z || size.x) || uvW;
    uvH = Math.abs(size.y) || uvH;
  } else if (normal.endsWith("Y")) {
    w = Math.abs(size.x * stretch.x) || w;
    h = Math.abs((size.z || size.y) * stretch.z) || h;
    uvW = Math.abs(size.x) || uvW;
    uvH = Math.abs(size.z || size.y) || uvH;
  }

  const layout = shape.textureLayout?.front || shape.textureLayout?.south || null;
  const uvs = faceUvsThree(layout, uvW, uvH, denomW, denomH);
  const geom = new THREE.PlaneGeometry(w, h);
  const uvAttr = geom.getAttribute("uv");
  // PlaneGeometry verts: 0=TL, 1=TR, 2=BL, 3=BR (see three.js PlaneGeometry / BoxGeometry)
  uvAttr.setXY(0, uvs[0][0], uvs[0][1]);
  uvAttr.setXY(1, uvs[1][0], uvs[1][1]);
  uvAttr.setXY(2, uvs[2][0], uvs[2][1]);
  uvAttr.setXY(3, uvs[3][0], uvs[3][1]);
  uvAttr.needsUpdate = true;

  const mat = makeFaceMaterial(texture, { ...shape, doubleSided: true }, tintHex);
  const mesh = new THREE.Mesh(geom, mat);
  if (normal === "+X") {
    mesh.rotation.y = Math.PI / 2;
  } else if (normal === "-X") {
    mesh.rotation.y = -Math.PI / 2;
  } else if (normal === "+Y") {
    mesh.rotation.x = -Math.PI / 2;
  } else if (normal === "-Y") {
    mesh.rotation.x = Math.PI / 2;
  } else if (normal === "-Z") {
    mesh.rotation.y = Math.PI;
  }
  if (stretch.x < 0) {
    mesh.scale.x *= -1;
  }
  if (stretch.y < 0) {
    mesh.scale.y *= -1;
  }
  return mesh;
}

/**
 * @param {any} node
 * @param {THREE.Object3D} parent
 * @param {THREE.Texture} texture
 * @param {number} denomW
 * @param {number} denomH
 * @param {string|null} tintHex
 */
function accumulateNode(node, parent, texture, denomW, denomH, tintHex) {
  const shape = node.shape;
  const visible = !shape || shape.visible !== false;

  const position = vec3(node.position);
  const orientation = quat(node.orientation);
  const offset = shape ? vec3(shape.offset) : new THREE.Vector3();

  const localPos = offset.clone().applyQuaternion(orientation).add(position);
  const group = new THREE.Group();
  group.position.copy(localPos);
  group.quaternion.copy(orientation);
  parent.add(group);

  if (visible && shape) {
    const type = shape.type || "none";
    let mesh = null;
    if (type === "box") {
      mesh = buildBoxMesh(shape, texture, denomW, denomH, tintHex);
    } else if (type === "quad") {
      mesh = buildQuadMesh(shape, texture, denomW, denomH, tintHex);
    }
    if (mesh) {
      group.add(mesh);
    }
  }

  if (Array.isArray(node.children)) {
    for (const child of node.children) {
      accumulateNode(child, group, texture, denomW, denomH, tintHex);
    }
  }
}

/**
 * @param {string} modelPath catalog-relative e.g. Blocks/.../Chair.blockymodel
 * @param {string|null} texturePath
 * @param {string|null} [tintHex]
 * @returns {Promise<THREE.Group|null>}
 */
export async function loadBlockyModel(modelPath, texturePath = null, tintHex = null) {
  const tintKey = tintHex ? String(tintHex).toLowerCase() : "";
  const key = `${modelPath}|${texturePath || ""}|${tintKey}`;
  if (modelCache.has(key)) {
    const cached = await modelCache.get(key);
    return cached ? cached.clone(true) : null;
  }

  const promise = (async () => {
    const modelUrl = assetUrl(modelPath);
    let json;
    try {
      const res = await fetch(modelUrl);
      if (!res.ok) {
        return null;
      }
      json = await res.json();
    } catch {
      return null;
    }

    let texPath = texturePath;
    if (!texPath) {
      const base = modelPath.replace(/\.blockymodel$/i, "");
      texPath = `${base}_Texture.png`;
    }

    let texture = null;
    let texW = 64;
    let texH = 64;
    const tryPaths = [
      texPath,
      modelPath.replace(/\.blockymodel$/i, ".png"),
      modelPath.replace(/\.blockymodel$/i, "_Texture.png"),
      modelPath.replace(/[^/]+$/, "Texture.png"),
      modelPath.replace(/\.blockymodel$/i, "_Textures/Texture.png"),
    ].filter(Boolean);

    for (const candidate of tryPaths) {
      try {
        // Clone per model+tint path so wrap mode can differ (SimpleUV vs atlas).
        const baseTex = await loadTexture(assetUrl(candidate));
        texture = baseTex.clone();
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.needsUpdate = true;
        if (baseTex.image) {
          texture.image = baseTex.image;
          texW = baseTex.image.width || texW;
          texH = baseTex.image.height || texH;
        }
        break;
      } catch {
        /* try next */
      }
    }
    if (!texture) {
      // Visible fallback so missing skins are obvious, not silent black.
      const data = new Uint8Array([180, 180, 180, 255]);
      texture = new THREE.DataTexture(data, 1, 1, THREE.RGBAFormat);
      texture.needsUpdate = true;
      texture.magFilter = THREE.NearestFilter;
      texture.minFilter = THREE.NearestFilter;
    }

    const nodes = Array.isArray(json.nodes) ? json.nodes : [];
    const { maxX, maxY } = measureUvLayout(nodes);
    // Stairs_SimpleUV etc. pack UVs in a larger virtual atlas than the BlockTexture.
    // Repeat so those tiles sample the block skin instead of clamping to a flat edge.
    if (maxX > texW + 1 || maxY > texH + 1) {
      texture.wrapS = THREE.RepeatWrapping;
      texture.wrapT = THREE.RepeatWrapping;
    }

    // Greyscale / biome-tinted skins need a color multiply. Default to grass green.
    let resolvedTint = tintHex;
    if (
      !resolvedTint &&
      (/_GS\.png$/i.test(String(texPath || "")) ||
        /Grassplant|\/Grass\/|Foliage\/Plants\/Cross/i.test(String(texPath || modelPath || "")))
    ) {
      resolvedTint = "#67b62d";
    }

    const root = new THREE.Group();
    root.name = modelPath;
    root.userData.hytaleCachedModel = true;
    for (const node of nodes) {
      accumulateNode(node, root, texture, texW, texH, resolvedTint);
    }
    root.scale.setScalar(BLOCK_SCALE);
    return root;
  })();

  modelCache.set(key, promise);
  try {
    const result = await promise;
    return result ? result.clone(true) : null;
  } catch (err) {
    // Don't poison the cache with a rejected promise (entity-heavy prefabs retry the same model).
    modelCache.delete(key);
    console.warn("Blocky model load failed", modelPath, err);
    return null;
  }
}

export function clearModelCaches() {
  for (const tex of textureCache.values()) {
    tex.dispose();
  }
  textureCache.clear();
  modelCache.clear();
}
