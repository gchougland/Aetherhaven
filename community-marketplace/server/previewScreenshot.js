import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { processScreenshot, ScreenshotProcessingError } from "./imageProcessing.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const RENDER_TIMEOUT_MS = 45_000;
const MAX_CONCURRENT = 1;

let activeJobs = 0;
/** @type {string[]} */
const queue = [];
/** @type {import("playwright-core").Browser | null} */
let sharedBrowser = null;

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

  async function resolveChromiumExecutable() {
    const fromEnv = String(process.env.CHROMIUM_PATH || process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || "").trim();
    if (fromEnv && fs.existsSync(fromEnv)) {
      return fromEnv;
    }
    for (const candidate of ["/bin/chromium", "/usr/bin/chromium", "/usr/bin/chromium-browser"]) {
      if (fs.existsSync(candidate)) {
        return candidate;
      }
    }
    try {
      const playwright = await import("playwright-core");
      const exe = playwright.chromium.executablePath();
      if (exe && fs.existsSync(exe)) {
        return exe;
      }
    } catch {
      /* no bundled browser */
    }
    return "";
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
        "--use-gl=swiftshader",
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
   * @param {string} submissionId
   */
  function enqueue(submissionId) {
    const id = String(submissionId || "").trim();
    if (!id || queue.includes(id)) {
      return;
    }
    if (queue.length >= 32) {
      log(`[preview-screenshot] queue full, dropping ${id}`);
      return;
    }
    queue.push(id);
    pump();
  }

  function pump() {
    while (activeJobs < MAX_CONCURRENT && queue.length) {
      const id = queue.shift();
      if (!id) {
        break;
      }
      activeJobs += 1;
      runJob(id)
        .catch((err) => log(`[preview-screenshot] failed for ${id}`, err))
        .finally(() => {
          activeJobs -= 1;
          pump();
        });
    }
  }

  /**
   * @param {string} submissionId
   */
  async function runJob(submissionId) {
    const meta = storage.loadSubmissionMeta(submissionId, "pending");
    if (!meta) {
      log(`[preview-screenshot] submission gone: ${submissionId}`);
      return;
    }
    if (storage.countScreenshotsForOwner("pending", submissionId) > 0) {
      log(`[preview-screenshot] skip ${submissionId}: already has screenshots`);
      return;
    }
    if (!assetsReady()) {
      log(`[preview-screenshot] skip ${submissionId}: hytale-assets not synced`);
      return;
    }

    const prefabPath = path.join(storage.submissionDir(submissionId, "pending"), "prefab.prefab.json");
    if (!fs.existsSync(prefabPath)) {
      log(`[preview-screenshot] skip ${submissionId}: missing prefab`);
      return;
    }

    const prefabUrl = `http://127.0.0.1:${port}/internal/pending-prefab/${encodeURIComponent(submissionId)}.json`;
    const pageUrl =
      `http://127.0.0.1:${port}/internal/prefab-render.html` +
      `?prefabUrl=${encodeURIComponent(prefabUrl)}`;

    let browser;
    let context;
    try {
      browser = await getBrowser();
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
      await saveAutoScreenshot(submissionId, meta, Buffer.from(png));
      log(`[preview-screenshot] saved for ${submissionId}`);
    } finally {
      try {
        await context?.close();
      } catch {
        /* ignore */
      }
    }
  }

  /**
   * @param {string} submissionId
   * @param {object} ownerMeta
   * @param {Buffer} buffer
   */
  async function saveAutoScreenshot(submissionId, ownerMeta, buffer) {
    // Re-check after render — owner may have uploaded meanwhile
    if (storage.countScreenshotsForOwner("pending", submissionId) > 0) {
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
        ownerKind: "pending",
        ownerId: submissionId,
        creatorUuid: ownerMeta.creatorUuid || "",
        creatorName: ownerMeta.creatorName || "Unknown",
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

  return { enqueue, shutdown, assetsReady };
}
