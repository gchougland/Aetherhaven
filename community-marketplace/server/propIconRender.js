import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { processPropIcon, ScreenshotProcessingError } from "./imageProcessing.js";
import { resolvePreviewScreenshotTarget } from "./previewScreenshot.js";
import { resolveChromiumExecutable } from "./chromiumExecutable.js";
import { normalizeFrontFacing } from "./frontFacing.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const RENDER_TIMEOUT_MS = 45_000;
const ICON_VIEWPORT = 512;

/** @type {import("playwright-core").Browser | null} */
let sharedBrowser = null;

/**
 * @param {object} options
 * @param {ReturnType<import("./storage.js").createStorage>} options.storage
 * @param {number} options.port
 * @param {(msg: string, err?: unknown) => void} [options.log]
 */
export function createPropIconRenderService(options) {
  const { storage, port } = options;
  const log = options.log || ((msg, err) => (err ? console.warn(msg, err) : console.log(msg)));

  function assetsReady() {
    const override = String(process.env.HYTALE_ASSETS_DIR || "").trim();
    const candidates = [override, path.join(__dirname, "..", "web", "hytale-assets")].filter(Boolean);
    for (const dir of candidates) {
      if (fs.existsSync(path.join(dir, "catalog", "block_catalog.json"))) {
        return true;
      }
    }
    return false;
  }

  async function status() {
    const chromiumPath = await resolveChromiumExecutable();
    return {
      assetsReady: assetsReady(),
      chromiumFound: Boolean(chromiumPath),
    };
  }

  async function getBrowser() {
    if (sharedBrowser) {
      return sharedBrowser;
    }
    const executablePath = await resolveChromiumExecutable();
    if (!executablePath) {
      throw new Error("Chromium not found. Set CHROMIUM_PATH or install Playwright browsers.");
    }
    const playwright = await import("playwright-core");
    sharedBrowser = await playwright.chromium.launch({
      executablePath,
      headless: true,
      args: [
        "--use-gl=angle",
        "--use-angle=swiftshader",
        "--enable-unsafe-swiftshader",
        "--enable-webgl",
        "--ignore-gpu-blocklist",
        "--no-sandbox",
        "--disable-dev-shm-usage",
      ],
    });
    sharedBrowser.on("disconnected", () => {
      sharedBrowser = null;
    });
    return sharedBrowser;
  }

  /**
   * @param {string} prefabUrl
   * @param {string} [frontFacing]
   * @returns {Promise<Buffer>}
   */
  async function captureIconPng(prefabUrl, frontFacing = "North") {
    const facing = normalizeFrontFacing(frontFacing);
    const pageUrl =
      `http://127.0.0.1:${port}/internal/prefab-render.html` +
      `?prefabUrl=${encodeURIComponent(prefabUrl)}&interactive=0&mode=icon` +
      `&frontFacing=${encodeURIComponent(facing)}`;

    let context;
    try {
      const browser = await getBrowser();
      context = await browser.newContext({
        viewport: { width: ICON_VIEWPORT, height: ICON_VIEWPORT },
        deviceScaleFactor: 1,
      });
      const page = await context.newPage();
      await page.goto(pageUrl, { waitUntil: "domcontentloaded", timeout: RENDER_TIMEOUT_MS });
      await page.waitForFunction(
        () => window.__PREFAB_RENDER_READY__ === true || window.__PREFAB_RENDER_ERROR__,
        null,
        { timeout: RENDER_TIMEOUT_MS }
      );
      const error = await page.evaluate(() => window.__PREFAB_RENDER_ERROR__);
      if (error) {
        throw new Error(String(error));
      }
      // Capture the WebGL canvas (same pixels the website viewer draws), not the page chrome.
      const dataUrl = await page.evaluate(() => {
        const viewer = window.__PREFAB_VIEWER__;
        if (!viewer?.renderer?.domElement) {
          throw new Error("prefab viewer canvas missing");
        }
        viewer.renderer.render(viewer.scene, viewer.camera);
        return viewer.renderer.domElement.toDataURL("image/png");
      });
      const base64 = String(dataUrl || "").replace(/^data:image\/png;base64,/, "");
      if (!base64 || base64 === dataUrl) {
        throw new Error("prefab viewer canvas capture failed");
      }
      const processed = await processPropIcon(Buffer.from(base64, "base64"));
      return processed.iconBuffer;
    } finally {
      try {
        await context?.close();
      } catch {
        /* ignore */
      }
    }
  }

  /**
   * @param {Buffer} prefabBuffer
   * @param {string} [frontFacing]
   * @returns {Promise<Buffer>}
   */
  async function renderPrefabBuffer(prefabBuffer, frontFacing = "North") {
    if (!assetsReady()) {
      throw new Error("hytale-assets not synced");
    }
    const crypto = await import("node:crypto");
    const tempId = crypto.randomUUID();
    const tempDir = path.join(storage.dirs.root, "_icon_render_temp");
    fs.mkdirSync(tempDir, { recursive: true });
    const tempFile = path.join(tempDir, `${tempId}.prefab.json`);
    fs.writeFileSync(tempFile, prefabBuffer);
    try {
      const prefabUrl = `http://127.0.0.1:${port}/internal/temp-prefab/${encodeURIComponent(tempId)}.json`;
      return await captureIconPng(prefabUrl, frontFacing);
    } finally {
      try {
        fs.unlinkSync(tempFile);
      } catch {
        /* ignore */
      }
    }
  }

  /**
   * @param {"pending"|"approved"} ownerKind
   * @param {string} ownerId
   * @param {{ attach?: boolean }} [options]
   */
  async function renderForOwner(ownerKind, ownerId, options = {}) {
    const target = resolvePreviewScreenshotTarget(storage, port, ownerKind, ownerId);
    if (!target) {
      throw new Error("prefab_not_found");
    }
    const iconBuffer = await captureIconPng(target.prefabUrl, target.frontFacing);
    if (options.attach) {
      const iconPath =
        target.ownerKind === "approved"
          ? storage.approvedPaths(target.ownerId).icon
          : path.join(storage.submissionDir(target.ownerId, "pending"), "icon.png");
      fs.mkdirSync(path.dirname(iconPath), { recursive: true });
      fs.writeFileSync(iconPath, iconBuffer);
    }
    return iconBuffer;
  }

  async function shutdown() {
    if (sharedBrowser) {
      try {
        await sharedBrowser.close();
      } catch {
        /* ignore */
      }
      sharedBrowser = null;
    }
  }

  return {
    assetsReady,
    status,
    captureIconPng,
    renderPrefabBuffer,
    renderForOwner,
    shutdown,
    ScreenshotProcessingError,
  };
}
