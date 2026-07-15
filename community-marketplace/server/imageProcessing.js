import sharp from "sharp";

export const SCREENSHOT_FULL_MAX_EDGE = 1920;
export const SCREENSHOT_CARD_MAX_EDGE = 800;
export const SCREENSHOT_FULL_WEBP_QUALITY = 82;
export const SCREENSHOT_CARD_WEBP_QUALITY = 75;

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

  let pipeline;
  try {
    pipeline = sharp(buffer, { failOn: "error" }).rotate();
    await pipeline.metadata();
  } catch {
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
  } catch {
    throw new ScreenshotProcessingError(
      "Could not process screenshot. Use a valid JPEG, PNG, or WebP image.",
      "screenshot_invalid"
    );
  }
}
