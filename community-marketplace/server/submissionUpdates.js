import fs from "node:fs";
import path from "node:path";
import {
  assertSize,
  MAX_BUILDING_JSON_BYTES,
  MAX_ICON_BYTES,
  MAX_PREFAB_BYTES,
  assignCommunityCatalogId,
  assignCommunityPropId,
  detectSubmissionContentType,
  isPropCatalogId,
  normalizeCatalogId,
  normalizeRequiredMods,
  readPrefabBlockIdVersion,
  resolveManifestContentType,
  validateSubmissionBuilding,
  validateSubmissionProp,
} from "./validation.js";

/**
 * @param {string | undefined | null} version
 */
export function parseVersionNumber(version) {
  const n = parseInt(String(version || "0"), 10);
  return Number.isFinite(n) && n >= 0 ? n : 0;
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} buildingId
 */
export function nextVersionForBuilding(storage, buildingId) {
  const id = normalizeCatalogId(buildingId);
  if (!id) {
    return "1";
  }
  let max = 0;
  const manifest = storage.readManifest();
  const approved = (manifest.entries || []).find((entry) => entry.id === id);
  if (approved) {
    max = Math.max(max, parseVersionNumber(approved.version));
  }
  for (const pending of storage.listPending()) {
    if (pending.proposedId === id) {
      max = Math.max(max, parseVersionNumber(pending.version));
    }
  }
  for (const rejected of storage.listRejected()) {
    if (rejected.proposedId === id) {
      max = Math.max(max, parseVersionNumber(rejected.version));
    }
  }
  return String(max + 1);
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} submissionId
 */
export function withdrawPendingSubmissionInternal(storage, submissionId) {
  storage.deleteScreenshotsForOwner("pending", submissionId);
  const dir = storage.submissionDir(submissionId, "pending");
  if (fs.existsSync(dir)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {{ profileUuid: string, profileUsername: string }} webUser
 * @param {(metaOrEntry: object, webUser: { profileUuid: string, profileUsername: string }) => boolean} isOwnedByWebUser
 */
export function listSlimSubmissionsForCreator(storage, webUser, isOwnedByWebUser) {
  const pending = storage
    .listPending()
    .filter((submission) => isOwnedByWebUser(submission, webUser))
    .map((submission) => ({
      kind: "pending",
      submissionId: submission.submissionId,
      proposedId: submission.proposedId,
      contentType: submission.contentType || resolveManifestContentType(undefined, submission),
      displayName: submission.displayName,
      status: "pending",
      version: submission.version || "1",
      submittedAt: submission.submittedAt,
    }));

  const rejected = storage
    .listRejected()
    .filter((submission) => isOwnedByWebUser(submission, webUser))
    .map((submission) => ({
      kind: "rejected",
      submissionId: submission.submissionId,
      proposedId: submission.proposedId,
      contentType: submission.contentType || resolveManifestContentType(undefined, submission),
      displayName: submission.displayName,
      status: "rejected",
      version: submission.version || "1",
      rejectedAt: submission.rejectedAt,
    }));

  const manifest = storage.readManifest();
  const approved = (manifest.entries || [])
    .filter((entry) => isOwnedByWebUser(entry, webUser))
    .map((entry) => ({
      kind: "approved",
      id: entry.id,
      contentType: resolveManifestContentType(entry.contentType, entry),
      displayName: entry.displayName,
      status: "approved",
      version: entry.version || "1",
      approvedAt: entry.approvedAt,
    }));

  return [...pending, ...approved, ...rejected].sort((a, b) => {
    const dateA = a.submittedAt || a.approvedAt || a.rejectedAt || "";
    const dateB = b.submittedAt || b.approvedAt || b.rejectedAt || "";
    return dateB.localeCompare(dateA);
  });
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} submissionId
 * @param {{ profileUuid: string, profileUsername: string }} webUser
 * @param {(metaOrEntry: object, webUser: { profileUuid: string, profileUsername: string }) => boolean} isOwnedByWebUser
 * @returns {"pending"|"rejected"|null}
 */
export function resolveOwnedSubmissionStatus(storage, submissionId, webUser, isOwnedByWebUser) {
  const pending = storage.loadSubmissionMeta(submissionId, "pending");
  if (pending && isOwnedByWebUser(pending, webUser)) {
    return "pending";
  }
  const rejected = storage.loadSubmissionMeta(submissionId, "rejected");
  if (rejected && isOwnedByWebUser(rejected, webUser)) {
    return "rejected";
  }
  return null;
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} submissionId
 * @param {string} fileName
 * @param {{ profileUuid: string, profileUsername: string }} webUser
 * @param {(metaOrEntry: object, webUser: { profileUuid: string, profileUsername: string }) => boolean} isOwnedByWebUser
 */
export function resolveOwnerSubmissionFile(storage, submissionId, fileName, webUser, isOwnedByWebUser) {
  const status = resolveOwnedSubmissionStatus(storage, submissionId, webUser, isOwnedByWebUser);
  if (!status) {
    return null;
  }
  const file = path.join(storage.submissionDir(submissionId, status), fileName);
  return fs.existsSync(file) ? file : null;
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} buildingId
 * @param {string} creatorUuid
 * @param {(metaOrEntry: object, profileUuid: string) => boolean} isOwnedByProfile
 */
function isOwnedBuilding(storage, buildingId, creatorUuid, isOwnedByProfile) {
  const id = normalizeCatalogId(buildingId);
  if (!id) {
    return false;
  }
  const manifest = storage.readManifest();
  const approved = (manifest.entries || []).find((entry) => entry.id === id);
  if (approved && isOwnedByProfile(approved, creatorUuid)) {
    return true;
  }
  for (const pending of storage.listPending()) {
    if (pending.proposedId === id && isOwnedByProfile(pending, creatorUuid)) {
      return true;
    }
  }
  for (const rejected of storage.listRejected()) {
    if (rejected.proposedId === id && isOwnedByProfile(rejected, creatorUuid)) {
      return true;
    }
  }
  return false;
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} submissionId
 * @param {string} proposedId
 * @param {string} creatorUuid
 * @param {string} creatorName
 * @param {Record<string, unknown>} definition
 * @param {Buffer} prefabBuffer
 * @param {Buffer | null | undefined} iconBuffer
 * @param {string} version
 * @param {number} blockIdVersion
 * @param {"prop"|"building"} definitionKind
 * @param {(value: unknown) => string} normalizeDescription
 * @param {(value: unknown) => string[]} normalizeTags
 */
function writePendingSubmission(
  storage,
  submissionId,
  proposedId,
  creatorUuid,
  creatorName,
  definition,
  prefabBuffer,
  iconBuffer,
  version,
  blockIdVersion,
  definitionKind,
  normalizeDescription,
  normalizeTags,
) {
  const dir = storage.submissionDir(submissionId, "pending");
  fs.mkdirSync(dir, { recursive: true });
  const definitionName = definitionKind === "prop" ? "prop.json" : "building.json";
  fs.writeFileSync(path.join(dir, definitionName), JSON.stringify(definition, null, 2));
  fs.writeFileSync(path.join(dir, "prefab.prefab.json"), prefabBuffer);
  if (iconBuffer) {
    fs.writeFileSync(path.join(dir, "icon.png"), iconBuffer);
  }
  const requiredMods = normalizeRequiredMods(definition.requiredMods);
  definition.requiredMods = requiredMods;
  const contentType = resolveManifestContentType(definitionKind === "prop" ? "prop" : "building", {
    id: proposedId,
    wallSegment: Boolean(definition.wallSegment),
  });
  const meta = {
    submissionId,
    proposedId,
    contentType,
    displayName: definition.displayName,
    description: normalizeDescription(definition.description),
    creatorUuid,
    creatorName,
    styleId: definition.styleId || "misc",
    tags: normalizeTags(definition.tags),
    blockIdVersion,
    status: "pending",
    submittedAt: new Date().toISOString(),
    version,
  };
  if (Boolean(definition.wallSegment)) {
    meta.wallSegment = true;
  }
  if (requiredMods.length) {
    meta.requiredMods = requiredMods;
  }
  fs.writeFileSync(path.join(dir, "meta.json"), JSON.stringify(meta, null, 2));
  return meta;
}

/**
 * @param {import("./storage.js").Storage} storage
 * @param {string} submissionId
 * @param {Record<string, unknown>} definition
 * @param {Buffer} prefabBuffer
 * @param {Buffer | null | undefined} iconBuffer
 * @param {string} version
 * @param {number} blockIdVersion
 * @param {"prop"|"building"} definitionKind
 * @param {(value: unknown) => string} normalizeDescription
 * @param {(value: unknown) => string[]} normalizeTags
 */
function replacePendingSubmission(
  storage,
  submissionId,
  definition,
  prefabBuffer,
  iconBuffer,
  version,
  blockIdVersion,
  definitionKind,
  normalizeDescription,
  normalizeTags,
) {
  const dir = storage.submissionDir(submissionId, "pending");
  const definitionName = definitionKind === "prop" ? "prop.json" : "building.json";
  fs.writeFileSync(path.join(dir, definitionName), JSON.stringify(definition, null, 2));
  fs.writeFileSync(path.join(dir, "prefab.prefab.json"), prefabBuffer);
  if (iconBuffer) {
    fs.writeFileSync(path.join(dir, "icon.png"), iconBuffer);
  }
  const metaPath = path.join(dir, "meta.json");
  const meta = fs.existsSync(metaPath) ? JSON.parse(fs.readFileSync(metaPath, "utf8")) : {};
  const requiredMods = normalizeRequiredMods(definition.requiredMods);
  definition.requiredMods = requiredMods;
  meta.displayName = definition.displayName;
  meta.description = normalizeDescription(definition.description);
  meta.styleId = definition.styleId || "misc";
  meta.tags = normalizeTags(definition.tags);
  meta.blockIdVersion = blockIdVersion;
  meta.version = version;
  meta.submittedAt = new Date().toISOString();
  meta.contentType = resolveManifestContentType(definitionKind === "prop" ? "prop" : "building", {
    id: meta.proposedId,
    wallSegment: Boolean(definition.wallSegment),
  });
  if (Boolean(definition.wallSegment)) {
    meta.wallSegment = true;
  } else {
    delete meta.wallSegment;
  }
  if (requiredMods.length) {
    meta.requiredMods = requiredMods;
  } else {
    delete meta.requiredMods;
  }
  fs.writeFileSync(metaPath, JSON.stringify(meta, null, 2));
  return meta;
}

/**
 * @param {object} params
 * @param {import("./storage.js").Storage} params.storage
 * @param {string} params.buildingId
 * @param {string} params.creatorUuid
 * @param {string} params.creatorName
 * @param {Express.Multer.File | undefined} params.buildingFile
 * @param {Express.Multer.File | undefined} params.propFile
 * @param {Express.Multer.File} params.prefabFile
 * @param {Express.Multer.File | undefined} params.iconFile
 * @param {unknown} [params.contentTypeField]
 * @param {(metaOrEntry: object, profileUuid: string) => boolean} params.isOwnedByProfile
 * @param {(value: unknown) => string} params.normalizeDescription
 * @param {(value: unknown) => string[]} params.normalizeTags
 */
export function updateOwnedSubmission({
  storage,
  buildingId,
  creatorUuid,
  creatorName,
  buildingFile,
  propFile,
  prefabFile,
  iconFile,
  contentTypeField,
  isOwnedByProfile,
  normalizeDescription,
  normalizeTags,
}) {
  const id = normalizeCatalogId(buildingId);
  if (!id) {
    return { status: 400, body: { error: "invalid_id" } };
  }
  if (!isOwnedBuilding(storage, id, creatorUuid, isOwnedByProfile)) {
    return { status: 403, body: { error: "not_owner" } };
  }

  try {
    const definitionFile = propFile || buildingFile;
    if (!definitionFile) {
      return { status: 400, body: { error: "definition_and_prefab_required" } };
    }
    assertSize(definitionFile.size, MAX_BUILDING_JSON_BYTES, propFile ? "prop" : "building");
    assertSize(prefabFile.size, MAX_PREFAB_BYTES, "prefab");
    if (iconFile) {
      assertSize(iconFile.size, MAX_ICON_BYTES, "icon");
    }

    const definition = JSON.parse(definitionFile.buffer.toString("utf8"));
    const blockIdVersion = readPrefabBlockIdVersion(prefabFile.buffer);
    const submissionKind = detectSubmissionContentType({
      propField: Boolean(propFile),
      buildingField: Boolean(buildingFile),
      contentTypeField,
      definition,
    });
    const isProp = submissionKind === "prop";
    if (isProp !== isPropCatalogId(id)) {
      return { status: 400, body: { error: "content_type_mismatch" } };
    }
    const validationError = isProp
      ? validateSubmissionProp(definition, blockIdVersion)
      : validateSubmissionBuilding(definition, blockIdVersion);
    if (validationError) {
      return { status: 400, body: { error: validationError } };
    }

    const assignedId = isProp
      ? assignCommunityPropId(definition, creatorUuid)
      : assignCommunityCatalogId(definition, creatorUuid);
    if (assignedId !== id) {
      return { status: 400, body: { error: "building_id_mismatch" } };
    }
    const requiredMods = normalizeRequiredMods(definition.requiredMods);
    definition.requiredMods = requiredMods;
    const definitionKind = isProp ? "prop" : "building";

    const nextVersion = nextVersionForBuilding(storage, id);
    const manifest = storage.readManifest();
    const approvedEntry = (manifest.entries || []).find((entry) => entry.id === id);
    const ownedPending = storage
      .listPending()
      .filter((pending) => pending.proposedId === id && isOwnedByProfile(pending, creatorUuid))
      .sort((a, b) => String(b.submittedAt || "").localeCompare(String(a.submittedAt || "")));
    const ownedRejected = storage
      .listRejected()
      .filter((rejected) => rejected.proposedId === id && isOwnedByProfile(rejected, creatorUuid));

    if (approvedEntry && isOwnedByProfile(approvedEntry, creatorUuid)) {
      for (const pending of ownedPending) {
        withdrawPendingSubmissionInternal(storage, pending.submissionId);
      }
      const submissionId = `${id}_${Date.now()}`;
      const meta = writePendingSubmission(
        storage,
        submissionId,
        id,
        creatorUuid,
        creatorName,
        definition,
        prefabFile.buffer,
        iconFile?.buffer,
        nextVersion,
        blockIdVersion,
        definitionKind,
        normalizeDescription,
        normalizeTags,
      );
      return {
        status: 201,
        body: {
          submissionId,
          proposedId: id,
          contentType: meta.contentType,
          status: "pending",
          version: meta.version,
          action: "created_pending",
          isBuildingUpdate: true,
          displayName: meta.displayName,
          description: meta.description || "",
        },
      };
    }

    if (ownedPending.length > 0) {
      const latest = ownedPending[0];
      const meta = replacePendingSubmission(
        storage,
        latest.submissionId,
        definition,
        prefabFile.buffer,
        iconFile?.buffer,
        nextVersion,
        blockIdVersion,
        definitionKind,
        normalizeDescription,
        normalizeTags,
      );
      return {
        status: 200,
        body: {
          submissionId: latest.submissionId,
          proposedId: id,
          contentType: meta.contentType,
          status: "pending",
          version: meta.version,
          action: "replaced_pending",
        },
      };
    }

    if (ownedRejected.length > 0) {
      const submissionId = `${id}_${Date.now()}`;
      const meta = writePendingSubmission(
        storage,
        submissionId,
        id,
        creatorUuid,
        creatorName,
        definition,
        prefabFile.buffer,
        iconFile?.buffer,
        nextVersion,
        blockIdVersion,
        definitionKind,
        normalizeDescription,
        normalizeTags,
      );
      return {
        status: 201,
        body: {
          submissionId,
          proposedId: id,
          contentType: meta.contentType,
          status: "pending",
          version: meta.version,
          action: "created_pending",
          displayName: meta.displayName,
          description: meta.description || "",
        },
      };
    }

    return { status: 404, body: { error: "not_found" } };
  } catch (error) {
    return { status: 400, body: { error: error.message || "update_failed" } };
  }
}
