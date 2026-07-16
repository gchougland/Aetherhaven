import fs from "node:fs";
import path from "node:path";
import {
  MAX_BUILDING_JSON_BYTES,
  MAX_PREFAB_BYTES,
  normalizeEditStyleId,
  normalizeEditTags,
  normalizeRequiredMods,
  readPrefabBlockIdVersion,
  validateBuildingDefinition,
  validateSubmissionBuilding,
} from "./validation.js";

export class AdminRawFileError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "AdminRawFileError";
    this.code = code;
  }
}

function parseJsonObject(text, maxBytes, label) {
  if (typeof text !== "string") {
    throw new AdminRawFileError(`${label}_invalid`, `${label} must be JSON text.`);
  }
  if (Buffer.byteLength(text, "utf8") > maxBytes) {
    throw new AdminRawFileError(`${label}_too_large`, `${label} exceeds the allowed size.`);
  }
  let value;
  try {
    value = JSON.parse(text);
  } catch (err) {
    const detail = err instanceof Error ? err.message : "Invalid JSON";
    throw new AdminRawFileError(`${label}_invalid`, `${label} is not valid JSON: ${detail}`);
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new AdminRawFileError(`${label}_invalid`, `${label} must contain a JSON object.`);
  }
  return value;
}

export function validateAdminRawFilePair({ buildingText, prefabText, publishedId = "" }) {
  const building = parseJsonObject(buildingText, MAX_BUILDING_JSON_BYTES, "building_json");
  const prefab = parseJsonObject(prefabText, MAX_PREFAB_BYTES, "prefab_json");
  const prefabBuffer = Buffer.from(prefabText, "utf8");
  const blockIdVersion = readPrefabBlockIdVersion(prefabBuffer);
  if (blockIdVersion < 1) {
    throw new AdminRawFileError(
      "block_id_version_missing",
      "Prefab JSON must contain a positive blockIdVersion.",
    );
  }

  if (publishedId) {
    if (building.id !== publishedId) {
      throw new AdminRawFileError(
        "published_id_immutable",
        `Published building id must remain "${publishedId}".`,
      );
    }
    const expectedPrefabPath = `${publishedId}.prefab.json`;
    if (building.prefabPath !== expectedPrefabPath) {
      throw new AdminRawFileError(
        "published_prefab_path_immutable",
        `Published prefabPath must remain "${expectedPrefabPath}".`,
      );
    }
  }

  const validationError = publishedId
    ? validateBuildingDefinition(building, blockIdVersion)
    : validateSubmissionBuilding(building, blockIdVersion);
  if (validationError) {
    throw new AdminRawFileError(validationError, `Building validation failed: ${validationError}.`);
  }

  return {
    building,
    prefab,
    blockIdVersion,
    buildingBytes: Buffer.byteLength(buildingText, "utf8"),
    prefabBytes: prefabBuffer.length,
  };
}

export function projectAdminRawMetadata(building, blockIdVersion, prefabBytes) {
  return {
    displayName: String(building.displayName || "").trim(),
    description: typeof building.description === "string" ? building.description.trim() : "",
    styleId: normalizeEditStyleId(building.styleId),
    tags: normalizeEditTags(building.tags),
    requiredMods: normalizeRequiredMods(building.requiredMods),
    blockIdVersion,
    prefabBytes,
  };
}

export function applyAdminRawMetadata(target, metadata, options = {}) {
  target.displayName = metadata.displayName;
  target.styleId = metadata.styleId;
  target.tags = metadata.tags;
  target.requiredMods = metadata.requiredMods;
  target.blockIdVersion = metadata.blockIdVersion;
  if (metadata.description) {
    target.description = metadata.description;
  } else {
    delete target.description;
  }
  if (options.includePrefabBytes) {
    target.prefabBytes = metadata.prefabBytes;
  } else {
    delete target.prefabBytes;
  }
  return target;
}

export function atomicWriteText(filePath, text) {
  const normalized = text.endsWith("\n") ? text : `${text}\n`;
  const temporary = path.join(
    path.dirname(filePath),
    `.${path.basename(filePath)}.${process.pid}.${Date.now()}.tmp`,
  );
  fs.writeFileSync(temporary, normalized, "utf8");
  try {
    fs.renameSync(temporary, filePath);
  } catch (err) {
    fs.rmSync(temporary, { force: true });
    throw err;
  }
}
