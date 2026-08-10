/**
 * Load .blockymodel JSON into a Three.js Object3D.
 * Geometry rules follow Hytale's BlockyModelBoundsParser (scale 1/32, node quats, box/quad shapes).
 */
import * as THREE from "three";
import { assetUrl } from "./BlockCatalog.js";

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

/**
 * Build UVs for a box face from Hytale textureLayout.
 * @param {{ offset?: {x:number,y:number}, mirror?: {x:boolean,y:boolean}, angle?: number } | null} layout
 * @param {number} faceW model units
 * @param {number} faceH model units
 * @param {number} texW
 * @param {number} texH
 */
function faceUvs(layout, faceW, faceH, texW, texH) {
  const ox = Number(layout?.offset?.x || 0);
  const oy = Number(layout?.offset?.y || 0);
  const mirrorX = Boolean(layout?.mirror?.x);
  const mirrorY = Boolean(layout?.mirror?.y);
  const angle = Number(layout?.angle || 0);

  let u0 = ox / texW;
  let v0 = 1 - (oy + faceH) / texH;
  let u1 = (ox + faceW) / texW;
  let v1 = 1 - oy / texH;

  if (mirrorX) {
    const t = u0;
    u0 = u1;
    u1 = t;
  }
  if (mirrorY) {
    const t = v0;
    v0 = v1;
    v1 = t;
  }

  // Corner order for PlaneGeometry / custom quads: BL, BR, TL, TR in local face space after build
  let corners = [
    [u0, v0],
    [u1, v0],
    [u0, v1],
    [u1, v1],
  ];

  const turns = ((angle % 360) + 360) % 360 / 90;
  for (let i = 0; i < turns; i += 1) {
    // rotate 90° CW in UV space around face center
    corners = [corners[2], corners[0], corners[3], corners[1]];
  }
  return corners;
}

/**
 * Create a textured box mesh in model units (pre-scale).
 * @param {any} shape
 * @param {THREE.Texture} texture
 * @param {number} texW
 * @param {number} texH
 */
function buildBoxMesh(shape, texture, texW, texH) {
  const size = vec3(shape.settings?.size, 1, 1, 1);
  const stretch = vec3(shape.stretch, 1, 1, 1);
  const sx = Math.abs(size.x * stretch.x);
  const sy = Math.abs(size.y * stretch.y);
  const sz = Math.abs(size.z * stretch.z);
  if (sx < 1e-6 && sy < 1e-6 && sz < 1e-6) {
    return null;
  }

  const layout = shape.textureLayout || {};
  const materials = [
    makeFaceMaterial(texture, layout.right || layout.east, sz, sy, texW, texH, shape), // +X
    makeFaceMaterial(texture, layout.left || layout.west, sz, sy, texW, texH, shape), // -X
    makeFaceMaterial(texture, layout.top || layout.up, sx, sz, texW, texH, shape), // +Y
    makeFaceMaterial(texture, layout.bottom || layout.down, sx, sz, texW, texH, shape), // -Y
    makeFaceMaterial(texture, layout.front || layout.south, sx, sy, texW, texH, shape), // +Z
    makeFaceMaterial(texture, layout.back || layout.north, sx, sy, texW, texH, shape), // -Z
  ];

  const geom = new THREE.BoxGeometry(sx || 0.01, sy || 0.01, sz || 0.01);
  // Apply custom UVs per face group (BoxGeometry groups: +x,-x,+y,-y,+z,-z)
  const uvAttr = geom.getAttribute("uv");
  const faceLayouts = [
    layout.right || layout.east,
    layout.left || layout.west,
    layout.top || layout.up,
    layout.bottom || layout.down,
    layout.front || layout.south,
    layout.back || layout.north,
  ];
  const faceSizes = [
    [sz, sy],
    [sz, sy],
    [sx, sz],
    [sx, sz],
    [sx, sy],
    [sx, sy],
  ];
  for (let f = 0; f < 6; f += 1) {
    const uvs = faceUvs(faceLayouts[f], faceSizes[f][0], faceSizes[f][1], texW, texH);
    // Each face: 4 verts in BoxGeometry order
    const base = f * 4;
    // three.js box face UV order: (0,1),(1,1),(0,0),(1,0) mapped to our BL,BR,TL,TR differently
    // Standard BoxGeometry UV per face: bottom-left, bottom-right, top-left, top-right in some versions
    // Use: v0=(u0,v1), v1=(u1,v1), v2=(u0,v0), v3=(u1,v0) matching three's default
    const mapped = [uvs[2], uvs[3], uvs[0], uvs[1]];
    for (let i = 0; i < 4; i += 1) {
      uvAttr.setXY(base + i, mapped[i][0], mapped[i][1]);
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
 * @param {THREE.Texture} texture
 * @param {any} layout
 * @param {number} fw
 * @param {number} fh
 * @param {number} texW
 * @param {number} texH
 * @param {any} shape
 */
function makeFaceMaterial(texture, layout, fw, fh, texW, texH, shape) {
  const mat = new THREE.MeshLambertMaterial({
    map: texture,
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
 * @param {any} shape
 * @param {THREE.Texture} texture
 * @param {number} texW
 * @param {number} texH
 */
function buildQuadMesh(shape, texture, texW, texH) {
  const size = vec3(shape.settings?.size, 1, 1, 0);
  const stretch = vec3(shape.stretch, 1, 1, 1);
  const normal = String(shape.settings?.normal || "+Z");
  let w = Math.abs(size.x * stretch.x) || 0.01;
  let h = Math.abs(size.y * stretch.y) || 0.01;
  if (normal.endsWith("X")) {
    w = Math.abs((size.z || size.x) * stretch.z) || w;
    h = Math.abs(size.y * stretch.y) || h;
  } else if (normal.endsWith("Y")) {
    w = Math.abs(size.x * stretch.x) || w;
    h = Math.abs((size.z || size.y) * stretch.z) || h;
  }

  const layout = shape.textureLayout?.front || shape.textureLayout?.south || null;
  const uvs = faceUvs(layout, w, h, texW, texH);
  const geom = new THREE.PlaneGeometry(w, h);
  const uvAttr = geom.getAttribute("uv");
  // PlaneGeometry: 0=( -0.5,-0.5), 1=(0.5,-0.5), 2=(-0.5,0.5), 3=(0.5,0.5) → BL BR TL TR
  uvAttr.setXY(0, uvs[0][0], uvs[0][1]);
  uvAttr.setXY(1, uvs[1][0], uvs[1][1]);
  uvAttr.setXY(2, uvs[2][0], uvs[2][1]);
  uvAttr.setXY(3, uvs[3][0], uvs[3][1]);
  uvAttr.needsUpdate = true;

  const mat = new THREE.MeshLambertMaterial({
    map: texture,
    transparent: true,
    alphaTest: 0.05,
    side: THREE.DoubleSide,
  });
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
  return mesh;
}

/**
 * @param {any} node
 * @param {THREE.Object3D} parent
 * @param {THREE.Texture} texture
 * @param {number} texW
 * @param {number} texH
 */
function accumulateNode(node, parent, texture, texW, texH) {
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
      mesh = buildBoxMesh(shape, texture, texW, texH);
    } else if (type === "quad") {
      mesh = buildQuadMesh(shape, texture, texW, texH);
    }
    if (mesh) {
      // Shape offset already folded into group position via bounds parser logic;
      // mesh itself is centered at origin of this node group.
      group.add(mesh);
    }
  }

  if (Array.isArray(node.children)) {
    for (const child of node.children) {
      accumulateNode(child, group, texture, texW, texH);
    }
  }
}

/**
 * @param {string} modelPath catalog-relative e.g. Blocks/.../Chair.blockymodel
 * @param {string|null} texturePath
 * @returns {Promise<THREE.Group|null>}
 */
export async function loadBlockyModel(modelPath, texturePath = null) {
  const key = `${modelPath}|${texturePath || ""}`;
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
      // Prefer same-name texture next to model
      const base = modelPath.replace(/\.blockymodel$/i, "");
      texPath = `${base}_Texture.png`;
    }

    let texture;
    let texW = 64;
    let texH = 64;
    try {
      texture = await loadTexture(assetUrl(texPath));
      if (texture.image) {
        texW = texture.image.width || texW;
        texH = texture.image.height || texH;
      }
    } catch {
      // Try Texture.png / model-name.png
      const fallbacks = [
        modelPath.replace(/[^/]+$/, "Texture.png"),
        modelPath.replace(/\.blockymodel$/i, ".png"),
        modelPath.replace(/\.blockymodel$/i, "_Textures/Texture.png"),
      ];
      for (const fb of fallbacks) {
        try {
          texture = await loadTexture(assetUrl(fb));
          if (texture.image) {
            texW = texture.image.width || texW;
            texH = texture.image.height || texH;
          }
          break;
        } catch {
          /* try next */
        }
      }
    }
    if (!texture) {
      texture = new THREE.Texture();
      texture.needsUpdate = true;
    }

    const root = new THREE.Group();
    root.name = modelPath;
    const nodes = Array.isArray(json.nodes) ? json.nodes : [];
    for (const node of nodes) {
      accumulateNode(node, root, texture, texW, texH);
    }
    root.scale.setScalar(BLOCK_SCALE);
    return root;
  })();

  modelCache.set(key, promise);
  const result = await promise;
  return result ? result.clone(true) : null;
}

export function clearModelCaches() {
  for (const tex of textureCache.values()) {
    tex.dispose();
  }
  textureCache.clear();
  modelCache.clear();
}
