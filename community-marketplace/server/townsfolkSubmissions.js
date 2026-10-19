import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import multer from "multer";
import { normalizePlayerUuid } from "./playerIdentity.js";
import { TOWNSFOLK_ICON } from "./townsfolkPortrait.js";

export const ATTRIBUTES = ["strength", "intelligence", "vitality", "dexterity", "speed"];
export const SKILLS = ["blacksmithing", "ranching", "farming", "mining", "building", "alchemy", "crafting", "leatherworking", "cooking", "logistics", "commerce", "jewelry"];
export const ASSIGNMENTS = ["idle", "tourist", "guard", "guild_adventurer"];
const ATTRIBUTE_START = 5, ATTRIBUTE_POINTS = 5, ATTRIBUTE_CAP = 8;
const SKILL_START = 1, SKILL_POINTS = 10, SKILL_CAP = 5;
const ID = /^[a-z][a-z0-9_]{0,63}$/;
const ASSET = /^[A-Za-z0-9_/-]+\.(png|blockymodel)$/;
const VOICE = /^(Bright|Warm|Mellow|Gravely|Old)(Male|Female)(Lower|Higher)?$|^RustyRobot(Lower|Higher)?$/;
const MAX_BYTES = 256 * 1024;

class ValidationError extends Error { }
function portraitFailure(error) {
  const missing = /^Portrait asset is missing: ([A-Za-z0-9_./-]+)$/.exec(error?.message || "");
  if (missing) return { code: "missing_asset", message: `Missing website asset: ${missing[1]}. Sync and upload the portrait assets, then retry.` };
  if (error?.message === "Portrait gradient is missing") return { code: "missing_gradient", message: "The website's skin or hair colour data is outdated. Update the portrait assets, then retry." };
  if (error?.message === "Portrait rendering timed out") return { code: "timeout", message: "Portrait rendering took too long. Please retry." };
  return { code: "render_failed", message: "Portrait rendering failed. Check the website server logs for the cause before retrying." };
}
function check(ok, message) { if (!ok) throw new ValidationError(message); }
function text(value, max, label) {
  check(typeof value === "string" && value.trim().length > 0 && value.length <= max && !/[\x00-\x1f]/.test(value), `Invalid ${label}.`);
  return value.trim();
}
function scores(value, keys, min, max) {
  check(value && typeof value === "object" && !Array.isArray(value), "Starting scores are missing.");
  return Object.fromEntries(keys.map(key => {
    check(Number.isInteger(value[key]) && value[key] >= min && value[key] <= max, `Invalid ${key} score.`);
    return [key, value[key]];
  }));
}

/** Only data produced by the character sheet is retained; no arbitrary paths, equipment or assignment restrictions. */
export function validateTownsfolk(character, model) {
  check(character && model && typeof character === "object" && typeof model === "object", "Both JSON files are required.");
  check(typeof character.id === "string" && ID.test(character.id) && character.modelAssetId === `Townsfolk_${character.id}`, "Invalid character/model identity.");
  check(["male", "female"].includes(character.gender), "Choose a gender.");
  check(typeof character.race === "string" && /^[a-z][a-z_]{0,31}$/.test(character.race), "Choose a race.");
  check(Array.isArray(character.personalityIds) && character.personalityIds.length === 3
    && new Set(character.personalityIds).size === 3 && character.personalityIds.every(id => typeof id === "string" && ID.test(id)), "Choose three different personalities.");
  check(VOICE.test(character.speechVoiceId), "Choose a valid voice.");
  check(Array.isArray(character.moveInRequirements) && character.moveInRequirements.length >= 1 && character.moveInRequirements.length <= 8, "Choose between one and eight move-in items.");
  const items = character.moveInRequirements.map(row => {
    check(row && typeof row.itemId === "string" && /^[A-Za-z0-9_:-]{1,128}$/.test(row.itemId) && Number.isInteger(row.count) && row.count >= 1 && row.count <= 64, "Move-in amounts must be between 1 and 64.");
    return { itemId: row.itemId, count: row.count };
  });
  check(new Set(items.map(row => row.itemId)).size === items.length, "Combine duplicate move-in items.");
  check(model.Parent === "Aetherhaven_Human" && Array.isArray(model.DefaultAttachments) && model.DefaultAttachments.length <= 64, "Invalid exported player model.");
  function appearance(part, root = false) {
    check(part && typeof part === "object", "Invalid model attachment.");
    const out = {};
    for (const key of ["Model", "Texture"]) {
      if (root && part[key] == null) continue; // The exporter may inherit the base Player body.
      check(typeof part[key] === "string" && part[key].length <= 256 && ASSET.test(part[key]) && !part[key].includes("..") && !part[key].startsWith("/"), "Invalid cosmetic asset path.");
      check(part[key].endsWith(key === "Model" ? ".blockymodel" : ".png"), "Invalid cosmetic asset type.");
      out[key] = part[key];
    }
    if (part.GradientSet != null) {
      out.GradientSet = text(part.GradientSet, 64, "gradient set");
      out.GradientId = text(part.GradientId, 64, "gradient");
    }
    return out;
  }
  const icon = `Icons/ModelsGenerated/${character.modelAssetId}.png`;
  const cleanModel = { Parent: "Aetherhaven_Human", ...appearance(model, true), DefaultAttachments: model.DefaultAttachments.map(part => appearance(part)),
    Icon: icon, IconProperties: TOWNSFOLK_ICON };
  const startingAttributes = scores(character.startingAttributes, ATTRIBUTES, 1, ATTRIBUTE_CAP);
  const startingSkills = scores(character.startingSkills, SKILLS, 0, SKILL_CAP);
  check(Object.values(startingAttributes).reduce((sum, value) => sum + value, 0) <= ATTRIBUTES.length * ATTRIBUTE_START + ATTRIBUTE_POINTS,
    "Too many attribute points. Lower an attribute to refund points.");
  check(Object.values(startingSkills).reduce((sum, value) => sum + value, 0) <= SKILLS.length * SKILL_START + SKILL_POINTS,
    "Too many skill points. Lower a skill to refund points.");
  const cleanCharacter = {
    id: character.id, displayName: text(character.displayName, 48, "name"), gender: character.gender, race: character.race,
    modelAssetId: character.modelAssetId, portraitIcon: `${character.modelAssetId}.png`, personalityIds: character.personalityIds,
    allowedAssignmentKinds: ASSIGNMENTS, befriendable: false, speechVoiceId: character.speechVoiceId,
    moveInRequirements: items, startingAttributes, startingSkills,
  };
  return { character: cleanCharacter, model: cleanModel };
}

export function createTownsfolkSubmissions(dataDir) {
  // Deliberately outside approved/submissions and every public manifest/static directory.
  const root = path.resolve(dataDir, "private-townsfolk");
  fs.mkdirSync(root, { recursive: true });
  function file(id) { const normalized = normalizePlayerUuid(id); return normalized ? path.join(root, `${normalized}.json`) : null; }
  function get(id) { const p = file(id); return p && fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, "utf8")) : null; }
  function save(id, creatorUuid, creatorName, character, model) {
    check(normalizePlayerUuid(id), "Invalid submission id.");
    const clean = validateTownsfolk(character, model);
    const digest = crypto.createHash("sha256").update(JSON.stringify(clean)).digest("hex");
    const existing = get(id);
    if (existing) {
      check(existing.creatorUuid === creatorUuid && existing.digest === digest, "Submission id already used. Reopen the creator to start a new submission.");
      return existing;
    }
    const entry = { id, creatorUuid, creatorName: text(creatorName, 64, "creator name"), submittedAt: new Date().toISOString(), digest, ...clean };
    // A single atomic rename publishes both files together, never a half-written character.
    const target = file(id), temp = `${target}.${crypto.randomUUID()}.tmp`;
    try { fs.writeFileSync(temp, JSON.stringify(entry, null, 2), { flag: "wx" }); fs.renameSync(temp, target); }
    finally { if (fs.existsSync(temp)) fs.unlinkSync(temp); }
    return entry;
  }
  return {
    save, get,
    remove(id) {
      const target = file(id);
      if (!target || !fs.existsSync(target)) return false;
      // Keep the record available for a retry if removing a file fails.
      const png = path.join(root, `${normalizePlayerUuid(id)}.png`);
      if (fs.existsSync(png)) fs.unlinkSync(png);
      fs.unlinkSync(target); // This record contains both the character and model JSON.
      return true;
    },
    portraitPath: id => normalizePlayerUuid(id) ? path.join(root, `${normalizePlayerUuid(id)}.png`) : null,
    portraitStatus(id, status, error = null) {
      const entry = get(id); if (!entry) return;
      entry.portraitStatus = status;
      if (error) entry.portraitError = error;
      else delete entry.portraitError;
      const target = file(id), temp = `${target}.${crypto.randomUUID()}.tmp`;
      try { fs.writeFileSync(temp, JSON.stringify(entry, null, 2), { flag: "wx" }); fs.renameSync(temp, target); }
      finally { if (fs.existsSync(temp)) fs.unlinkSync(temp); }
    },
    list: () => fs.readdirSync(root).filter(name => name.endsWith(".json")).map(name => get(name.slice(0, -5)))
      .filter(Boolean).sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
      .map(({ model, digest, ...entry }) => entry),
  };
}

export function registerTownsfolkRoutes(app, { dataDir, requireWebUser, requireAdmin, submissionRateLimit, renderPortrait }) {
  const storage = createTownsfolkSubmissions(dataDir);
  const rendering = new Map();
  async function portrait(entry) {
    if (fs.existsSync(storage.portraitPath(entry.id))) return "ready";
    if (rendering.has(entry.id)) return rendering.get(entry.id);
    // Do not let a large submission burst build an unbounded worker queue.
    if (rendering.size >= 8) {
      storage.portraitStatus(entry.id, "failed", { code: "busy", message: "The portrait queue is full. Wait a moment, then retry." }); return "failed";
    }
    storage.portraitStatus(entry.id, "rendering");
    const job = Promise.resolve().then(() => renderPortrait(entry.model)).then(png => {
      const target = storage.portraitPath(entry.id), temp = `${target}.tmp`;
      try { fs.writeFileSync(temp, png); fs.renameSync(temp, target); }
      finally { if (fs.existsSync(temp)) fs.unlinkSync(temp); }
      storage.portraitStatus(entry.id, "ready"); return "ready";
    }).catch(error => {
      console.warn("Townsfolk portrait failed:", error.message);
      storage.portraitStatus(entry.id, "failed", portraitFailure(error)); return "failed";
    }).finally(() => rendering.delete(entry.id));
    rendering.set(entry.id, job); return job;
  }
  const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: MAX_BYTES, files: 2, fields: 0, parts: 3 } })
    .fields([{ name: "townsfolk", maxCount: 1 }, { name: "model", maxCount: 1 }]);
  app.post("/api/v1/townsfolk-submissions", submissionRateLimit, (req, res) => {
    upload(req, res, async error => {
      if (error) return res.status(400).json({ error: "invalid_upload", message: "Send two JSON files, each no larger than 256 KB." });
      const creatorUuid = normalizePlayerUuid(req.get("X-Player-Uuid"));
      if (!creatorUuid) return res.status(400).json({ error: "missing_player_uuid" });
      try {
        const character = JSON.parse(req.files?.townsfolk?.[0]?.buffer?.toString("utf8") || "null");
        const model = JSON.parse(req.files?.model?.[0]?.buffer?.toString("utf8") || "null");
        const entry = storage.save(req.get("X-Submission-Id"), creatorUuid, req.get("X-Player-Name") || "Player", character, model);
        // The two source files survive even if assets are missing or portrait rendering fails.
        const portraitStatus = await portrait(entry);
        res.status(201).json({ submissionId: entry.id, portraitStatus });
      } catch (error) {
        const invalid = error instanceof SyntaxError || error instanceof ValidationError;
        res.status(invalid ? 400 : 500).json({ error: invalid ? "invalid_townsfolk" : "save_failed", message:
          error instanceof SyntaxError ? "Invalid JSON file." : invalid ? error.message : "Could not save your submission. Please retry." });
      }
    });
  });
  app.get("/api/admin/townsfolk", requireWebUser, requireAdmin, (_req, res) => {
    res.set("Cache-Control", "no-store").json({ submissions: storage.list().map(entry => ({ ...entry,
      portraitStatus: fs.existsSync(storage.portraitPath(entry.id)) ? "ready" : rendering.has(entry.id) ? "rendering" : "failed" })) });
  });
  app.post("/api/admin/townsfolk/:id/portrait", requireWebUser, requireAdmin, async (req, res) => {
    const entry = storage.get(req.params.id);
    if (!entry) return res.status(404).json({ error: "not_found" });
    const portraitStatus = await portrait(entry);
    res.set("Cache-Control", "no-store").json({ portraitStatus, portraitError: storage.get(entry.id)?.portraitError || null });
  });
  app.delete("/api/admin/townsfolk/:id", requireWebUser, requireAdmin, (req, res) => {
    res.set("Cache-Control", "no-store");
    const entry = storage.get(req.params.id);
    if (!entry) return res.status(404).json({ error: "not_found", message: "This submission no longer exists." });
    if (rendering.has(entry.id)) return res.status(409).json({ error: "portrait_busy", message: "Wait for this character's portrait to finish, then delete the submission." });
    try { storage.remove(entry.id); res.status(204).end(); }
    catch (error) {
      console.warn("Townsfolk deletion failed:", error.message);
      res.status(500).json({ error: "delete_failed", message: "Could not remove all submission files. Please retry." });
    }
  });
  app.get("/api/admin/townsfolk/:id/files/:kind", requireWebUser, requireAdmin, (req, res) => {
    const entry = storage.get(req.params.id);
    if (!entry || !["townsfolk", "model", "portrait"].includes(req.params.kind)) return res.status(404).json({ error: "not_found" });
    if (req.params.kind === "portrait") {
      const portraitFile = storage.portraitPath(entry.id);
      if (!fs.existsSync(portraitFile)) return res.status(409).json({ error: "portrait_not_ready" });
      res.set("Cache-Control", "no-store").attachment(`${entry.character.modelAssetId}.png`).type("png").sendFile(portraitFile);
      return;
    }
    const model = req.params.kind === "model";
    const name = model ? entry.character.modelAssetId : entry.character.id;
    res.set("Cache-Control", "no-store").attachment(`${name}.json`).type("application/json")
      .send(JSON.stringify(model ? entry.model : entry.character, null, 2) + "\n");
  });
}
