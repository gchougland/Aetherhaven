export const SCREENSHOT_FULL_MAX_EDGE = 1920;
export const SCREENSHOT_CARD_MAX_EDGE = 800;
export const SCREENSHOT_FULL_WEBP_QUALITY = 82;
export const SCREENSHOT_CARD_WEBP_QUALITY = 75;
export const PROP_ICON_SIZE = 64;

export class ScreenshotProcessingError extends Error {
  /**
   * @param {string} message
   * @param {string} [code]
   */
  constructor(message, code = "screenshot_invalid") {
    super(message);
    this.name = "ScreenshotProcessingError";
    this.code = code;
  }
}

/** @type {typeof import("sharp") | null} */
let sharpModule = null;

/**
 * Lazy-load sharp so a missing native binary does not crash process startup
 * (Railway healthcheck runs before any upload).
 * @returns {Promise<typeof import("sharp")>}
 */
async function loadSharp() {
  if (sharpModule) {
    return sharpModule;
  }
  try {
    const mod = await import("sharp");
    sharpModule = mod.default || mod;
    return sharpModule;
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    throw new ScreenshotProcessingError(
      `Image processing unavailable on this server (${detail}).`,
      "screenshot_processing_unavailable"
    );
  }
}

/**
 * Resize and encode an uploaded screenshot into full + card WebP variants.
 *
 * @param {Buffer} buffer
 * @returns {Promise<{ fullBuffer: Buffer, cardBuffer: Buffer, mimeType: "image/webp", ext: "webp" }>}
 */
export async function processScreenshot(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
    throw new ScreenshotProcessingError("Screenshot file is empty or invalid.", "screenshot_invalid");
  }

  const sharp = await loadSharp();

  let pipeline;
  try {
    pipeline = sharp(buffer, { failOn: "error" }).rotate();
    await pipeline.metadata();
  } catch (err) {
    if (err instanceof ScreenshotProcessingError) {
      throw err;
    }
    throw new ScreenshotProcessingError(
      "Could not read screenshot. Use a valid JPEG, PNG, or WebP image.",
      "screenshot_invalid"
    );
  }

  try {
    const [fullBuffer, cardBuffer] = await Promise.all([
      pipeline
        .clone()
        .resize({
          width: SCREENSHOT_FULL_MAX_EDGE,
          height: SCREENSHOT_FULL_MAX_EDGE,
          fit: "inside",
          withoutEnlargement: true,
        })
        .webp({ quality: SCREENSHOT_FULL_WEBP_QUALITY })
        .toBuffer(),
      pipeline
        .clone()
        .resize({
          width: SCREENSHOT_CARD_MAX_EDGE,
          height: SCREENSHOT_CARD_MAX_EDGE,
          fit: "inside",
          withoutEnlargement: true,
        })
        .webp({ quality: SCREENSHOT_CARD_WEBP_QUALITY })
        .toBuffer(),
    ]);

    return {
      fullBuffer,
      cardBuffer,
      mimeType: "image/webp",
      ext: "webp",
    };
  } catch (err) {
    if (err instanceof ScreenshotProcessingError) {
      throw err;
    }
    throw new ScreenshotProcessingError(
      "Could not process screenshot. Use a valid JPEG, PNG, or WebP image.",
      "screenshot_invalid"
    );
  }
}

/**
 * Resize a captured prefab render into a square transparent PNG icon.
 *
 * @param {Buffer} buffer
 * @returns {Promise<{ iconBuffer: Buffer, mimeType: "image/png", ext: "png" }>}
 */
export async function processPropIcon(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
    throw new ScreenshotProcessingError("Icon capture is empty or invalid.", "icon_invalid");
  }

  const sharp = await loadSharp();

  try {
    // Do not auto-rotate from EXIF — that can twist entity orientation relative to the viewer.
    let pipeline = sharp(buffer, { failOn: "error" }).ensureAlpha();
    try {
      // Drop empty margins from the capture so the prop fills the 64x64 better.
      pipeline = sharp(await pipeline.trim({ threshold: 1 }).toBuffer()).ensureAlpha();
    } catch {
      // trim throws when the image is fully opaque or empty; keep the original pipeline.
      pipeline = sharp(buffer, { failOn: "error" }).ensureAlpha();
    }
    const iconBuffer = await pipeline
      .resize(PROP_ICON_SIZE, PROP_ICON_SIZE, {
        fit: "contain",
        background: { r: 0, g: 0, b: 0, alpha: 0 },
      })
      .png()
      .toBuffer();
    return { iconBuffer, mimeType: "image/png", ext: "png" };
  } catch (err) {
    if (err instanceof ScreenshotProcessingError) {
      throw err;
    }
    throw new ScreenshotProcessingError("Could not process prop icon capture.", "icon_invalid");
  }
}
