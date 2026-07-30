import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { marked } from "marked";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.join(__dirname, "..", "web");
const topicsDir = path.join(webRoot, "wiki", "topics");
const navPath = path.join(webRoot, "wiki", "nav.json");

/** @type {{ portraitRemap?: Record<string, string> } | null} */
let navCache = null;

function loadNav() {
  if (navCache) return navCache;
  try {
    navCache = JSON.parse(fs.readFileSync(navPath, "utf8"));
  } catch {
    navCache = {};
  }
  return navCache;
}

function stripFrontmatter(raw) {
  const text = raw.replace(/\r\n/g, "\n");
  if (!text.startsWith("---\n")) {
    return { meta: {}, body: text };
  }
  const end = text.indexOf("\n---\n", 4);
  if (end < 0) {
    return { meta: {}, body: text };
  }
  const fmBlock = text.slice(4, end);
  const body = text.slice(end + 5).trim();
  const meta = {};
  for (const line of fmBlock.split("\n")) {
    const idx = line.indexOf(":");
    if (idx < 0) continue;
    const key = line.slice(0, idx).trim();
    let value = line.slice(idx + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    meta[key] = value;
  }
  return { meta, body };
}

function rewriteMarkdownImages(md) {
  const remap = loadNav().portraitRemap || {};
  return md.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (full, alt, src) => {
    const trimmed = src.trim();
    if (remap[trimmed]) {
      return `![${alt}](${remap[trimmed]})`;
    }
    if (trimmed.startsWith("wiki/")) {
      return `![${alt}](/wiki/images/${trimmed.slice(5)})`;
    }
    return full;
  });
}

function rewriteMarkdownLinks(md) {
  return md.replace(/\[([^\]]+)\]\(\?topic=([^)]+)\)/g, (_, label, id) => {
    const topicId = id.trim();
    return `[${label}](/wiki.html?topic=${encodeURIComponent(topicId)})`;
  });
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/**
 * @param {string} topicId
 * @returns {{ html: string, title: string, description: string } | null}
 */
export function renderWikiTopic(topicId) {
  const safeId = topicId.replace(/[^a-zA-Z0-9_-]/g, "");
  if (!safeId) return null;
  const filePath = path.join(topicsDir, `${safeId}.md`);
  if (!fs.existsSync(filePath)) return null;

  const raw = fs.readFileSync(filePath, "utf8");
  const { meta, body } = stripFrontmatter(raw);
  const prepared = rewriteMarkdownLinks(rewriteMarkdownImages(body));
  marked.setOptions({ gfm: true, breaks: false });
  const html = marked.parse(prepared);
  const title = meta.name || safeId;
  const description = meta.description || `Aetherhaven wiki: ${title}`;
  return { html, title, description };
}

export function listWikiTopicIds() {
  if (!fs.existsSync(topicsDir)) return [];
  return fs
    .readdirSync(topicsDir)
    .filter((f) => f.endsWith(".md"))
    .map((f) => f.slice(0, -3))
    .sort();
}

export function buildSitemapXml(baseUrl) {
  const staticPages = [
    "",
    "index.html",
    "wiki.html",
    "about.html",
    "terms.html",
    "privacy.html",
  ];
  const urls = staticPages.map((p) => {
    const loc = p ? `${baseUrl}/${p}` : `${baseUrl}/`;
    return `  <url><loc>${escapeHtml(loc)}</loc></url>`;
  });
  for (const id of listWikiTopicIds()) {
    const loc = `${baseUrl}/wiki.html?topic=${encodeURIComponent(id)}`;
    urls.push(`  <url><loc>${escapeHtml(loc)}</loc></url>`);
  }
  return (
    '<?xml version="1.0" encoding="UTF-8"?>\n' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    urls.join("\n") +
    "\n</urlset>\n"
  );
}

export function buildRobotsTxt(baseUrl) {
  return `User-agent: *\nAllow: /\n\nSitemap: ${baseUrl}/sitemap.xml\n`;
}
