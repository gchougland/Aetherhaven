#!/usr/bin/env node
/**
 * Push the synced viewer assets onto the Railway volume, then prove they landed.
 *
 * `railway volume files upload <local> <remote>` treats an existing REMOTE_PATH as the
 * parent to drop the folder into, so naming the destination `/data/hytale-assets` puts
 * everything at `/data/hytale-assets/hytale-assets` and the site keeps serving whatever
 * was there before. Nothing errors, models just quietly turn grey. The destination has
 * to be the parent, `/data`, so the folder name lands as `hytale-assets`.
 *
 * `--overwrite` is deliberately not passed: it replaces REMOTE_PATH, and REMOTE_PATH is
 * `/data`, which also holds submissions, approved builds and screenshots.
 *
 * Usage:
 *   npm run upload-hytale-assets
 *   npm run upload-hytale-assets -- --remote /data --base https://aetherhaven.net
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const marketplaceRoot = path.resolve(__dirname, "..");
const localDir = path.join(marketplaceRoot, "web", "hytale-assets");

const args = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

/** Parent of the served directory, never the served directory itself. */
const remoteParent = flag("--remote", "/data");
const baseUrl = flag("--base", "https://aetherhaven.net");

function run(command, commandArgs) {
  console.log(`\n> ${command} ${commandArgs.join(" ")}`);
  const res = spawnSync(command, commandArgs, {
    cwd: marketplaceRoot,
    stdio: "inherit",
    shell: process.platform === "win32",
  });
  return res.status ?? 1;
}

if (!fs.existsSync(path.join(localDir, "catalog", "block_catalog.json"))) {
  console.error(`No synced assets at ${localDir}. Run: npm run sync-hytale-assets`);
  process.exit(1);
}

// Never ship a sync that is already missing files locally.
if (run(process.execPath, [path.join("scripts", "verify-viewer-assets.mjs")]) !== 0) {
  console.error("\nLocal assets are incomplete. Re-run the sync before uploading.");
  process.exit(1);
}

if (run("railway", ["volume", "files", "upload", path.join("web", "hytale-assets"), remoteParent]) !== 0) {
  console.error("\nUpload failed.");
  process.exit(1);
}

console.log(`\nUpload finished. Checking what ${baseUrl} actually serves ...`);
const verified = run(process.execPath, [
  path.join("scripts", "verify-viewer-assets.mjs"),
  "--base",
  baseUrl,
  "--no-prefabs",
  "--concurrency",
  "32",
]);
if (verified !== 0) {
  console.error(
    `\nFiles are missing live. Check that nothing landed at ${remoteParent}/hytale-assets/hytale-assets.`
  );
  process.exit(1);
}
console.log("\nVolume is up to date.");
