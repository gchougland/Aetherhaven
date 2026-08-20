/**
 * Interactive Three.js prefab viewer (orbit drag + scroll zoom).
 */
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import { loadCatalogs } from "./BlockCatalog.js?v=29";
import { buildPrefabMesh, disposeObject3D, PREFAB_VIEWER_TRANSFORM_REV } from "./PrefabMeshBuilder.js?v=29";

export { PREFAB_VIEWER_TRANSFORM_REV };

const ICON_ENTER_FULLSCREEN = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 3H3v6M15 3h6v6M9 21H3v-6M15 21h6v-6" /></svg>`;
const ICON_EXIT_FULLSCREEN = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 9h6V3M21 9h-6V3M3 15h6v6M21 15h-6v6" /></svg>`;

export class PrefabViewer {
  /**
   * @param {HTMLElement} container
   * @param {{ assetBase?: string, interactive?: boolean, fullscreenButton?: boolean }} [options]
   */
  constructor(container, options = {}) {
    this.container = container;
    this.assetBase = options.assetBase || "/hytale-assets";
    this.interactive = options.interactive !== false;
    this.hideGrid = options.hideGrid === true;
    this.transparentBackground = options.transparentBackground === true;
    this.transformRev = PREFAB_VIEWER_TRANSFORM_REV;
    this._disposed = false;
    this._root = null;
    this._raf = 0;
    container.dataset.prefabViewerRev = PREFAB_VIEWER_TRANSFORM_REV;

    const width = Math.max(100, container.clientWidth || 640);
    const height = Math.max(100, container.clientHeight || 400);

    this.scene = new THREE.Scene();
    if (this.transparentBackground) {
      this.scene.background = null;
    } else {
      this.scene.background = new THREE.Color(0x1a1418);
    }

    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 500);
    this.camera.position.set(12, 10, 12);

    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: this.transparentBackground,
      preserveDrawingBuffer: true,
    });
    if (this.transparentBackground) {
      this.renderer.setClearColor(0x000000, 0);
    }
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setSize(width, height, false);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    container.appendChild(this.renderer.domElement);
    this.renderer.domElement.style.width = "100%";
    this.renderer.domElement.style.height = "100%";
    this.renderer.domElement.style.display = "block";
    this.renderer.domElement.style.touchAction = "none";
    if (this.transparentBackground) {
      this.renderer.domElement.style.background = "transparent";
    }

    const hemi = new THREE.HemisphereLight(0xfff0e8, 0x2a2030, 0.85);
    this.scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xffe6c8, 1.1);
    sun.position.set(8, 18, 6);
    this.scene.add(sun);
    const fill = new THREE.DirectionalLight(0x88aaff, 0.35);
    fill.position.set(-10, 6, -8);
    this.scene.add(fill);

    this._grid = null;
    if (!this.hideGrid) {
      const grid = new THREE.GridHelper(32, 32, 0x5a4038, 0x3a2824);
      grid.position.y = -0.01;
      this.scene.add(grid);
      this._grid = grid;
    }

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

    this._fullscreenButton = null;
    this._onFullscreenChange = null;
    if (options.fullscreenButton !== false) {
      this._addFullscreenButton();
    }

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

  _addFullscreenButton() {
    if (!(document.fullscreenEnabled || document.webkitFullscreenEnabled)) {
      return;
    }
    const button = document.createElement("button");
    button.type = "button";
    button.className = "prefab-viewer-fullscreen";
    button.addEventListener("click", () => this.toggleFullscreen());
    this.container.appendChild(button);
    this._fullscreenButton = button;

    this._onFullscreenChange = () => {
      const on = this.isFullscreen();
      this.container.classList.toggle("prefab-viewer-fullscreen-active", on);
      button.innerHTML = on ? ICON_EXIT_FULLSCREEN : ICON_ENTER_FULLSCREEN;
      button.title = on ? "Leave full screen" : "Full screen";
      button.setAttribute("aria-label", button.title);
      this.resize();
      // The browser lays the element out after the event, so measure again next frame.
      requestAnimationFrame(() => this.resize());
    };
    this._onFullscreenChange();
    document.addEventListener("fullscreenchange", this._onFullscreenChange);
    document.addEventListener("webkitfullscreenchange", this._onFullscreenChange);
  }

  isFullscreen() {
    const active = document.fullscreenElement || document.webkitFullscreenElement || null;
    return active === this.container;
  }

  toggleFullscreen() {
    if (this.isFullscreen()) {
      const exit = document.exitFullscreen || document.webkitExitFullscreen;
      exit?.call(document);
      return;
    }
    const request = this.container.requestFullscreen || this.container.webkitRequestFullscreen;
    // Rejects when the browser blocks it (no user gesture, or a policy says no).
    request?.call(this.container)?.catch?.(() => {});
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
    // Icon mode: pull back a bit more and use a flatter angle so small props fill the frame
    // without looking like a tiny speck on a dark plate.
    const dist = maxDim * (this.transparentBackground ? 1.9 : 1.55);
    this.controls.target.copy(center);
    if (this.transparentBackground) {
      this.camera.position.set(center.x + dist * 0.95, center.y + dist * 0.4, center.z + dist * 0.55);
    } else {
      this.camera.position.set(center.x + dist * 0.85, center.y + dist * 0.65, center.z + dist * 0.85);
    }
    this.camera.near = Math.max(0.05, dist / 200);
    this.camera.far = Math.max(200, dist * 20);
    this.camera.updateProjectionMatrix();
    this.controls.update();

    if (this.hideGrid) {
      return;
    }

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
    if (this._onFullscreenChange) {
      document.removeEventListener("fullscreenchange", this._onFullscreenChange);
      document.removeEventListener("webkitfullscreenchange", this._onFullscreenChange);
      this._onFullscreenChange = null;
    }
    if (this.isFullscreen()) {
      const exit = document.exitFullscreen || document.webkitExitFullscreen;
      exit?.call(document);
    }
    this.container.classList.remove("prefab-viewer-fullscreen-active");
    this._fullscreenButton?.remove();
    this._fullscreenButton = null;
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
