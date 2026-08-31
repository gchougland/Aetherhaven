/**
 * Apply the hold-last pose from a .blockyanim (open trapdoors, doors, etc.).
 */
import * as THREE from "three";
import { assetUrl } from "./BlockCatalog.js?v=44";

/** @type {Map<string, Promise<object|null>>} */
const animCache = new Map();

/**
 * @param {string} animPath
 * @returns {Promise<object|null>}
 */
async function loadBlockyAnim(animPath) {
  const key = String(animPath || "");
  if (!key) {
    return null;
  }
  if (animCache.has(key)) {
    return animCache.get(key);
  }
  const promise = (async () => {
    try {
      const res = await fetch(assetUrl(key));
      if (!res.ok) {
        return null;
      }
      return await res.json();
    } catch {
      return null;
    }
  })();
  animCache.set(key, promise);
  return promise;
}

/**
 * @param {THREE.Object3D} root
 * @param {string} animPath
 */
export async function applyBlockyAnimationPose(root, animPath) {
  const anim = await loadBlockyAnim(animPath);
  if (!anim?.nodeAnimations) {
    return;
  }
  /** @type {Map<string, THREE.Object3D>} */
  const byName = new Map();
  root.traverse((obj) => {
    if (obj?.name) {
      byName.set(obj.name, obj);
    }
  });
  for (const [name, track] of Object.entries(anim.nodeAnimations)) {
    const node = byName.get(name);
    if (!node || !track || typeof track !== "object") {
      continue;
    }
    const orients = Array.isArray(track.orientation) ? track.orientation : [];
    if (orients.length) {
      const delta = orients[orients.length - 1]?.delta;
      if (delta && Number.isFinite(Number(delta.w))) {
        const q = new THREE.Quaternion(
          Number(delta.x) || 0,
          Number(delta.y) || 0,
          Number(delta.z) || 0,
          Number(delta.w) || 1
        );
        node.quaternion.multiply(q);
      }
    }
    const positions = Array.isArray(track.position) ? track.position : [];
    if (positions.length) {
      const delta = positions[positions.length - 1]?.delta;
      if (delta) {
        node.position.x += Number(delta.x) || 0;
        node.position.y += Number(delta.y) || 0;
        node.position.z += Number(delta.z) || 0;
      }
    }
  }
}

export function clearAnimationCaches() {
  animCache.clear();
}
