import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import express from "express";
import { ATTRIBUTES, SKILLS, ASSIGNMENTS, validateTownsfolk, createTownsfolkSubmissions, registerTownsfolkRoutes } from "../server/townsfolkSubmissions.js";
import { TOWNSFOLK_ICON } from "../server/townsfolkPortrait.js";

const AUTHOR = "11111111-1111-4111-8111-111111111111", ID = "22222222-2222-4222-8222-222222222222";
export function character() {
  return { id: "community_test", modelAssetId: "Townsfolk_community_test", displayName: "Test Adventurer", gender: "male", race: "human",
    personalityIds: ["adventurer", "crafty", "grumpy"], speechVoiceId: "GravelyMaleLower",
    moveInRequirements: [{ itemId: "Wood_Softwood_Planks", count: 8 }],
    startingAttributes: Object.fromEntries(ATTRIBUTES.map(key => [key, 5])), startingSkills: Object.fromEntries(SKILLS.map(key => [key, key === "jewelry" ? 0 : 1])) };
}
function model() { return { Parent: "Aetherhaven_Human", Model: "Characters/Player.blockymodel", Texture: "Characters/Player_Textures/Player_Greyscale.png", DefaultAttachments: [] }; }

test("exports all assignments, no obsolete equipment and matching portrait references", () => {
  const result = validateTownsfolk({ ...character(), equipmentProfileId: "old", allowedAssignmentKinds: ["guard"] }, model());
  assert.deepEqual(result.character.allowedAssignmentKinds, ASSIGNMENTS);
  assert.equal(result.character.equipmentProfileId, undefined);
  assert.equal(result.character.startingSkills.jewelry, 0);
  assert.deepEqual(result.model.IconProperties, TOWNSFOLK_ICON);
  assert.equal(`Icons/ModelsGenerated/${result.character.portraitIcon}`, result.model.Icon);
  assert.equal(result.model.Icon, "Icons/ModelsGenerated/Townsfolk_community_test.png");
  assert.doesNotThrow(() => validateTownsfolk(character(), { Parent: "Aetherhaven_Human", DefaultAttachments: [] }));
});

test("rejects malformed scores, duplicate traits, invalid gifts and arbitrary model paths", () => {
  for (const change of [c => c.startingSkills.jewelry = -1, c => c.startingAttributes.speed = 21,
    c => c.startingSkills.farming = 1.5, c => c.personalityIds[1] = c.personalityIds[0],
    c => delete c.race, c => c.personalityIds[0] = null, c => c.moveInRequirements[0].count = 65,
    c => c.moveInRequirements = [], c => c.moveInRequirements[0].itemId = null]) {
    const c = character(); change(c); assert.throws(() => validateTownsfolk(c, model()));
  }
  for (const Model of ["../../secret.blockymodel", "https://example.com/a.blockymodel", "/etc/passwd", "Characters/a.png"])
    assert.throws(() => validateTownsfolk(character(), { ...model(), Model }));
});

test("paired files persist privately and retries do not duplicate or overwrite a submission", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "townsfolk-storage-"));
  try {
    const store = createTownsfolkSubmissions(dir);
    store.save(ID, AUTHOR, "Player", character(), model()); store.save(ID, AUTHOR, "Player", character(), model());
    assert.equal(store.list().length, 1);
    assert.equal(store.list()[0].model, undefined);
    assert.equal(store.get("../../secret"), null);
    assert.throws(() => store.save(ID, AUTHOR, "Player", { ...character(), displayName: "Overwrite" }, model()));
    assert.equal(createTownsfolkSubmissions(dir).get(ID).character.displayName, "Test Adventurer");
    assert.deepEqual(fs.readdirSync(dir), ["private-townsfolk"]);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test("HTTP upload, authenticated admin listing/downloads, portrait retry and spoofed-header denial", async t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "townsfolk-http-"));
  const app = express(); let failPortrait = true;
  // Only this test harness supplies sessions. Production routes use the website's signed login session.
  app.use((req, _res, next) => { req.session = req.get("X-Test-Session") ? { user: { role: req.get("X-Test-Session") } } : {}; next(); });
  registerTownsfolkRoutes(app, { dataDir: dir,
    requireWebUser: (req,res,next) => req.session.user ? next() : res.sendStatus(401),
    requireAdmin: (req,res,next) => req.session.user?.role === "admin" ? next() : res.sendStatus(403),
    submissionRateLimit: (_req,_res,next) => next(),
    renderPortrait: async () => { if (failPortrait) throw new Error("test missing asset"); return Buffer.from("test portrait"); },
  });
  let server;
  try {
    server = app.listen(0, "127.0.0.1"); await new Promise(resolve => server.once("listening", resolve));
    t.diagnostic(`Local test server: PID ${process.pid}, cwd ${process.cwd()}, port ${server.address().port}; closed in finally.`);
    const base = `http://127.0.0.1:${server.address().port}`;
    const send = async () => {
      const body = new FormData(); body.append("townsfolk", new Blob([JSON.stringify(character())]), "character.json");
      body.append("model", new Blob([JSON.stringify(model())]), "model.json");
      return fetch(`${base}/api/v1/townsfolk-submissions`, { method: "POST", body, headers: { "X-Player-Uuid": AUTHOR, "X-Player-Name": "Player", "X-Submission-Id": ID } });
    };
    let response = await send(); const receipt = await response.json();
    assert.equal(response.status, 201, JSON.stringify(receipt)); assert.equal(receipt.portraitStatus, "failed");
    const routes = ["/api/admin/townsfolk", ...["townsfolk","model","portrait"].map(kind=>`/api/admin/townsfolk/${ID}/files/${kind}`)];
    for (const route of routes) {
      assert.equal((await fetch(base+route, { headers: { "X-Player-Uuid": AUTHOR } })).status, 401);
      assert.equal((await fetch(base+route, { headers: { "X-Test-Session": "player", "X-Player-Uuid": AUTHOR } })).status, 403);
    }
    assert.equal((await fetch(`${base}/private-townsfolk/${ID}.json`)).status, 404);
    const admin = { "X-Test-Session": "admin" };
    response = await fetch(base+routes[0], { headers: admin }); assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal((await response.json()).submissions.length, 1);
    response = await fetch(base+routes[1], { headers: admin }); assert.equal((await response.json()).displayName, "Test Adventurer");
    assert.match(response.headers.get("content-disposition"), /community_test.json/);
    assert.equal((await fetch(base+routes[3], { headers: admin })).status, 409);
    assert.equal((await fetch(`${base}/api/admin/townsfolk/${ID}/portrait`, { method: "POST" })).status, 401);
    failPortrait = false;
    response = await fetch(`${base}/api/admin/townsfolk/${ID}/portrait`, { method: "POST", headers: admin });
    assert.equal((await response.json()).portraitStatus, "ready");
    response = await fetch(base+routes[3], { headers: admin }); assert.equal(response.status, 200); assert.match(response.headers.get("content-type"), /image\/png/);
    assert.equal((await send()).status, 201);
    assert.equal((await (await fetch(base+routes[0], { headers: admin })).json()).submissions.length, 1);
  } finally {
    if (server) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); assert.equal(server.listening, false); }
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
