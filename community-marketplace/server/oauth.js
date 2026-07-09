import crypto from "node:crypto";

const b64url = (buf) =>
  buf
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

/**
 * @param {object} cfg
 * @param {string} cfg.issuer
 * @param {string} cfg.clientId
 * @param {string} cfg.clientSecret
 * @param {string} cfg.redirectUri
 */
export function createOidc(cfg) {
  const { issuer, clientId, clientSecret, redirectUri } = cfg;
  const enabled = Boolean(clientId && clientSecret && redirectUri);

  /** @type {Promise<any>|null} */
  let discoveryPromise = null;

  async function discovery() {
    if (!enabled) {
      throw new Error("oidc_not_configured");
    }
    if (!discoveryPromise) {
      discoveryPromise = fetch(`${issuer}/.well-known/openid-configuration`).then((r) => r.json());
    }
    return discoveryPromise;
  }

  function createPkce() {
    const verifier = b64url(crypto.randomBytes(32));
    const challenge = b64url(crypto.createHash("sha256").update(verifier).digest());
    return { verifier, challenge };
  }

  async function authorizationUrl(state, pkceChallenge) {
    const doc = await discovery();
    const params = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope: "openid hytale:profile",
      state,
      code_challenge: pkceChallenge,
      code_challenge_method: "S256",
    });
    return `${doc.authorization_endpoint}?${params}`;
  }

  async function exchangeCode(code, verifier) {
    const doc = await discovery();
    const body = new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: redirectUri,
      code_verifier: verifier,
    });
    const auth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
    const res = await fetch(doc.token_endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Authorization: `Basic ${auth}`,
      },
      body,
    });
    const json = await res.json();
    if (!res.ok) {
      throw new Error(json.error_description || json.error || "token_exchange_failed");
    }
    return json;
  }

  async function userInfo(accessToken) {
    const doc = await discovery();
    const res = await fetch(doc.userinfo_endpoint, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!res.ok) {
      throw new Error("userinfo_failed");
    }
    return res.json();
  }

  return { enabled, createPkce, authorizationUrl, exchangeCode, userInfo };
}
