/**
 * Optional AdSense loader.
 * - With ADSENSE_CLIENT_ID set (via site-config), the verification script is already
 *   injected into HTML head by the server; this file fills ad units when ads.enabled.
 * - Fail silently when ads are off or config cannot be loaded.
 */
(async function initAds() {
  const SLOT_SELECTORS = {
    browseHeader: "#ad-browse-header",
    wikiHeader: "#ad-wiki-header",
  };

  let config;
  try {
    const res = await fetch("/api/site-config");
    if (!res.ok) return;
    config = await res.json();
  } catch {
    return;
  }

  const ads = config?.ads;
  if (!ads?.clientId || ads.provider !== "adsense") {
    return;
  }

  // Ensure the client script is present (backup if served from static HTML without injection).
  ensureAdSenseScript(ads.clientId);

  if (!ads.enabled) {
    return;
  }

  const slots = ads.slots || {};
  for (const [key, selector] of Object.entries(SLOT_SELECTORS)) {
    const slotId = slots[key];
    const el = document.querySelector(selector);
    if (slotId && el) {
      fillSlot(el, ads.clientId, slotId);
    }
  }
})();

function ensureAdSenseScript(clientId) {
  const src = `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(
    clientId
  )}`;
  if (
    document.querySelector(
      'script[src^="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"]'
    )
  ) {
    return;
  }
  const script = document.createElement("script");
  script.async = true;
  script.src = src;
  script.crossOrigin = "anonymous";
  document.head.appendChild(script);
}

function fillSlot(container, clientId, slotId) {
  if (!container || container.dataset.adFilled === "1") return;
  container.dataset.adFilled = "1";
  container.hidden = false;
  container.innerHTML = `
    <p class="ad-label">Advertisement</p>
    <ins class="adsbygoogle"
      style="display:block"
      data-ad-client="${escapeAttr(clientId)}"
      data-ad-slot="${escapeAttr(slotId)}"
      data-ad-format="auto"
      data-full-width-responsive="true"></ins>
  `;
  try {
    (window.adsbygoogle = window.adsbygoogle || []).push({});
  } catch {
    /* AdSense may throw if blocked; leave the labeled slot empty. */
  }
}

function escapeAttr(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
