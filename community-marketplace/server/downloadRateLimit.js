/**
 * In-memory rate limits for in-game install download reports.
 */

const buckets = new Map();

function consume(key, max, windowMs) {
  const now = Date.now();
  let entry = buckets.get(key);
  if (!entry || now >= entry.resetAt) {
    entry = { count: 0, resetAt: now + windowMs };
    buckets.set(key, entry);
  }
  entry.count += 1;
  return entry.count <= max;
}

export function createDownloadRateLimit(options) {
  const maxPerIp = options.maxPerIp ?? 120;
  const maxPerBuildingIp = options.maxPerBuildingIp ?? 5;
  const maxPerPlayer = options.maxPerPlayer ?? 120;
  const windowMs = options.windowMs ?? 60 * 60 * 1000;

  return function downloadRateLimit(req, res, next) {
    const ip = String(req.ip || req.socket?.remoteAddress || "unknown");
    const buildingId = String(req.params?.id || "")
      .trim()
      .toLowerCase();
    const uuid = String(req.get("X-Player-Uuid") || "")
      .trim()
      .toLowerCase();

    if (!consume(`dl-ip:${ip}`, maxPerIp, windowMs)) {
      res.status(429).json({ error: "rate_limited_ip" });
      return;
    }
    if (buildingId && !consume(`dl-ip-building:${ip}:${buildingId}`, maxPerBuildingIp, windowMs)) {
      res.status(429).json({ error: "rate_limited_building" });
      return;
    }
    if (uuid && !consume(`dl-uuid:${uuid}`, maxPerPlayer, windowMs)) {
      res.status(429).json({ error: "rate_limited_player" });
      return;
    }
    next();
  };
}
