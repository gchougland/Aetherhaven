import fs from "node:fs";

/**
 * @param {unknown} raw
 * @returns {"North"|"East"|"South"|"West"}
 */
export function normalizeFrontFacing(raw) {
  const t = String(raw || "").trim();
  if (/^east$/i.test(t)) return "East";
  if (/^south$/i.test(t)) return "South";
  if (/^west$/i.test(t)) return "West";
  return "North";
}

/**
 * Read frontFacing from a building.json / prop.json path if present.
 * @param {string|null|undefined} definitionPath
 * @returns {"North"|"East"|"South"|"West"}
 */
export function readFrontFacingFromDefinition(definitionPath) {
  const file = String(definitionPath || "").trim();
  if (!file || !fs.existsSync(file)) {
    return "North";
  }
  try {
    const raw = JSON.parse(fs.readFileSync(file, "utf8"));
    return normalizeFrontFacing(raw?.frontFacing);
  } catch {
    return "North";
  }
}
