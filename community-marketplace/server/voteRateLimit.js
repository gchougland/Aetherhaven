/**
 * In-memory rate limits for catalog upvote toggles.
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

export function createVoteRateLimit(options) {
  const maxPerUser = options.maxPerUser ?? 60;
  const windowMs = options.windowMs ?? 60 * 60 * 1000;

  return function voteRateLimit(req, res, next) {
    const uuid = String(req.session?.user?.profile?.uuid || "")
      .trim()
      .toLowerCase();
    if (!uuid) {
      res.status(401).json({ error: "login_required" });
      return;
    }
    if (!consume(`vote:${uuid}`, maxPerUser, windowMs)) {
      res.status(429).json({ error: "rate_limited" });
      return;
    }
    next();
  };
}
