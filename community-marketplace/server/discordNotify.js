const FETCH_TIMEOUT_MS = 5000;
const DESCRIPTION_MAX = 500;

/**
 * @param {unknown} value
 * @returns {string}
 */
function truncateDescription(value) {
  const text = String(value || "")
    .trim()
    .replace(/\s+/g, " ");
  if (!text) {
    return "";
  }
  if (text.length <= DESCRIPTION_MAX) {
    return text;
  }
  return `${text.slice(0, DESCRIPTION_MAX - 1)}…`;
}

/**
 * @param {string} name
 * @returns {string}
 */
function resolveWebhookUrl(name) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    return "";
  }
  return value.trim();
}

/**
 * @param {string} url
 * @param {object} payload
 */
async function postWebhook(url, payload) {
  if (!url) {
    return;
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
    if (!res.ok) {
      const body = await res.text().catch(() => "");
      console.warn(`[discord] webhook ${res.status}: ${body.slice(0, 200)}`);
    }
  } catch (e) {
    console.warn(`[discord] webhook failed: ${e?.message || e}`);
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Admin channel: new submission awaiting approval.
 * @param {{ publicBaseUrl: string, submissionId: string, proposedId: string, displayName?: string, description?: string, creatorName?: string, submittedAt?: string }} opts
 */
export async function notifyBuildingPending(opts) {
  const url = resolveWebhookUrl("DISCORD_PENDING_WEBHOOK_URL");
  if (!url) {
    return;
  }
  const base = String(opts.publicBaseUrl || "").replace(/\/$/, "");
  const displayName = String(opts.displayName || "Untitled").trim() || "Untitled";
  const description = truncateDescription(opts.description);
  const fields = [
    { name: "Creator", value: String(opts.creatorName || "Unknown").trim() || "Unknown", inline: true },
    { name: "Proposed ID", value: `\`${opts.proposedId || "—"}\``, inline: true },
  ];
  /** @type {Record<string, unknown>} */
  const embed = {
    title: `New submission: ${displayName}`,
    url: `${base}/admin.html`,
    color: 0xe0a040,
    fields,
    footer: { text: "Aetherhaven Community Marketplace" },
  };
  if (description) {
    embed.description = description;
  }
  if (opts.submittedAt) {
    embed.timestamp = opts.submittedAt;
  }
  await postWebhook(url, { embeds: [embed] });
}

/**
 * Public channel: newly approved building.
 * @param {{ publicBaseUrl: string, id: string, displayName?: string, description?: string, creatorName?: string, tags?: string[], styleId?: string, approvedAt?: string, hasIcon?: boolean }} opts
 */
export async function notifyBuildingApproved(opts) {
  const url = resolveWebhookUrl("DISCORD_APPROVED_WEBHOOK_URL");
  if (!url) {
    return;
  }
  const base = String(opts.publicBaseUrl || "").replace(/\/$/, "");
  const displayName = String(opts.displayName || "Untitled").trim() || "Untitled";
  const description = truncateDescription(opts.description);
  const fields = [
    { name: "Creator", value: String(opts.creatorName || "Unknown").trim() || "Unknown", inline: true },
  ];
  if (opts.styleId) {
    fields.push({ name: "Style", value: String(opts.styleId), inline: true });
  }
  const tags = Array.isArray(opts.tags) ? opts.tags.filter(Boolean) : [];
  if (tags.length) {
    fields.push({ name: "Tags", value: tags.join(", "), inline: false });
  }
  /** @type {Record<string, unknown>} */
  const embed = {
    title: displayName,
    url: base || undefined,
    color: 0x4a9e6e,
    fields,
    footer: { text: "Aetherhaven Community Marketplace" },
  };
  if (description) {
    embed.description = description;
  }
  if (opts.approvedAt) {
    embed.timestamp = opts.approvedAt;
  }
  if (opts.hasIcon && opts.id && base) {
    embed.thumbnail = {
      url: `${base}/api/v1/buildings/${encodeURIComponent(opts.id)}/icon.png`,
    };
  }
  await postWebhook(url, { embeds: [embed] });
}
