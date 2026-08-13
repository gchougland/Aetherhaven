import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const BINARY_NAMES =
  process.platform === "win32"
    ? ["chromium.exe", "chrome.exe", "msedge.exe"]
    : ["chromium", "chromium-browser", "google-chrome", "google-chrome-stable", "chrome"];

/**
 * @param {string} filePath
 */
export function isChromiumExecutable(filePath) {
  if (!filePath) {
    return false;
  }
  try {
    fs.accessSync(filePath, fs.constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

/**
 * @param {string[]} candidates
 */
function firstExisting(candidates) {
  for (const candidate of candidates) {
    if (candidate && isChromiumExecutable(candidate)) {
      return candidate;
    }
  }
  return "";
}

function pathCandidates() {
  const dirs = String(process.env.PATH || "")
    .split(path.delimiter)
    .filter(Boolean);
  const out = [];
  for (const dir of dirs) {
    for (const name of BINARY_NAMES) {
      out.push(path.join(dir, name));
    }
  }
  return out;
}

/**
 * @param {string} root
 */
export function playwrightLayoutCandidates(root) {
  if (!root) {
    return [];
  }
  let entries;
  try {
    entries = fs.readdirSync(root, { withFileTypes: true });
  } catch {
    return [];
  }
  const out = [];
  for (const entry of entries) {
    if (!entry.isDirectory() || !entry.name.startsWith("chromium")) {
      continue;
    }
    const base = path.join(root, entry.name);
    out.push(
      path.join(base, "chrome-linux", "chrome"),
      path.join(base, "chrome-linux", "headless_shell"),
      path.join(base, "chrome-headless-shell-linux64", "chrome-headless-shell"),
      path.join(base, "chrome-win64", "chrome.exe"),
      path.join(base, "chrome-win", "chrome.exe"),
      path.join(base, "chrome-mac", "Chromium.app", "Contents", "MacOS", "Chromium")
    );
  }
  return out;
}

/**
 * Locate a Chromium/Chrome binary for Playwright.
 * Nixpacks puts nixpkgs chromium on PATH (not /bin/chromium).
 */
export async function resolveChromiumExecutable() {
  const fromEnv = String(
    process.env.CHROMIUM_PATH || process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH || ""
  ).trim();
  if (fromEnv && isChromiumExecutable(fromEnv)) {
    return fromEnv;
  }

  const found =
    firstExisting([
      "/bin/chromium",
      "/usr/bin/chromium",
      "/usr/bin/chromium-browser",
      "/nix/var/nix/profiles/default/bin/chromium",
      path.join(os.homedir(), ".nix-profile", "bin", "chromium"),
    ]) ||
    firstExisting(pathCandidates()) ||
    firstExisting(playwrightLayoutCandidates(process.env.PLAYWRIGHT_BROWSERS_PATH || "")) ||
    firstExisting(playwrightLayoutCandidates(path.join(os.homedir(), ".cache", "ms-playwright"))) ||
    firstExisting(playwrightLayoutCandidates("/root/.cache/ms-playwright")) ||
    firstExisting(playwrightLayoutCandidates(path.join(process.cwd(), ".playwright-browsers")));

  if (found) {
    return found;
  }

  try {
    const playwright = await import("playwright-core");
    const exe = playwright.chromium.executablePath();
    if (exe && isChromiumExecutable(exe)) {
      return exe;
    }
  } catch {
    /* no bundled browser */
  }
  return "";
}
