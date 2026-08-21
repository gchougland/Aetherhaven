import fs from "node:fs";
import { normalizeCatalogId } from "./validation.js";

/**
 * @param {ReturnType<import("./storage.js").createStorage>} storage
 * @param {string} buildingId
 */
export function readApprovedCoverScreenshotId(storage, buildingId) {
  try {
    const paths = storage.approvedPaths(buildingId);
    if (!fs.existsSync(paths.meta)) {
      return "";
    }
    const meta = JSON.parse(fs.readFileSync(paths.meta, "utf8"));
    return String(meta.coverScreenshotId || "").trim();
  } catch {
    return "";
  }
}

/**
 * @param {ReturnType<import("./storage.js").createStorage>} storage
 * @param {string} buildingId
 * @param {string} coverScreenshotId
 */
export function isValidCoverScreenshot(storage, buildingId, coverScreenshotId) {
  if (!coverScreenshotId) {
    return false;
  }
  const shot = storage.loadScreenshotMeta(coverScreenshotId);
  return Boolean(
    shot &&
      shot.status === "approved" &&
      shot.ownerKind === "approved" &&
      shot.ownerId === buildingId
  );
}

/**
 * Sets the marketplace card cover when the building/prop does not already have a valid one.
 * Accepts plot_community_* and prop_community_* ids.
 *
 * @param {ReturnType<import("./storage.js").createStorage>} storage
 * @param {string} buildingId
 * @param {string} screenshotId
 * @returns {boolean} true when cover was applied
 */
export function applyCoverScreenshotIfUnset(storage, buildingId, screenshotId) {
  const id = normalizeCatalogId(buildingId);
  const coverId = String(screenshotId || "").trim();
  if (!id || !coverId || !isValidCoverScreenshot(storage, id, coverId)) {
    return false;
  }

  const existing = readApprovedCoverScreenshotId(storage, id);
  if (existing && isValidCoverScreenshot(storage, id, existing)) {
    return false;
  }

  const paths = storage.approvedPaths(id);
  if (!fs.existsSync(paths.meta)) {
    return false;
  }

  const approvedMeta = JSON.parse(fs.readFileSync(paths.meta, "utf8"));
  approvedMeta.coverScreenshotId = coverId;
  fs.writeFileSync(paths.meta, JSON.stringify(approvedMeta, null, 2));

  const manifest = storage.readManifest();
  const entry = (manifest.entries || []).find((candidate) => candidate.id === id);
  if (entry) {
    entry.coverScreenshotId = coverId;
    storage.writeManifest(manifest);
  }

  return true;
}

/**
 * After screenshots move to a published building, use the earliest approved shot as cover if unset.
 *
 * @param {ReturnType<import("./storage.js").createStorage>} storage
 * @param {string} buildingId
 * @returns {boolean}
 */
export function autoSetCoverFromExistingApprovedScreenshots(storage, buildingId) {
  const id = normalizeCatalogId(buildingId);
  if (!id) {
    return false;
  }
  const existing = readApprovedCoverScreenshotId(storage, id);
  if (existing && isValidCoverScreenshot(storage, id, existing)) {
    return false;
  }
  const approved = storage.listScreenshotsForOwner("approved", id, "approved");
  if (!approved.length) {
    return false;
  }
  approved.sort((a, b) =>
    String(a.approvedAt || a.uploadedAt || "").localeCompare(String(b.approvedAt || b.uploadedAt || ""))
  );
  return applyCoverScreenshotIfUnset(storage, id, approved[0].screenshotId);
}
