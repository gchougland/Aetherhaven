import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { playwrightLayoutCandidates, resolveChromiumExecutable } from "../server/chromiumExecutable.js";

function tempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "aetherhaven-chromium-"));
}

function fakeChromium(dir) {
  const name = process.platform === "win32" ? "chromium.exe" : "chromium";
  const file = path.join(dir, name);
  fs.writeFileSync(file, "");
  if (process.platform !== "win32") {
    fs.chmodSync(file, 0o755);
  }
  return file;
}

test("resolveChromiumExecutable uses CHROMIUM_PATH when the file exists", async () => {
  const dir = tempDir();
  const file = fakeChromium(dir);
  const previous = process.env.CHROMIUM_PATH;
  process.env.CHROMIUM_PATH = file;
  try {
    assert.equal(path.resolve(await resolveChromiumExecutable()), path.resolve(file));
  } finally {
    if (previous === undefined) {
      delete process.env.CHROMIUM_PATH;
    } else {
      process.env.CHROMIUM_PATH = previous;
    }
  }
});

test("resolveChromiumExecutable ignores a missing CHROMIUM_PATH and searches PATH", async () => {
  const dir = tempDir();
  const file = fakeChromium(dir);
  const previousPath = process.env.PATH;
  const previousChromium = process.env.CHROMIUM_PATH;
  const previousPlaywright = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  process.env.CHROMIUM_PATH = path.join(dir, "does-not-exist");
  delete process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  process.env.PATH = `${dir}${path.delimiter}${previousPath || ""}`;
  try {
    assert.equal(path.resolve(await resolveChromiumExecutable()), path.resolve(file));
  } finally {
    process.env.PATH = previousPath;
    if (previousChromium === undefined) {
      delete process.env.CHROMIUM_PATH;
    } else {
      process.env.CHROMIUM_PATH = previousChromium;
    }
    if (previousPlaywright === undefined) {
      delete process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
    } else {
      process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = previousPlaywright;
    }
  }
});

test("playwrightLayoutCandidates finds chrome-linux chrome", () => {
  const dir = tempDir();
  const chrome = path.join(dir, "chromium-1234", "chrome-linux", "chrome");
  fs.mkdirSync(path.dirname(chrome), { recursive: true });
  fs.writeFileSync(chrome, "");
  assert.equal(
    playwrightLayoutCandidates(dir).includes(chrome),
    true
  );
});
