import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { processScreenshot, ScreenshotProcessingError } from "./imageProcessing.js";
import { autoSetCoverFromExistingApprovedScreenshots } from "./coverScreenshots.js";
import { resolveChromiumExecutable } from "./chromiumExecutable.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const RENDER_TIMEOUT_MS = 45_000;
const MAX_CONCURRENT = 1;
const MAX_QUEUE = 32;

let activeJobs = 0;
/** @type {{ ownerKind: "pending"|"approved", ownerId: string }[]} */
const queue = [];
/** @type {import("playwright-core").Browser | null} */
let sharedBrowser = null;

/**
 * @param {ReturnType<import("./storage.js").createStorage>} storage
 * @param {number} port
 * @param {"pending"|"approved"} ownerKind
 * @param {string} ownerId
 * @returns {{
 *   ownerKind: "pending"|"approved",
 *   ownerId: string,
 *   meta: object,
 *   prefabPath: string,
 *   prefabUrl: string,
 * } | null}
 */
export function resolvePreviewScreenshotTarget(storage, port, ownerKind, ownerId) {
  const kind = ownerKind === "approved" ? "approved" : "pending";
  const id = String(ownerId || "").trim();
  if (!id) {
    return null;
  }

  if (kind === "pending") {
    const meta = storage.loadSubmissionMeta(id, "pending");
    if (meta) {
      const prefabPath = path.join(storage.submissionDir(id, "pending"), "prefab.prefab.json");
      if (!fs.existsSync(prefabPath)) {
        return null;
      }
      return {
        ownerKind: "pending",
        ownerId: id,
        meta,
        prefabPath,
        prefabUrl: `http://127.0.0.1:${port}/internal/pending-prefab/${encodeURIComponent(id)}.json`,
      };
    }
    const approved = storage.findApprovedBySubmissionId(id);
    const approvedId = String(approved?.id || "").trim();
    if (!approvedId) {
      return null;
    }
    return resolvePreviewScreenshotTarget(storage, port, "approved", approvedId);
  }

  const paths = storage.approvedPaths(id);
  if (!fs.existsSync(paths.meta) || !fs.existsSync(paths.prefab)) {
    return null;
  }
  let meta;
  try {
    meta = JSON.parse(fs.readFileSync(paths.meta, "utf8"));
  } catch {
    return null;
  }
  return {
    ownerKind: "approved",
    ownerId: id,
    meta,
    prefabPath: paths.prefab,
    prefabUrl: `http://127.0.0.1:${port}/internal/approved-prefab/${encodeURIComponent(id)}.json`,
  };
}

/**
 * @param {object} options
 * @param {ReturnType<import("./storage.js").createStorage>} options.storage
 * @param {number} options.port
 * @param {() => string} options.publicBaseUrl — unused; always hit localhost
 * @param {(msg: string, err?: unknown) => void} [options.log]
 */
export function createPreviewScreenshotService(options) {
  const { storage, port } = options;
  const log = options.log || ((msg, err) => (err ? console.warn(msg, err) : console.log(msg)));

  function assetsReady() {
    const override = String(process.env.HYTALE_ASSETS_DIR || "").trim();
    const candidates = [
      override,
      path.join(__dirname, "..", "web", "hytale-assets"),
    ].filter(Boolean);
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

  resolveChromiumExecutable()
    .then((exe) => {
      log(`[preview-screenshot] chromium: ${exe || "not found"}`);
      log(`[preview-screenshot] hytale-assets: ${assetsReady() ? "ready" : "not synced"}`);
    })
    .catch((err) => log("[preview-screenshot] startup check failed", err));

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
   * @param {string} ownerId
   * @param {"pending"|"approved"} [ownerKind]
   */
  function enqueue(ownerId, ownerKind = "pending") {
    const kind = ownerKind === "approved" ? "approved" : "pending";
    const id = String(ownerId || "").trim();
    if (!id) {
      return;
    }
    if (queue.some((job) => job.ownerKind === kind && job.ownerId === id)) {
      return;
    }
    if (queue.length >= MAX_QUEUE) {
      log(`[preview-screenshot] queue full, dropping ${kind}:${id}`);
      return;
    }
    queue.push({ ownerKind: kind, ownerId: id });
    pump();
  }

  function pump() {
    while (activeJobs < MAX_CONCURRENT && queue.length) {
      const job = queue.shift();
      if (!job) {
        break;
      }
      activeJobs += 1;
      runJob(job.ownerKind, job.ownerId)
        .catch((err) => log(`[preview-screenshot] failed for ${job.ownerKind}:${job.ownerId}`, err))
        .finally(() => {
          activeJobs -= 1;
          pump();
        });
    }
  }

  /**
   * @param {"pending"|"approved"} ownerKind
   * @param {string} ownerId
   */
  async function runJob(ownerKind, ownerId) {
    const target = resolvePreviewScreenshotTarget(storage, port, ownerKind, ownerId);
    if (!target) {
      log(`[preview-screenshot] submission gone: ${ownerKind}:${ownerId}`);
      return;
    }
    if (storage.countScreenshotsForOwner(target.ownerKind, target.ownerId) > 0) {
      log(`[preview-screenshot] skip ${target.ownerKind}:${target.ownerId}: already has screenshots`);
      if (target.ownerKind === "approved") {
        autoSetCoverFromExistingApprovedScreenshots(storage, target.ownerId);
      }
      return;
    }
    if (!assetsReady()) {
      log(`[preview-screenshot] skip ${target.ownerKind}:${target.ownerId}: hytale-assets not synced`);
      return;
    }

    const pageUrl =
      `http://127.0.0.1:${port}/internal/prefab-render.html` +
      `?prefabUrl=${encodeURIComponent(target.prefabUrl)}&interactive=0`;

    let context;
    try {
      const browser = await getBrowser();
      context = await browser.newContext({
        viewport: { width: 1280, height: 720 },
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

      const png = await page.screenshot({ type: "png", fullPage: false });
      await saveAutoScreenshot(target.ownerKind, target.ownerId, target.meta, Buffer.from(png));
      log(`[preview-screenshot] saved for ${target.ownerKind}:${target.ownerId}`);
    } finally {
      try {
        await context?.close();
      } catch {
        /* ignore */
      }
    }
  }

  /**
   * @param {"pending"|"approved"} ownerKind
   * @param {string} ownerId
   * @param {object} ownerMeta
   * @param {Buffer} buffer
   */
  async function saveAutoScreenshot(ownerKind, ownerId, ownerMeta, buffer) {
    let kind = ownerKind;
    let id = ownerId;
    let meta = ownerMeta;
    if (kind === "pending" && !storage.loadSubmissionMeta(id, "pending")) {
      const approved = storage.findApprovedBySubmissionId(id);
      const approvedId = String(approved?.id || "").trim();
      if (!approvedId) {
        log(`[preview-screenshot] submission gone before save: ${id}`);
        return;
      }
      kind = "approved";
      id = approvedId;
      meta = approved;
    }
    if (storage.countScreenshotsForOwner(kind, id) > 0) {
      if (kind === "approved") {
        autoSetCoverFromExistingApprovedScreenshots(storage, id);
      }
      return;
    }
    let processed;
    try {
      processed = await processScreenshot(buffer);
    } catch (err) {
      if (err instanceof ScreenshotProcessingError) {
        throw err;
      }
      throw err;
    }

    const crypto = await import("node:crypto");
    const screenshotId = crypto.randomUUID();
    const paths = storage.screenshotPaths(screenshotId, processed.ext);
    fs.mkdirSync(paths.dir, { recursive: true });
    try {
      fs.writeFileSync(paths.image, processed.fullBuffer);
      fs.writeFileSync(paths.card, processed.cardBuffer);
      const shotMeta = {
        screenshotId,
        ownerKind: kind,
        ownerId: id,
        creatorUuid: meta.creatorUuid || "",
        creatorName: meta.creatorName || "Unknown",
        status: "approved",
        source: "auto_preview",
        uploadedAt: new Date().toISOString(),
        approvedAt: new Date().toISOString(),
        mimeType: processed.mimeType,
        ext: processed.ext,
        bytes: processed.fullBuffer.length,
        cardBytes: processed.cardBuffer.length,
      };
      storage.writeScreenshotMeta(shotMeta);
      if (kind === "approved") {
        autoSetCoverFromExistingApprovedScreenshots(storage, id);
      }
    } catch (err) {
      storage.deleteScreenshot(screenshotId);
      throw err;
    }
  }

  async function shutdown() {
    queue.length = 0;
    if (sharedBrowser) {
      try {
        await sharedBrowser.close();
      } catch {
        /* ignore */
      }
      sharedBrowser = null;
    }
  }

  return { enqueue, shutdown, assetsReady, status };
}
