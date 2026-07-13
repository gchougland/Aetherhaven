/**
 * Sync Town Journal markdown, images, crossmod tutorial, and addon GuideTopics
 * into community-marketplace/web/wiki/ for the public website wiki.
 *
 * Usage (from community-marketplace): npm run sync-wiki
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const REPO = path.resolve(ROOT, "..");
const WEB_WIKI = path.join(ROOT, "web", "wiki");
const TOPICS_OUT = path.join(WEB_WIKI, "topics");
const IMAGES_OUT = path.join(WEB_WIKI, "images");
const SITE_PAGES = path.join(WEB_WIKI, "site-pages");

const GUIDE_EN = path.join(
  REPO,
  "src",
  "main",
  "resources",
  "Common",
  "Docs",
  "Hexvane_AetherhavenWiki",
  "en-US"
);
const GUIDE_IMG = path.join(
  REPO,
  "src",
  "main",
  "resources",
  "Common",
  "UI",
  "Custom",
  "Aetherhaven",
  "wiki"
);
const PORTRAITS = path.join(
  REPO,
  "src",
  "main",
  "resources",
  "Common",
  "Icons",
  "ModelsGenerated"
);
const CROSSMOD = path.join(REPO, "tutorials", "crossmod-integration.md");

const SIBLING_TOPICS = [
  {
    id: "villager_mechanic",
    path: path.join(
      REPO,
      "..",
      "Machinaria",
      "src",
      "main",
      "resources",
      "Server",
      "Aetherhaven",
      "GuideTopics",
      "en-US",
      "villager_mechanic.md"
    ),
    portraitSrc: path.join(
      REPO,
      "..",
      "Machinaria",
      "src",
      "main",
      "resources",
      "Common",
      "Icons",
      "ModelsGenerated",
      "Machinaria_Mechanic.png"
    ),
    portraitName: "Machinaria_Mechanic.png",
  },
  {
    id: "villager_fisherman",
    path: path.join(
      REPO,
      "..",
      "CozyTales-Fishing",
      "src",
      "main",
      "resources",
      "Server",
      "Aetherhaven",
      "GuideTopics",
      "en-US",
      "villager_fisherman.md"
    ),
    portraitSrc: path.join(
      REPO,
      "..",
      "CozyTales-Fishing",
      "src",
      "main",
      "resources",
      "Common",
      "Icons",
      "ModelsGenerated",
      "CozyFishing_Fisherman.png"
    ),
    portraitName: "CozyFishing_Fisherman.png",
  },
];

/** npcRoleId → portrait filename under images/ (in-game Icons/ModelsGenerated) */
const ROLE_PORTRAITS = {
  Aetherhaven_Elder_Lyren: "Aetherhaven_Elder_Lyren.png",
  Aetherhaven_Innkeeper: "Aetherhaven_Innkeeper.png",
  Aetherhaven_Merchant: "Aetherhaven_Merchant.png",
  Aetherhaven_Blacksmith: "Aetherhaven_Blacksmith.png",
  Aetherhaven_Farmer: "Aetherhaven_Farmer.png",
  Aetherhaven_Priestess: "Aetherhaven_Priestess.png",
  Aetherhaven_Miner: "Aetherhaven_Miner.png",
  Aetherhaven_Logger: "Aetherhaven_Logger.png",
  Aetherhaven_Rancher: "Aetherhaven_Rancher.png",
  Aetherhaven_Crystal_Keeper: "Aetherhaven_Crystal_Keeper.png",
  Aetherhaven_Pyrotechnic: "Aetherhaven_Pyrotechnic.png",
  Aetherhaven_Florist: "Aetherhaven_Florist.png",
  Aetherhaven_Builder: "Aetherhaven_Builder.png",
  Aetherhaven_Guild_Master: "Aetherhaven_Guild_Master.png",
  Aetherhaven_Bard: "Aetherhaven_Bard.png",
  Machinaria_Mechanic: "Machinaria_Mechanic.png",
  CozyFishing_Fisherman: "CozyFishing_Fisherman.png",
};

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function copyFile(src, dest) {
  ensureDir(path.dirname(dest));
  fs.copyFileSync(src, dest);
}

function stripYamlQuotes(val) {
  if (val.length >= 2) {
    const a = val[0];
    const b = val[val.length - 1];
    if ((a === '"' && b === '"') || (a === "'" && b === "'")) {
      return val.slice(1, -1);
    }
  }
  return val;
}

function parseFrontmatter(raw) {
  const text = raw.replace(/\r\n/g, "\n");
  if (!text.startsWith("---\n")) {
    return { name: "", description: "", npcRoleId: null, subTopics: [], body: text.trim() };
  }
  const end = text.indexOf("\n---\n", 4);
  if (end < 0) {
    return { name: "", description: "", npcRoleId: null, subTopics: [], body: text.trim() };
  }
  const fm = text.slice(4, end);
  const body = text.slice(end + 5).trim();
  let name = "";
  let description = "";
  let npcRoleId = null;
  const subTopics = [];
  let inSubs = false;
  for (const line of fm.split("\n")) {
    const t = line.trim();
    if (!t) continue;
    if (inSubs) {
      if (t.startsWith("- ")) {
        subTopics.push(t.slice(2).trim());
        continue;
      }
      const colonEarly = t.indexOf(":");
      if (colonEarly > 0 && !t.startsWith("-")) {
        const maybeKey = t.slice(0, colonEarly).trim();
        if (maybeKey && !maybeKey.includes(" ")) {
          inSubs = false;
        } else {
          continue;
        }
      } else {
        continue;
      }
    }
    if (t.startsWith("sub-topics:")) {
      inSubs = true;
      continue;
    }
    const colon = t.indexOf(":");
    if (colon <= 0) continue;
    const key = t.slice(0, colon).trim();
    const val = stripYamlQuotes(t.slice(colon + 1).trim());
    if (key === "name") name = val;
    else if (key === "description") description = val;
    else if (key === "npcRoleId") npcRoleId = val || null;
  }
  return { name, description, npcRoleId, subTopics, body };
}

function humanizeId(id) {
  return id.replace(/_/g, " ");
}

function plainTextFromMarkdown(body) {
  return body
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/!\[[^\]]*\]\([^)]+\)/g, " ")
    .replace(/\[[^\]]*\]\([^)]+\)/g, " ")
    .replace(/[#>*_`~|]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function loadTopicMeta(id, filePath) {
  const raw = fs.readFileSync(filePath, "utf8");
  const parsed = parseFrontmatter(raw);
  return {
    id,
    name: parsed.name || humanizeId(id),
    description: parsed.description || "",
    npcRoleId: parsed.npcRoleId,
    subTopics: parsed.subTopics,
    body: parsed.body,
    plain: plainTextFromMarkdown(parsed.body),
  };
}

function walkTree(id, depth, byId, completed, treeChildren) {
  if (!id || completed.has(id)) return null;
  completed.add(id);
  const meta = byId.get(id);
  if (!meta) {
    console.warn(`Missing topic file for nav id: ${id}`);
    return null;
  }
  const children = [];
  for (const childId of meta.subTopics) {
    const node = walkTree(childId, depth + 1, byId, completed, true);
    if (node) children.push(node);
  }
  return {
    id,
    name: meta.name,
    depth,
    children,
  };
}

function flattenNav(nodes, out = []) {
  for (const n of nodes) {
    out.push({ id: n.id, name: n.name, depth: n.depth });
    flattenNav(n.children, out);
  }
  return out;
}

function buildTitleMap(byId) {
  const titleMap = {};
  for (const meta of byId.values()) {
    titleMap[meta.name] = meta.id;
    // Common aliases used in guide prose
    if (meta.id === "mechanic_player_shop") {
      titleMap["Player shop"] = meta.id;
      titleMap["shop spots"] = meta.id;
    }
    if (meta.id === "mechanic_charter") {
      titleMap["Town charter"] = meta.id;
      titleMap["charter"] = meta.id;
    }
    if (meta.id === "getting_started") {
      titleMap["Getting Started"] = meta.id;
    }
  }
  return titleMap;
}

function portraitRemap(byId) {
  const map = {};
  for (const meta of byId.values()) {
    if (!meta.npcRoleId) continue;
    const file = ROLE_PORTRAITS[meta.npcRoleId];
    if (!file) continue;
    // Markdown uses wiki/villager_*.png
    const topicKey = `villager_${meta.id.replace(/^villager_/, "")}`;
    map[`wiki/${meta.id}.png`] = `/wiki/images/${file}`;
    map[`wiki/${topicKey}.png`] = `/wiki/images/${file}`;
  }
  // Explicit topic-id keys for clarity
  for (const [role, file] of Object.entries(ROLE_PORTRAITS)) {
    const entry = [...byId.values()].find((m) => m.npcRoleId === role);
    if (entry) {
      map[`wiki/${entry.id}.png`] = `/wiki/images/${file}`;
    }
  }
  return map;
}

function main() {
  if (!fs.existsSync(GUIDE_EN)) {
    console.error(`Guide source missing: ${GUIDE_EN}`);
    process.exit(1);
  }

  ensureDir(TOPICS_OUT);
  ensureDir(IMAGES_OUT);

  // Clear old topics (keep site-pages directory)
  for (const f of fs.readdirSync(TOPICS_OUT)) {
    fs.unlinkSync(path.join(TOPICS_OUT, f));
  }
  for (const f of fs.readdirSync(IMAGES_OUT)) {
    if (f === ".gitkeep") continue;
    fs.unlinkSync(path.join(IMAGES_OUT, f));
  }

  // Core guide topics
  for (const f of fs.readdirSync(GUIDE_EN)) {
    if (!f.endsWith(".md")) continue;
    copyFile(path.join(GUIDE_EN, f), path.join(TOPICS_OUT, f));
  }

  // Guide images
  if (fs.existsSync(GUIDE_IMG)) {
    for (const f of fs.readdirSync(GUIDE_IMG)) {
      if (!/\.(png|jpg|jpeg|webp)$/i.test(f)) continue;
      copyFile(path.join(GUIDE_IMG, f), path.join(IMAGES_OUT, f));
    }
  }

  // Portraits
  if (fs.existsSync(PORTRAITS)) {
    for (const f of fs.readdirSync(PORTRAITS)) {
      if (!f.startsWith("Aetherhaven_") || !f.endsWith(".png")) continue;
      copyFile(path.join(PORTRAITS, f), path.join(IMAGES_OUT, f));
    }
  }

  // Crossmod
  if (fs.existsSync(CROSSMOD)) {
    const body = fs.readFileSync(CROSSMOD, "utf8").replace(/\r\n/g, "\n").trim();
    const wrapped = `---
name: Crossmod integration
description: How other mods add villagers, buildings, dialogue, and more via asset packs
author: Hexvane
---

${body}
`;
    fs.writeFileSync(path.join(TOPICS_OUT, "crossmod_integration.md"), wrapped, "utf8");
  } else {
    console.warn(`Crossmod guide missing: ${CROSSMOD}`);
  }

  // Sibling addon GuideTopics + portraits
  for (const sibling of SIBLING_TOPICS) {
    if (fs.existsSync(sibling.path)) {
      copyFile(sibling.path, path.join(TOPICS_OUT, `${sibling.id}.md`));
      console.log(`Copied addon topic ${sibling.id}`);
    } else {
      console.warn(`Addon topic not found (skip): ${sibling.path}`);
    }
    if (fs.existsSync(sibling.portraitSrc)) {
      copyFile(sibling.portraitSrc, path.join(IMAGES_OUT, sibling.portraitName));
    } else {
      console.warn(`Addon portrait not found (skip): ${sibling.portraitSrc}`);
    }
  }

  // Website-authored site pages
  if (fs.existsSync(SITE_PAGES)) {
    for (const f of fs.readdirSync(SITE_PAGES)) {
      if (!f.endsWith(".md")) continue;
      copyFile(path.join(SITE_PAGES, f), path.join(TOPICS_OUT, f));
    }
  }

  // Build metadata maps
  const byId = new Map();
  for (const f of fs.readdirSync(TOPICS_OUT)) {
    if (!f.endsWith(".md")) continue;
    const id = f.slice(0, -3);
    byId.set(id, loadTopicMeta(id, path.join(TOPICS_OUT, f)));
  }

  // Core welcome tree (must not include addon villagers from patches)
  const completed = new Set();
  const welcomeRoot = walkTree("welcome", 0, byId, completed, true);

  // Addons section (website tree)
  const addonsRoot = walkTree("addons", 0, byId, new Set(), true);

  // Developers (website-authored hub + crossmod)
  const developersRoot = walkTree("developers", 0, byId, new Set(), true);

  const tree = [];
  if (welcomeRoot) tree.push(welcomeRoot);
  if (addonsRoot) tree.push(addonsRoot);
  if (developersRoot) tree.push(developersRoot);

  // Flat list for prev/next — depth-first across all sections
  const flat = flattenNav(tree);

  const titleMap = buildTitleMap(byId);
  const portraits = portraitRemap(byId);

  const nav = {
    tree,
    flat,
    titleMap,
    portraitRemap: portraits,
  };
  fs.writeFileSync(path.join(WEB_WIKI, "nav.json"), JSON.stringify(nav, null, 2), "utf8");

  const searchIndex = [...byId.values()].map((m) => ({
    id: m.id,
    name: m.name,
    description: m.description,
    text: m.plain.slice(0, 4000),
  }));
  fs.writeFileSync(
    path.join(WEB_WIKI, "search-index.json"),
    JSON.stringify(searchIndex),
    "utf8"
  );

  console.log(
    `Synced ${byId.size} topics, ${fs.readdirSync(IMAGES_OUT).length} images → ${WEB_WIKI}`
  );
}

main();
