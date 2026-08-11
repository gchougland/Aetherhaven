/**
 * Interactive Three.js prefab viewer (orbit drag + scroll zoom).
 */
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { loadCatalogs } from "./BlockCatalog.js?v=24";
import { buildPrefabMesh, disposeObject3D, PREFAB_VIEWER_TRANSFORM_REV } from "./PrefabMeshBuilder.js?v=24";

export { PREFAB_VIEWER_TRANSFORM_REV };

export class PrefabViewer {
  /**
   * @param {HTMLElement} container
   * @param {{ assetBase?: string, interactive?: boolean }} [options]
   */
  constructor(container, options = {}) {
    this.container = container;
    this.assetBase = options.assetBase || "/hytale-assets";
    this.interactive = options.interactive !== false;
    this.transformRev = PREFAB_VIEWER_TRANSFORM_REV;
    this._disposed = false;
    this._root = null;
    this._raf = 0;
    container.dataset.prefabViewerRev = PREFAB_VIEWER_TRANSFORM_REV;

    const width = Math.max(100, container.clientWidth || 640);
    const height = Math.max(100, container.clientHeight || 400);

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x1a1418);

    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 500);
    this.camera.position.set(12, 10, 12);

    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: false,
      preserveDrawingBuffer: true,
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setSize(width, height, false);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    container.appendChild(this.renderer.domElement);
    this.renderer.domElement.style.width = "100%";
    this.renderer.domElement.style.height = "100%";
    this.renderer.domElement.style.display = "block";
    this.renderer.domElement.style.touchAction = "none";

    const hemi = new THREE.HemisphereLight(0xfff0e8, 0x2a2030, 0.85);
    this.scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xffe6c8, 1.1);
    sun.position.set(8, 18, 6);
    this.scene.add(sun);
    const fill = new THREE.DirectionalLight(0x88aaff, 0.35);
    fill.position.set(-10, 6, -8);
    this.scene.add(fill);

    const grid = new THREE.GridHelper(32, 32, 0x5a4038, 0x3a2824);
    grid.position.y = -0.01;
    this.scene.add(grid);
    this._grid = grid;

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.enablePan = true;
    this.controls.enableZoom = this.interactive;
    this.controls.enableRotate = this.interactive;
    this.controls.minDistance = 2;
    this.controls.maxDistance = 200;
    this.controls.target.set(0, 2, 0);

    this._onResize = () => this.resize();
    window.addEventListener("resize", this._onResize);

    this._animate = () => {
      if (this._disposed) {
        return;
      }
      this._raf = requestAnimationFrame(this._animate);
      this.controls.update();
      this.renderer.render(this.scene, this.camera);
    };
    this._animate();
  }

  resize() {
    if (this._disposed) {
      return;
    }
    const width = Math.max(100, this.container.clientWidth || 640);
    const height = Math.max(100, this.container.clientHeight || 400);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  /**
   * @param {string} prefabUrl
   * @param {{ onProgress?: (done:number, total:number) => void }} [options]
   */
  async loadPrefabUrl(prefabUrl, options = {}) {
    const res = await fetch(prefabUrl);
    if (!res.ok) {
      throw new Error(`Could not load prefab (${res.status})`);
    }
    const prefab = await res.json();
    return this.loadPrefab(prefab, options);
  }

  /**
   * @param {object} prefab
   * @param {{ onProgress?: (done:number, total:number) => void }} [options]
   */
  async loadPrefab(prefab, options = {}) {
    await loadCatalogs(this.assetBase);
    if (this._root) {
      this.scene.remove(this._root);
      disposeObject3D(this._root);
      this._root = null;
    }
    const { root, bounds } = await buildPrefabMesh(prefab, options);
    if (this._disposed) {
      disposeObject3D(root);
      return;
    }
    this._root = root;
    this.scene.add(root);
    this.fitToBounds(bounds);
    // One extra frame so textures settle before screenshot consumers read the canvas.
    this.renderer.render(this.scene, this.camera);
  }

  /**
   * @param {THREE.Box3} bounds
   */
  fitToBounds(bounds) {
    const size = bounds.getSize(new THREE.Vector3());
    const center = bounds.getCenter(new THREE.Vector3());
    const maxDim = Math.max(size.x, size.y, size.z, 1);
    const dist = maxDim * 1.55;
    this.controls.target.copy(center);
    this.camera.position.set(center.x + dist * 0.85, center.y + dist * 0.65, center.z + dist * 0.85);
    this.camera.near = Math.max(0.05, dist / 200);
    this.camera.far = Math.max(200, dist * 20);
    this.camera.updateProjectionMatrix();
    this.controls.update();

    const gridSize = Math.max(8, Math.ceil(maxDim * 1.5 / 4) * 4);
    if (this._grid) {
      this.scene.remove(this._grid);
      this._grid.geometry?.dispose();
      this._grid.material?.dispose?.();
    }
    this._grid = new THREE.GridHelper(gridSize, Math.min(64, gridSize), 0x5a4038, 0x3a2824);
    this._grid.position.set(center.x, bounds.min.y - 0.02, center.z);
    this.scene.add(this._grid);
  }

  /**
   * @param {"image/png"|"image/webp"} [type]
   * @param {number} [quality]
   * @returns {Promise<Blob|null>}
   */
  captureBlob(type = "image/png", quality = 0.92) {
    return new Promise((resolve) => {
      this.renderer.render(this.scene, this.camera);
      this.renderer.domElement.toBlob((blob) => resolve(blob), type, quality);
    });
  }

  dispose() {
    if (this._disposed) {
      return;
    }
    this._disposed = true;
    cancelAnimationFrame(this._raf);
    window.removeEventListener("resize", this._onResize);
    this.controls.dispose();
    if (this._root) {
      this.scene.remove(this._root);
      disposeObject3D(this._root);
      this._root = null;
    }
    this.renderer.dispose();
    if (this.renderer.domElement.parentElement === this.container) {
      this.container.removeChild(this.renderer.domElement);
    }
  }
}

/**
 * Mount helper used by the building modal and the headless render page.
 * @param {HTMLElement} container
 * @param {string} prefabUrl
 * @param {{ assetBase?: string, interactive?: boolean, onProgress?: Function }} [options]
 */
export async function mountPrefabViewer(container, prefabUrl, options = {}) {
  const viewer = new PrefabViewer(container, options);
  await viewer.loadPrefabUrl(prefabUrl, { onProgress: options.onProgress });
  return viewer;
}
