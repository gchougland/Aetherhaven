const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/**
 * @param {unknown} value
 * @returns {string}
 */
export function normalizePlayerUuid(value) {
  const id = String(value || "")
    .trim()
    .toLowerCase();
  return UUID_RE.test(id) ? id : "";
}

/**
 * Hytale web login exposes profile.uuid while in-game requests use the identity token sub.
 * Favorites must consider both ids as the same player when present on the session.
 *
 * @param {object | null | undefined} user
 * @returns {string[]}
 */
export function sessionIdentityUuids(user) {
  if (!user) {
    return [];
  }
  const out = [];
  const seen = new Set();
  for (const raw of [user.profile?.uuid, user.sub]) {
    const id = normalizePlayerUuid(raw);
    if (id && !seen.has(id)) {
      seen.add(id);
      out.push(id);
    }
  }
  return out;
}

/**
 * @param {import("express").Request} req
 * @returns {string[]}
 */
export function requestFavoriteIdentityUuids(req) {
  const sessionUuids = sessionIdentityUuids(req.session?.user);
  if (sessionUuids.length > 0) {
    return sessionUuids;
  }
  const headerUuid = normalizePlayerUuid(req.get("X-Player-Uuid"));
  return headerUuid ? [headerUuid] : [];
}

/**
 * @param {string[]} uuids
 * @returns {string}
 */
export function canonicalFavoriteUuid(uuids) {
  return uuids.length > 0 ? uuids[0] : "";
}
