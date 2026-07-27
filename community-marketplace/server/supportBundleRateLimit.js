/**
 * Rate limits for in-game support bundle uploads.
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

export function createSupportBundleRateLimit(options = {}) {
  const maxPerPlayer = options.maxPerPlayer ?? 3;
  const maxPerIp = options.maxPerIp ?? 10;
  const playerWindowMs = options.playerWindowMs ?? 24 * 60 * 60 * 1000;
  const ipWindowMs = options.ipWindowMs ?? 60 * 60 * 1000;

  return function supportBundleRateLimit(req, res, next) {
    const ip = String(req.ip || req.socket?.remoteAddress || "unknown");
    const uuid = String(req.get("X-Player-Uuid") || "").trim().toLowerCase();

    if (!consume(`support-ip:${ip}`, maxPerIp, ipWindowMs)) {
      res.status(429).json({ error: "rate_limited_ip" });
      return;
    }
    if (uuid && !consume(`support-uuid:${uuid}`, maxPerPlayer, playerWindowMs)) {
      res.status(429).json({ error: "rate_limited_player" });
      return;
    }
    next();
  };
}
