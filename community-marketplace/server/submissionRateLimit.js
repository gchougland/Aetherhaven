/**
 * In-memory rate limits for public mod submissions.
 * Abuse is further limited by the moderation queue (nothing is published without admin approval).
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

export function createSubmissionRateLimit(options) {
  const maxPerPlayer = options.maxPerPlayer ?? 10;
  const maxPerIp = options.maxPerIp ?? 30;
  const playerWindowMs = options.playerWindowMs ?? 24 * 60 * 60 * 1000;
  const ipWindowMs = options.ipWindowMs ?? 60 * 60 * 1000;

  return function submissionRateLimit(req, res, next) {
    const ip = String(req.ip || req.socket?.remoteAddress || "unknown");
    const uuid = String(req.get("X-Player-Uuid") || "").trim().toLowerCase();

    if (!consume(`ip:${ip}`, maxPerIp, ipWindowMs)) {
      res.status(429).json({ error: "rate_limited_ip" });
      return;
    }
    if (uuid && !consume(`uuid:${uuid}`, maxPerPlayer, playerWindowMs)) {
      res.status(429).json({ error: "rate_limited_player" });
      return;
    }
    next();
  };
}
