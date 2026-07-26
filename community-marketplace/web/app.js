async function fetchMe() {
  const res = await fetch("/api/me");
  return res.json();
}

function userDisplayName(user) {
  return user?.profile?.username || user?.sub || "Player";
}

function userInitials(user) {
  const name = String(userDisplayName(user)).trim();
  if (!name) {
    return "?";
  }
  const parts = name.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

function currentAccountPath() {
  const path = window.location.pathname || "/";
  if (path.endsWith("/")) {
    return path === "/" ? "/" : path.slice(0, -1);
  }
  return path;
}

function accountMenuLink(href, label) {
  const current = currentAccountPath();
  const path = href.startsWith("/") ? href : `/${href}`;
  const isCurrent =
    path === "/"
      ? current === "/" || current.endsWith("/index.html")
      : current === path || current.endsWith(path);
  const currentAttr = isCurrent ? ' aria-current="page"' : "";
  return `<a href="${escapeAttr(href)}"${currentAttr}>${escapeHtml(label)}</a>`;
}

function closeAccountMenu() {
  const root = document.getElementById("accountMenu");
  if (!root) {
    return;
  }
  const toggle = root.querySelector(".account-menu-toggle");
  const dropdown = root.querySelector(".account-menu-dropdown");
  if (dropdown) {
    dropdown.hidden = true;
  }
  if (toggle) {
    toggle.setAttribute("aria-expanded", "false");
  }
}

function toggleAccountMenu() {
  const root = document.getElementById("accountMenu");
  if (!root) {
    return;
  }
  const toggle = root.querySelector(".account-menu-toggle");
  const dropdown = root.querySelector(".account-menu-dropdown");
  if (!toggle || !dropdown) {
    return;
  }
  const open = dropdown.hidden;
  dropdown.hidden = !open;
  toggle.setAttribute("aria-expanded", open ? "true" : "false");
}

let accountMenuListenersBound = false;

function setupAccountMenuListeners() {
  if (accountMenuListenersBound) {
    return;
  }
  accountMenuListenersBound = true;
  document.addEventListener("click", (event) => {
    const root = document.getElementById("accountMenu");
    if (!root || root.contains(event.target)) {
      return;
    }
    closeAccountMenu();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeAccountMenu();
    }
  });
}

function renderAccountMenu(user, isAdmin = false) {
  const root = document.getElementById("accountMenu");
  if (!root) {
    return;
  }
  setupAccountMenuListeners();
  if (!user) {
    root.innerHTML = `
      <button type="button" class="account-menu-toggle" aria-expanded="false" aria-haspopup="true" aria-label="Account menu" onclick="event.stopPropagation(); toggleAccountMenu()">
        <span class="account-avatar account-avatar--guest" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </span>
      </button>
      <div class="account-menu-dropdown" hidden role="menu">
        <a href="/auth/login">Sign in with Hytale</a>
      </div>`;
    return;
  }
  const name = userDisplayName(user);
  const initials = userInitials(user);
  root.innerHTML = `
    <button type="button" class="account-menu-toggle" aria-expanded="false" aria-haspopup="true" aria-label="Account menu for ${escapeAttr(name)}" onclick="event.stopPropagation(); toggleAccountMenu()">
      <span class="account-avatar" aria-hidden="true">${escapeHtml(initials)}</span>
    </button>
    <div class="account-menu-dropdown" hidden role="menu">
      <div class="account-menu-label">${escapeHtml(name)}</div>
      ${accountMenuLink("/account.html", "Account")}
      ${isAdmin ? accountMenuLink("/admin.html", "Admin") : ""}
      <a href="/auth/logout">Sign out</a>
    </div>`;
}

async function refreshAuthNav() {
  const me = await fetchMe();
  renderAccountMenu(me.user, me.isAdmin);
}

function formatBytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

async function fetchCatalog() {
  const res = await fetch("/api/catalog", {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error("catalog_fetch_failed");
  }
  return res.json();
}

function buildingIconHtml(iconUrl, sizeClass, usesCoverImage = false) {
  const cls = [sizeClass || "building-icon", usesCoverImage ? "building-icon--cover" : ""]
    .filter(Boolean)
    .join(" ");
  if (!iconUrl) {
    return `<div class="building-icon-wrap building-icon-wrap--placeholder" aria-hidden="true"></div>`;
  }
  return `<div class="building-icon-wrap${usesCoverImage ? " building-icon-wrap--cover" : ""}"><img class="${cls}" src="${escapeAttr(iconUrl)}" alt="" onerror="this.parentElement.classList.add('building-icon-wrap--placeholder');this.remove();" /></div>`;
}

/** Website card image: cover screenshot when set, otherwise the in-game token icon. */
function buildingCardImageUrl(entry) {
  if (!entry) {
    return "";
  }
  if (entry.usesCoverImage && entry.coverImageUrl) {
    return entry.coverImageUrl;
  }
  return entry.iconUrl || "";
}

function showStatusError(statusEl, message) {
  if (!statusEl) return;
  statusEl.hidden = false;
  statusEl.classList.add("status--error");
  statusEl.textContent = message;
}

function emptyStateHtml(message) {
  return `<p class="empty-state">${escapeHtml(message)}</p>`;
}

function upvoteControlHtml(entry, canVote) {
  const count = entry.upvoteCount || 0;
  const active = entry.userHasUpvoted ? " upvote-btn--active" : "";
  if (!canVote) {
    return `<a class="upvote-btn" href="/auth/login" title="Sign in to upvote" aria-label="Sign in to upvote (${count} upvotes)" onclick="event.stopPropagation()"><span class="upvote-arrow" aria-hidden="true">▲</span><span class="upvote-count">${count}</span></a>`;
  }
  return `<button type="button" class="upvote-btn${active}" data-building-id="${escapeAttr(entry.id)}" onclick="event.stopPropagation(); toggleUpvote('${escapeAttr(entry.id)}', this)" aria-pressed="${entry.userHasUpvoted ? "true" : "false"}" aria-label="Upvote (${count})"><span class="upvote-arrow" aria-hidden="true">▲</span><span class="upvote-count">${count}</span></button>`;
}

function favoriteControlHtml(entry, canFavorite) {
  const favorited = Boolean(entry.userHasFavorited);
  const active = favorited ? " upvote-btn--active" : "";
  const star = favorited ? "★" : "☆";
  const label = favorited ? "Remove from favorites" : "Add to favorites";
  if (!canFavorite) {
    return `<a class="upvote-btn favorite-btn" href="/auth/login" title="Sign in to save favorites" aria-label="Sign in to save favorites" onclick="event.stopPropagation()"><span class="favorite-star" aria-hidden="true">☆</span></a>`;
  }
  return `<button type="button" class="upvote-btn favorite-btn${active}" data-building-id="${escapeAttr(entry.id)}" onclick="event.stopPropagation(); toggleFavorite('${escapeAttr(entry.id)}', this)" aria-pressed="${favorited ? "true" : "false"}" aria-label="${escapeAttr(label)}" title="${escapeAttr(label)}"><span class="favorite-star" aria-hidden="true">${star}</span></button>`;
}

function buildingCardHeaderHtml(entry, canVote) {
  return `
    ${buildingIconHtml(buildingCardImageUrl(entry), null, Boolean(entry.usesCoverImage))}
    <div class="building-card-overlay building-card-overlay--favorite">${favoriteControlHtml(entry, canVote)}</div>
    <div class="building-card-overlay building-card-overlay--upvote">${upvoteControlHtml(entry, canVote)}</div>`;
}

function formatDownloadCount(count) {
  const n = Number(count) || 0;
  if (n === 1) {
    return "1 download";
  }
  return `${n} downloads`;
}

function goldCostHtml(entry, className = "") {
  const gold = Number(entry?.treasuryGoldCoinCost) || 0;
  if (gold <= 0) {
    return "";
  }
  const cls = ["gold-cost", className].filter(Boolean).join(" ");
  return `<span class="${cls}" title="Gold cost" aria-label="Gold cost ${gold}"><img class="gold-cost-icon" src="/assets/Deco_Scrap_Treasure.png" alt="" width="14" height="14" /><span class="gold-cost-value">${escapeHtml(String(gold))}</span></span>`;
}

function requiredModsHtml(entry, compact = false) {
  if (!Array.isArray(entry?.requiredMods)) {
    return `<p class="meta building-requires building-requires--invalid">Dependency metadata unavailable</p>`;
  }
  const mods = entry.requiredMods;
  if (!mods.length) {
    return `<p class="meta building-requires building-requires--none">No external mods required</p>`;
  }
  const labels = mods
    .map((m) => {
      const name = String(m?.name || m?.id || "").trim();
      const id = String(m?.id || "").trim();
      if (!name) {
        return "";
      }
      const idLabel = id && id !== name ? ` <code>${escapeHtml(id)}</code>` : "";
      return `<span class="required-mod">${escapeHtml(name)}${idLabel}</span>`;
    })
    .filter(Boolean);
  if (!labels.length) {
    return `<p class="meta building-requires building-requires--invalid">Invalid dependency metadata</p>`;
  }
  const classes = compact ? "building-requires building-requires--compact" : "building-modal-requires building-requires";
  return `<div class="${classes}"><span class="building-modal-requires-label">Requires mods</span><span class="required-mod-list">${labels.join("")}</span></div>`;
}

function descriptionToggleHtml(entry) {
  const desc = String(entry.description || "").trim();
  if (!desc) {
    return "";
  }
  return `
    <div class="building-details">
      <button type="button" class="description-toggle" aria-expanded="false" onclick="event.stopPropagation(); toggleBuildingDescription(this)">
        <span class="description-toggle-label">Description</span>
        <span class="description-toggle-chevron" aria-hidden="true"></span>
      </button>
      <div class="building-description" hidden>
        <p>${escapeHtml(desc)}</p>
      </div>
    </div>`;
}

function toggleBuildingDescription(buttonEl) {
  if (!buttonEl) {
    return;
  }
  const details = buttonEl.closest(".building-details");
  const desc = details?.querySelector(".building-description");
  if (!details || !desc) {
    return;
  }
  const open = buttonEl.getAttribute("aria-expanded") === "true";
  buttonEl.setAttribute("aria-expanded", open ? "false" : "true");
  details.classList.toggle("building-details--open", !open);
  desc.hidden = open;
}

function renderBuildingCard(entry, options = {}) {
  const { canVote = false, adminDelete = false, showId = false, openDetail = false, adminEdit = false } = options;
  const deleteBtn = adminDelete
    ? `<button
        type="button"
        class="danger admin-delete-btn"
        onclick="event.stopPropagation(); deleteApprovedBuilding(${jsString(entry.id)}, ${jsString(entry.displayName || '')})"
      >Remove permanently</button>`
    : "";
  const editBtn = adminEdit
    ? `<a class="secondary admin-edit-btn" href="/edit.html?id=${encodeURIComponent(entry.id)}&from=admin" onclick="rememberAdminPosition(); event.stopPropagation()">Edit</a>`
    : "";
  const idMeta = showId ? `<p class="meta">${escapeHtml(entry.id)}</p>` : "";
  const cardClass = [
    "card",
    "building-card",
    adminDelete ? "admin-building-card" : "",
    openDetail ? "building-card--clickable" : "",
  ]
    .filter(Boolean)
    .join(" ");
  const shotHint =
    openDetail && entry.screenshotCount > 0
      ? `<p class="meta building-card-shots">${escapeHtml(String(entry.screenshotCount))} screenshot${entry.screenshotCount === 1 ? "" : "s"}</p>`
      : "";
  const openAttrs = openDetail
    ? `role="button" tabindex="0" onclick="openBuildingDetail('${escapeAttr(entry.id)}')" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();openBuildingDetail('${escapeAttr(entry.id)}');}"`
    : "";
  const goldBadge = goldCostHtml(entry, "gold-cost--inline");
  return `
    <article class="${cardClass}" data-building-id="${escapeAttr(entry.id)}" ${openAttrs}>
      <div class="building-card-header">
        ${buildingCardHeaderHtml(entry, canVote)}
      </div>
      <div class="building-card-body">
        <h3>${escapeHtml(entry.displayName)}</h3>
        ${idMeta}
        <p class="meta">by ${escapeHtml(entry.creatorName || "Unknown")}</p>
        <p class="meta building-card-stats">${formatBytes(entry.prefabBytes || 0)} · <span class="download-count">${escapeHtml(formatDownloadCount(entry.downloadCount))}</span> · v${escapeHtml(entry.version)}${goldBadge ? ` · ${goldBadge}` : ""}</p>
        ${requiredModsHtml(entry, true)}
        ${shotHint}
        ${descriptionToggleHtml(entry)}
        ${editBtn}
        ${deleteBtn}
      </div>
    </article>`;
}

async function toggleUpvote(buildingId, buttonEl) {
  if (!buttonEl || buttonEl.disabled) {
    return;
  }
  buttonEl.disabled = true;
  try {
    const res = await fetch(`/api/buildings/${encodeURIComponent(buildingId)}/upvote`, { method: "POST" });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (res.status === 401) {
        window.location.href = "/auth/login";
        return;
      }
      alert(body.error || "Upvote failed");
      return;
    }
    updateCatalogEntryVoteState(buildingId, body.userHasUpvoted, body.upvoteCount ?? 0);
    const active = body.userHasUpvoted;
    buttonEl.classList.toggle("upvote-btn--active", active);
    buttonEl.setAttribute("aria-pressed", active ? "true" : "false");
    const countEl = buttonEl.querySelector(".upvote-count");
    if (countEl) {
      countEl.textContent = String(body.upvoteCount ?? 0);
    }
    buttonEl.setAttribute("aria-label", `Upvote (${body.upvoteCount ?? 0})`);
  } finally {
    buttonEl.disabled = false;
  }
}

function updateCatalogEntryFavoriteState(buildingId, favorited) {
  const entry = catalogEntriesById.get(buildingId);
  if (entry) {
    entry.userHasFavorited = favorited;
  }
  const listEntry = allCatalogEntries.find((e) => e.id === buildingId);
  if (listEntry) {
    listEntry.userHasFavorited = favorited;
  }
}

function updateCatalogEntryVoteState(buildingId, upvoted, upvoteCount) {
  const entry = catalogEntriesById.get(buildingId);
  if (entry) {
    entry.userHasUpvoted = upvoted;
    entry.upvoteCount = upvoteCount;
  }
  const listEntry = allCatalogEntries.find((e) => e.id === buildingId);
  if (listEntry) {
    listEntry.userHasUpvoted = upvoted;
    listEntry.upvoteCount = upvoteCount;
  }
}

function applyFavoriteButtonState(buttonEl, favorited) {
  if (!buttonEl) {
    return;
  }
  buttonEl.classList.toggle("upvote-btn--active", favorited);
  buttonEl.setAttribute("aria-pressed", favorited ? "true" : "false");
  const label = favorited ? "Remove from favorites" : "Add to favorites";
  buttonEl.setAttribute("aria-label", label);
  buttonEl.setAttribute("title", label);
  const starEl = buttonEl.querySelector(".favorite-star");
  if (starEl) {
    starEl.textContent = favorited ? "★" : "☆";
  }
}

async function toggleFavorite(buildingId, buttonEl) {
  if (!buttonEl || buttonEl.disabled) {
    return;
  }
  buttonEl.disabled = true;
  try {
    const res = await fetch(`/api/buildings/${encodeURIComponent(buildingId)}/favorite`, { method: "POST" });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (res.status === 401) {
        window.location.href = "/auth/login";
        return;
      }
      alert(body.error || "Favorite failed");
      return;
    }
    updateCatalogEntryFavoriteState(buildingId, body.userHasFavorited);
    applyFavoriteButtonState(buttonEl, body.userHasFavorited);
    document.querySelectorAll(`.favorite-btn[data-building-id="${CSS.escape(buildingId)}"]`).forEach((el) => {
      if (el !== buttonEl) {
        applyFavoriteButtonState(el, body.userHasFavorited);
      }
    });
    if (getCatalogFilterState().favoritesOnly) {
      applyCatalogFilters({ resetPage: false });
    }
  } finally {
    buttonEl.disabled = false;
  }
}

async function loadCatalog() {
  const el = document.getElementById("catalog");
  const status = document.getElementById("status");
  if (!el) return;
  try {
    const me = await fetchMe();
    const user = me.user;
    renderAccountMenu(user, me.isAdmin);
    const data = await fetchCatalog();
    catalogCanVote = Boolean(user);
    allCatalogEntries = data.entries || [];
    catalogEntriesById = new Map(allCatalogEntries.map((e) => [e.id, e]));
    setupCatalogFilters();
    populateCatalogFilterOptions(allCatalogEntries);
    applyCatalogFilters();
    renderNewestCarousel();
    ensureBuildingDetailModal();
  } catch (e) {
    showStatusError(status, "Could not load catalog.");
  }
}

const NEWEST_CAROUSEL_LIMIT = 8;
const NEWEST_CAROUSEL_AUTOPLAY_MS = 5000;

let newestCarouselBound = false;
let newestCarouselAutoplayId = null;
let newestCarouselRealCount = 0;
/** Logical index into the real (non-cloned) slide list. */
let newestCarouselIndex = 0;
let newestCarouselNormalizing = false;
let newestCarouselAnimating = false;
let newestCarouselAnimTimer = null;

function getNewestCatalogEntries(limit = NEWEST_CAROUSEL_LIMIT) {
  return allCatalogEntries
    .slice()
    .sort((a, b) => {
      const byDate = String(b.approvedAt || "").localeCompare(String(a.approvedAt || ""));
      if (byDate !== 0) {
        return byDate;
      }
      return String(a.displayName || "").localeCompare(String(b.displayName || ""));
    })
    .slice(0, limit);
}

function renderNewestCarouselCard(entry, options = {}) {
  const clone = Boolean(options.clone);
  const goldBadge = goldCostHtml(entry, "gold-cost--inline");
  return `
    <article
      class="card newest-carousel-card building-card--clickable"
      role="listitem"
      data-building-id="${escapeAttr(entry.id)}"
      tabindex="${clone ? "-1" : "0"}"
      ${clone ? 'aria-hidden="true"' : ""}
      onclick="openBuildingDetail('${escapeAttr(entry.id)}')"
      onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();openBuildingDetail('${escapeAttr(entry.id)}');}"
    >
      <div class="newest-carousel-card-media">
        ${buildingCardHeaderHtml(entry, catalogCanVote)}
      </div>
      <div class="newest-carousel-card-body">
        <h4>${escapeHtml(entry.displayName)}</h4>
        <p class="meta">by ${escapeHtml(entry.creatorName || "Unknown")}</p>
        <p class="meta newest-carousel-card-stats">${formatBytes(entry.prefabBytes || 0)}${goldBadge ? ` · ${goldBadge}` : ""}</p>
      </div>
    </article>`;
}

function buildNewestCarouselHtml(entries) {
  if (entries.length < 2) {
    return entries.map((entry) => renderNewestCarouselCard(entry)).join("");
  }
  const parts = [];
  for (let copy = 0; copy < 3; copy++) {
    const clone = copy !== 1;
    for (const entry of entries) {
      parts.push(renderNewestCarouselCard(entry, { clone }));
    }
  }
  return parts.join("");
}

function getNewestCarouselCards(track) {
  return Array.from(track?.querySelectorAll(".newest-carousel-card") || []);
}

function setNewestCarouselActiveCard(absoluteIndex) {
  const track = document.getElementById("newestCarouselTrack");
  if (!track) {
    return;
  }
  const cards = getNewestCarouselCards(track);
  cards.forEach((card, i) => {
    card.classList.toggle("is-active", i === absoluteIndex);
  });
}

function middleAbsoluteIndex(realIndex) {
  return newestCarouselRealCount + realIndex;
}

function syncNewestCarouselIndexFromScroll() {
  const track = document.getElementById("newestCarouselTrack");
  if (!track || newestCarouselRealCount < 1) {
    return;
  }
  const cards = getNewestCarouselCards(track);
  if (!cards.length) {
    return;
  }
  const trackRect = track.getBoundingClientRect();
  const centerX = trackRect.left + trackRect.width / 2;
  let bestIndex = 0;
  let bestDist = Infinity;
  cards.forEach((card, i) => {
    const rect = card.getBoundingClientRect();
    const dist = Math.abs(rect.left + rect.width / 2 - centerX);
    if (dist < bestDist) {
      bestDist = dist;
      bestIndex = i;
    }
  });
  newestCarouselIndex = bestIndex % newestCarouselRealCount;
  setNewestCarouselActiveCard(bestIndex);
}

function jumpNewestCarouselToAbsolute(absoluteIndex) {
  const track = document.getElementById("newestCarouselTrack");
  if (!track) {
    return;
  }
  const cards = getNewestCarouselCards(track);
  const card = cards[absoluteIndex];
  if (!card) {
    return;
  }
  newestCarouselNormalizing = true;
  const trackRect = track.getBoundingClientRect();
  const cardRect = card.getBoundingClientRect();
  const delta = cardRect.left + cardRect.width / 2 - (trackRect.left + trackRect.width / 2);
  track.scrollLeft += delta;
  newestCarouselNormalizing = false;
  setNewestCarouselActiveCard(absoluteIndex);
}

function normalizeNewestCarouselToMiddle() {
  const track = document.getElementById("newestCarouselTrack");
  if (!track || newestCarouselRealCount < 2 || newestCarouselAnimating) {
    return;
  }
  jumpNewestCarouselToAbsolute(middleAbsoluteIndex(newestCarouselIndex));
}

function finishNewestCarouselAnimation() {
  if (newestCarouselAnimTimer != null) {
    clearTimeout(newestCarouselAnimTimer);
    newestCarouselAnimTimer = null;
  }
  newestCarouselAnimating = false;
  normalizeNewestCarouselToMiddle();
}

function scrollNewestCarouselToAbsolute(absoluteIndex, behavior = "smooth") {
  const track = document.getElementById("newestCarouselTrack");
  if (!track) {
    return;
  }
  const cards = getNewestCarouselCards(track);
  const card = cards[absoluteIndex];
  if (!card) {
    return;
  }

  setNewestCarouselActiveCard(absoluteIndex);

  const trackRect = track.getBoundingClientRect();
  const cardRect = card.getBoundingClientRect();
  const delta = cardRect.left + cardRect.width / 2 - (trackRect.left + trackRect.width / 2);

  if (behavior === "smooth") {
    newestCarouselAnimating = true;
    if (newestCarouselAnimTimer != null) {
      clearTimeout(newestCarouselAnimTimer);
    }
    newestCarouselAnimTimer = setTimeout(finishNewestCarouselAnimation, 450);
    track.scrollBy({ left: delta, behavior: "smooth" });
  } else {
    newestCarouselAnimating = false;
    track.scrollBy({ left: delta, behavior: "auto" });
    normalizeNewestCarouselToMiddle();
  }
}

function scrollNewestCarousel(direction) {
  const track = document.getElementById("newestCarouselTrack");
  if (!track) {
    return;
  }
  const n = newestCarouselRealCount;
  const cards = getNewestCarouselCards(track);
  if (!cards.length || n < 1) {
    return;
  }
  if (newestCarouselAnimating) {
    return;
  }

  if (n < 2) {
    newestCarouselIndex = 0;
    scrollNewestCarouselToAbsolute(0, "smooth");
    return;
  }

  const currentReal = ((newestCarouselIndex % n) + n) % n;
  const nextReal = (currentReal + direction + n) % n;
  let absoluteTarget = middleAbsoluteIndex(nextReal);

  // Animate through a neighbor clone set when wrapping so peeks stay filled.
  if (direction > 0 && nextReal < currentReal) {
    absoluteTarget = 2 * n + nextReal;
  } else if (direction < 0 && nextReal > currentReal) {
    absoluteTarget = nextReal;
  }

  newestCarouselIndex = nextReal;
  scrollNewestCarouselToAbsolute(absoluteTarget, "smooth");
}

function stopNewestCarouselAutoplay() {
  if (newestCarouselAutoplayId != null) {
    clearInterval(newestCarouselAutoplayId);
    newestCarouselAutoplayId = null;
  }
}

function startNewestCarouselAutoplay() {
  stopNewestCarouselAutoplay();
  const track = document.getElementById("newestCarouselTrack");
  const section = document.getElementById("newestCarouselSection");
  if (!track || !section || section.hidden || newestCarouselRealCount < 2) {
    return;
  }
  newestCarouselAutoplayId = setInterval(() => {
    if (document.hidden || newestCarouselAnimating) {
      return;
    }
    scrollNewestCarousel(1);
  }, NEWEST_CAROUSEL_AUTOPLAY_MS);
}

function setupNewestCarouselControls() {
  if (newestCarouselBound) {
    return;
  }
  newestCarouselBound = true;
  const section = document.getElementById("newestCarouselSection");
  const carousel = document.getElementById("newestCarousel");
  const track = document.getElementById("newestCarouselTrack");
  const prev = document.getElementById("newestCarouselPrev");
  const next = document.getElementById("newestCarouselNext");
  if (!section || !carousel || !track) {
    return;
  }
  prev?.addEventListener("click", (event) => {
    event.preventDefault();
    scrollNewestCarousel(-1);
    startNewestCarouselAutoplay();
  });
  next?.addEventListener("click", (event) => {
    event.preventDefault();
    scrollNewestCarousel(1);
    startNewestCarouselAutoplay();
  });
  track.addEventListener(
    "scroll",
    () => {
      if (newestCarouselNormalizing || newestCarouselAnimating) {
        return;
      }
      window.requestAnimationFrame(syncNewestCarouselIndexFromScroll);
    },
    { passive: true }
  );
  track.addEventListener("scrollend", () => {
    if (newestCarouselAnimating) {
      finishNewestCarouselAnimation();
      return;
    }
    if (!newestCarouselNormalizing) {
      syncNewestCarouselIndexFromScroll();
      normalizeNewestCarouselToMiddle();
    }
  });
  window.addEventListener("resize", () => {
    newestCarouselAnimating = false;
    normalizeNewestCarouselToMiddle();
  });
  const pause = () => stopNewestCarouselAutoplay();
  const resume = () => startNewestCarouselAutoplay();
  carousel.addEventListener("mouseenter", pause);
  carousel.addEventListener("mouseleave", resume);
  carousel.addEventListener("focusin", pause);
  carousel.addEventListener("focusout", (event) => {
    if (!carousel.contains(event.relatedTarget)) {
      resume();
    }
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      pause();
    } else if (!section.hidden) {
      resume();
    }
  });
}

function renderNewestCarousel() {
  const section = document.getElementById("newestCarouselSection");
  const track = document.getElementById("newestCarouselTrack");
  if (!section || !track) {
    return;
  }
  const newest = getNewestCatalogEntries();
  if (!newest.length) {
    newestCarouselRealCount = 0;
    newestCarouselIndex = 0;
    section.hidden = true;
    track.innerHTML = "";
    stopNewestCarouselAutoplay();
    return;
  }
  section.hidden = false;
  newestCarouselRealCount = newest.length;
  newestCarouselIndex = 0;
  newestCarouselAnimating = false;
  track.innerHTML = buildNewestCarouselHtml(newest);
  setupNewestCarouselControls();
  window.requestAnimationFrame(() => {
    if (newestCarouselRealCount >= 2) {
      jumpNewestCarouselToAbsolute(middleAbsoluteIndex(0));
    } else {
      jumpNewestCarouselToAbsolute(0);
    }
  });
  startNewestCarouselAutoplay();
}

const CATALOG_PAGE_SIZE = 12;

let allCatalogEntries = [];
let filteredCatalogEntries = [];
let catalogCanVote = false;
let catalogFiltersBound = false;
let catalogPage = 1;

function setupCatalogFilters() {
  const toolbar = document.getElementById("catalogToolbar");
  if (!toolbar) {
    return;
  }
  toolbar.hidden = false;
  if (catalogFiltersBound) {
    return;
  }
  catalogFiltersBound = true;
  const search = document.getElementById("catalogSearch");
  const author = document.getElementById("catalogAuthorFilter");
  const style = document.getElementById("catalogStyleFilter");
  const type = document.getElementById("catalogTypeFilter");
  const sort = document.getElementById("catalogSort");
  const favoritesOnly = document.getElementById("catalogFavoritesOnly");
  const clear = document.getElementById("catalogClearFilters");
  const onFilterChange = () => applyCatalogFilters({ resetPage: true });
  search?.addEventListener("input", onFilterChange);
  author?.addEventListener("change", onFilterChange);
  style?.addEventListener("change", onFilterChange);
  type?.addEventListener("change", onFilterChange);
  sort?.addEventListener("change", onFilterChange);
  favoritesOnly?.addEventListener("change", onFilterChange);
  clear?.addEventListener("click", () => {
    if (search) search.value = "";
    if (author) author.value = "";
    if (style) style.value = "";
    if (type) type.value = "";
    if (sort) sort.value = "upvotes";
    if (favoritesOnly) favoritesOnly.checked = false;
    applyCatalogFilters({ resetPage: true });
  });
  const urlFavorites = new URLSearchParams(window.location.search).get("favorites") === "1";
  if (favoritesOnly && urlFavorites) {
    favoritesOnly.checked = true;
  }
}

function uniqueSortedValues(values) {
  return [...new Set(values.filter(Boolean))].sort((a, b) =>
    a.localeCompare(b, undefined, { sensitivity: "base" })
  );
}

function fillSelectOptions(selectEl, values, allLabel) {
  if (!selectEl) {
    return;
  }
  const current = selectEl.value;
  selectEl.innerHTML =
    `<option value="">${escapeHtml(allLabel)}</option>` +
    values.map((v) => `<option value="${escapeAttr(v)}">${escapeHtml(v)}</option>`).join("");
  if (values.includes(current)) {
    selectEl.value = current;
  }
}

function populateCatalogFilterOptions(entries) {
  const authors = uniqueSortedValues(entries.map((e) => e.creatorName || "Unknown"));
  const styles = uniqueSortedValues(entries.map((e) => e.styleId || "misc"));
  const tags = uniqueSortedValues(entries.flatMap((e) => (Array.isArray(e.tags) ? e.tags : [])));
  fillSelectOptions(document.getElementById("catalogAuthorFilter"), authors, "All authors");
  fillSelectOptions(document.getElementById("catalogStyleFilter"), styles, "All styles");
  fillSelectOptions(document.getElementById("catalogTypeFilter"), tags, "All types");
}

function getCatalogFilterState() {
  return {
    query: String(document.getElementById("catalogSearch")?.value || "")
      .trim()
      .toLowerCase(),
    author: String(document.getElementById("catalogAuthorFilter")?.value || ""),
    style: String(document.getElementById("catalogStyleFilter")?.value || ""),
    type: String(document.getElementById("catalogTypeFilter")?.value || ""),
    favoritesOnly: Boolean(document.getElementById("catalogFavoritesOnly")?.checked),
  };
}

function getCatalogSortMode() {
  const value = String(document.getElementById("catalogSort")?.value || "upvotes");
  const allowed = new Set(["upvotes", "downloads", "newest", "name-asc", "name-desc"]);
  return allowed.has(value) ? value : "upvotes";
}

function catalogEntryDisplayName(entry) {
  return String(entry?.displayName || "");
}

function compareCatalogEntryNames(a, b) {
  return catalogEntryDisplayName(a).localeCompare(catalogEntryDisplayName(b), undefined, {
    sensitivity: "base",
  });
}

function sortCatalogEntries(entries, sortMode) {
  const mode = sortMode || "upvotes";
  return [...entries].sort((a, b) => {
    if (mode === "downloads") {
      const byDownloads = (b.downloadCount || 0) - (a.downloadCount || 0);
      if (byDownloads !== 0) {
        return byDownloads;
      }
      return compareCatalogEntryNames(a, b);
    }
    if (mode === "newest") {
      const byDate = String(b.approvedAt || "").localeCompare(String(a.approvedAt || ""));
      if (byDate !== 0) {
        return byDate;
      }
      return compareCatalogEntryNames(a, b);
    }
    if (mode === "name-asc") {
      return compareCatalogEntryNames(a, b);
    }
    if (mode === "name-desc") {
      return compareCatalogEntryNames(b, a);
    }
    const byVotes = (b.upvoteCount || 0) - (a.upvoteCount || 0);
    if (byVotes !== 0) {
      return byVotes;
    }
    const byName = compareCatalogEntryNames(a, b);
    if (byName !== 0) {
      return byName;
    }
    return String(a.approvedAt || "").localeCompare(String(b.approvedAt || ""));
  });
}

function entryMatchesCatalogFilters(entry, filters) {
  if (filters.favoritesOnly) {
    if (!catalogCanVote) {
      return false;
    }
    if (!entry.userHasFavorited) {
      return false;
    }
  }
  if (filters.author && (entry.creatorName || "Unknown") !== filters.author) {
    return false;
  }
  if (filters.style && (entry.styleId || "misc") !== filters.style) {
    return false;
  }
  const tags = Array.isArray(entry.tags) ? entry.tags : [];
  if (filters.type && !tags.includes(filters.type)) {
    return false;
  }
  if (!filters.query) {
    return true;
  }
  const haystack = [entry.displayName || "", entry.creatorName || "", ...tags]
    .join(" ")
    .toLowerCase();
  return haystack.includes(filters.query);
}

function updateCatalogResultCount(rangeStart, rangeEnd, filteredTotal, allTotal) {
  const el = document.getElementById("catalogResultCount");
  if (!el) {
    return;
  }
  if (!filteredTotal) {
    el.textContent = allTotal ? `0 of ${allTotal} builds` : "0 builds";
    return;
  }
  const range =
    rangeStart === rangeEnd ? `${rangeStart}` : `${rangeStart}–${rangeEnd}`;
  if (filteredTotal === allTotal) {
    el.textContent =
      filteredTotal === 1 ? "1 build" : `Showing ${range} of ${filteredTotal} builds`;
  } else {
    el.textContent = `Showing ${range} of ${filteredTotal} matches (${allTotal} total)`;
  }
}

function catalogPageWindow(current, totalPages) {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }
  const pages = new Set([1, totalPages, current]);
  for (let i = current - 1; i <= current + 1; i++) {
    if (i >= 1 && i <= totalPages) {
      pages.add(i);
    }
  }
  if (current <= 3) {
    pages.add(2);
    pages.add(3);
    pages.add(4);
  }
  if (current >= totalPages - 2) {
    pages.add(totalPages - 1);
    pages.add(totalPages - 2);
    pages.add(totalPages - 3);
  }
  return [...pages].sort((a, b) => a - b);
}

function renderCatalogPagination(totalPages) {
  const nav = document.getElementById("catalogPagination");
  if (!nav) {
    return;
  }
  if (totalPages <= 1 || !filteredCatalogEntries.length) {
    nav.hidden = true;
    nav.innerHTML = "";
    return;
  }
  nav.hidden = false;
  const windowPages = catalogPageWindow(catalogPage, totalPages);
  let html = `<button type="button" class="secondary" ${
    catalogPage <= 1 ? "disabled" : ""
  } onclick="goToCatalogPage(${catalogPage - 1})" aria-label="Previous page">Prev</button>`;
  let prev = 0;
  for (const page of windowPages) {
    if (prev && page - prev > 1) {
      html += `<span class="catalog-pagination-ellipsis" aria-hidden="true">…</span>`;
    }
    const current = page === catalogPage;
    html += `<button type="button" class="secondary" ${
      current ? 'aria-current="page"' : ""
    } onclick="goToCatalogPage(${page})" aria-label="Page ${page}">${page}</button>`;
    prev = page;
  }
  html += `<button type="button" class="secondary" ${
    catalogPage >= totalPages ? "disabled" : ""
  } onclick="goToCatalogPage(${catalogPage + 1})" aria-label="Next page">Next</button>`;
  nav.innerHTML = html;
}

function goToCatalogPage(page) {
  const totalPages = Math.max(1, Math.ceil(filteredCatalogEntries.length / CATALOG_PAGE_SIZE));
  const next = Math.min(Math.max(1, Number(page) || 1), totalPages);
  if (next === catalogPage && document.getElementById("catalog")?.children.length) {
    return;
  }
  catalogPage = next;
  renderCatalogPage();
  document.getElementById("catalogToolbar")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderCatalogPage() {
  const el = document.getElementById("catalog");
  if (!el) {
    return;
  }
  const allTotal = allCatalogEntries.length;
  const filteredTotal = filteredCatalogEntries.length;
  if (!allTotal) {
    el.innerHTML = emptyStateHtml("No approved buildings yet.");
    updateCatalogResultCount(0, 0, 0, 0);
    renderCatalogPagination(0);
    return;
  }
  if (!filteredTotal) {
    const filters = getCatalogFilterState();
    let message = "No builds match your search or filters.";
    if (filters.favoritesOnly && !catalogCanVote) {
      message = "Sign in to view your favorite builds.";
    } else if (filters.favoritesOnly) {
      message = "You have not favorited any builds yet. Star a build to save it here.";
    }
    el.innerHTML = emptyStateHtml(message);
    updateCatalogResultCount(0, 0, 0, allTotal);
    renderCatalogPagination(0);
    return;
  }
  const totalPages = Math.max(1, Math.ceil(filteredTotal / CATALOG_PAGE_SIZE));
  if (catalogPage > totalPages) {
    catalogPage = totalPages;
  }
  if (catalogPage < 1) {
    catalogPage = 1;
  }
  const start = (catalogPage - 1) * CATALOG_PAGE_SIZE;
  const pageEntries = filteredCatalogEntries.slice(start, start + CATALOG_PAGE_SIZE);
  updateCatalogResultCount(start + 1, start + pageEntries.length, filteredTotal, allTotal);
  el.innerHTML = pageEntries
    .map((e) => renderBuildingCard(e, { canVote: catalogCanVote, openDetail: true }))
    .join("");
  renderCatalogPagination(totalPages);
}

function applyCatalogFilters({ resetPage = true } = {}) {
  const el = document.getElementById("catalog");
  if (!el) {
    return;
  }
  const filters = getCatalogFilterState();
  const url = new URL(window.location.href);
  if (filters.favoritesOnly) {
    url.searchParams.set("favorites", "1");
  } else {
    url.searchParams.delete("favorites");
  }
  history.replaceState(null, "", url);
  filteredCatalogEntries = sortCatalogEntries(
    allCatalogEntries.filter((e) => entryMatchesCatalogFilters(e, filters)),
    getCatalogSortMode()
  );
  if (resetPage) {
    catalogPage = 1;
  }
  renderCatalogPage();
}

let mySubmissionsCache = [];
let mySubmissionsFilters = { query: "", status: "all", sort: "newest" };
let mySubmissionsFiltersBound = false;

function submissionKind(item) {
  if (item.kind === "approved" || item.status === "approved") return "approved";
  if (item.kind === "rejected" || item.status === "rejected") return "rejected";
  return "pending";
}

function submissionDateValue(item) {
  return item.submittedAt || item.approvedAt || item.rejectedAt || "";
}

function formatRelativeDate(iso) {
  if (!iso) return "";
  const then = new Date(iso);
  if (Number.isNaN(then.getTime())) return "";
  const diffMs = Date.now() - then.getTime();
  const sec = Math.round(diffMs / 1000);
  if (sec < 60) return "just now";
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 48) return `${hr}h ago`;
  const day = Math.round(hr / 24);
  if (day < 30) return `${day}d ago`;
  return then.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function submissionDisplayName(item) {
  return String(item?.displayName || "Untitled");
}

function submissionMatchesQuery(item, query) {
  if (!query) return true;
  const haystack = [
    item.displayName,
    item.id,
    item.submissionId,
    item.proposedId,
    item.reason,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(query);
}

function sortMySubmissions(entries, sort) {
  return [...entries].sort((a, b) => {
    if (sort === "name-asc") {
      return submissionDisplayName(a).localeCompare(submissionDisplayName(b), undefined, {
        sensitivity: "base",
      });
    }
    if (sort === "name-desc") {
      return submissionDisplayName(b).localeCompare(submissionDisplayName(a), undefined, {
        sensitivity: "base",
      });
    }
    const byDate = String(submissionDateValue(b)).localeCompare(String(submissionDateValue(a)));
    if (byDate !== 0) return byDate;
    return submissionDisplayName(a).localeCompare(submissionDisplayName(b), undefined, {
      sensitivity: "base",
    });
  });
}

function getFilteredMySubmissions() {
  const query = String(mySubmissionsFilters.query || "")
    .trim()
    .toLowerCase();
  const status = mySubmissionsFilters.status || "all";
  const filtered = mySubmissionsCache.filter((item) => {
    if (status !== "all" && submissionKind(item) !== status) return false;
    return submissionMatchesQuery(item, query);
  });
  return sortMySubmissions(filtered, mySubmissionsFilters.sort || "newest");
}

function countMySubmissionsByStatus() {
  const counts = { all: mySubmissionsCache.length, pending: 0, approved: 0, rejected: 0 };
  for (const item of mySubmissionsCache) {
    counts[submissionKind(item)] += 1;
  }
  return counts;
}

function updateSubmissionsChipCounts() {
  const counts = countMySubmissionsByStatus();
  document.querySelectorAll("[data-count-for]").forEach((el) => {
    const key = el.getAttribute("data-count-for");
    el.textContent = String(counts[key] ?? 0);
  });
  document.querySelectorAll(".submissions-chip").forEach((btn) => {
    const status = btn.getAttribute("data-status");
    const active = status === mySubmissionsFilters.status;
    btn.classList.toggle("is-active", active);
    btn.setAttribute("aria-pressed", active ? "true" : "false");
  });
}

function updateSubmissionsResultCount(visibleCount) {
  const el = document.getElementById("submissionsResultCount");
  if (!el) return;
  const total = mySubmissionsCache.length;
  if (!total) {
    el.textContent = "";
    return;
  }
  if (visibleCount === total && mySubmissionsFilters.status === "all" && !mySubmissionsFilters.query.trim()) {
    el.textContent = `${total} submission${total === 1 ? "" : "s"}`;
    return;
  }
  el.textContent = `Showing ${visibleCount} of ${total}`;
}

function renderMySubmissionSection(title, items) {
  if (!items.length) return "";
  return `
    <section class="submissions-group">
      <h3 class="submissions-group-title">${escapeHtml(title)} <span class="submissions-group-count">${items.length}</span></h3>
      <div class="submissions-group-list">
        ${items.map(renderMySubmissionItem).join("")}
      </div>
    </section>`;
}

function renderFilteredMySubmissions() {
  const list = document.getElementById("mySubmissions");
  if (!list) return;

  updateSubmissionsChipCounts();

  if (!mySubmissionsCache.length) {
    list.innerHTML = emptyStateHtml("No submissions yet.");
    updateSubmissionsResultCount(0);
    return;
  }

  const filtered = getFilteredMySubmissions();
  updateSubmissionsResultCount(filtered.length);

  if (!filtered.length) {
    list.innerHTML = emptyStateHtml("No submissions match your filters.");
    return;
  }

  if (mySubmissionsFilters.status === "all") {
    const pending = filtered.filter((i) => submissionKind(i) === "pending");
    const approved = filtered.filter((i) => submissionKind(i) === "approved");
    const rejected = filtered.filter((i) => submissionKind(i) === "rejected");
    list.innerHTML = [
      renderMySubmissionSection("Pending review", pending),
      renderMySubmissionSection("Published", approved),
      renderMySubmissionSection("Rejected", rejected),
    ].join("");
    return;
  }

  list.innerHTML = `<div class="submissions-group-list">${filtered.map(renderMySubmissionItem).join("")}</div>`;
}

function setupMySubmissionsFilters() {
  if (mySubmissionsFiltersBound) return;
  const toolbar = document.getElementById("submissionsToolbar");
  const search = document.getElementById("submissionsSearch");
  const sort = document.getElementById("submissionsSort");
  const clear = document.getElementById("submissionsClearFilters");
  const chips = document.getElementById("submissionsStatusChips");
  if (!toolbar || !search || !sort) return;

  mySubmissionsFiltersBound = true;
  toolbar.hidden = false;

  search.addEventListener("input", () => {
    mySubmissionsFilters.query = search.value || "";
    renderFilteredMySubmissions();
  });
  sort.addEventListener("change", () => {
    mySubmissionsFilters.sort = sort.value || "newest";
    renderFilteredMySubmissions();
  });
  chips?.addEventListener("click", (event) => {
    const btn = event.target.closest(".submissions-chip");
    if (!btn) return;
    mySubmissionsFilters.status = btn.getAttribute("data-status") || "all";
    renderFilteredMySubmissions();
  });
  clear?.addEventListener("click", () => {
    mySubmissionsFilters = { query: "", status: "all", sort: "newest" };
    search.value = "";
    sort.value = "newest";
    renderFilteredMySubmissions();
  });
}

async function loadSubmissions() {
  const list = document.getElementById("mySubmissions");
  const me = await fetch("/api/me").then((r) => r.json());
  if (!me.user) {
    window.location.href = "/auth/login";
    return;
  }
  renderAccountMenu(me.user, me.isAdmin);

  if (!list) return;
  try {
    const res = await fetch("/api/my-submissions");
    if (res.status === 401) {
      window.location.href = "/auth/login";
      return;
    }
    if (!res.ok) {
      list.innerHTML = emptyStateHtml("Could not load your submissions.");
      return;
    }
    const data = await res.json();
    mySubmissionsCache = data.submissions || [];
    setupMySubmissionsFilters();
    const toolbar = document.getElementById("submissionsToolbar");
    if (toolbar) {
      toolbar.hidden = mySubmissionsCache.length === 0;
    }
    renderFilteredMySubmissions();
  } catch {
    list.innerHTML = emptyStateHtml("Could not load your submissions.");
  }
}

async function loadAccountPage() {
  const root = document.getElementById("accountDetails");
  const me = await fetch("/api/me").then((r) => r.json());
  if (!me.user) {
    window.location.href = "/auth/login";
    return;
  }
  renderAccountMenu(me.user, me.isAdmin);
  if (!root) {
    return;
  }
  const name = userDisplayName(me.user);
  const uuid = me.user.profile?.uuid || "";
  root.innerHTML = `
    <h2>Account</h2>
    <div class="account-field">
      <span class="account-field-label">Username</span>
      <div class="account-field-row"><code>${escapeHtml(name)}</code></div>
    </div>
    <div class="account-field">
      <span class="account-field-label">Profile UUID</span>
      <div class="account-field-row">
        <code id="accountProfileUuid">${escapeHtml(uuid || "—")}</code>
        <button type="button" class="secondary" ${uuid ? "" : "disabled "}onclick="copyAccountUuid()">Copy</button>
      </div>
    </div>
    <p class="meta">Submissions are matched by this profile UUID or your Hytale username.</p>`;
}

async function copyAccountUuid() {
  const el = document.getElementById("accountProfileUuid");
  const uuid = el?.textContent?.trim();
  if (!uuid || uuid === "—") {
    return;
  }
  try {
    await navigator.clipboard.writeText(uuid);
    alert("Profile UUID copied.");
  } catch {
    alert("Could not copy. Select the UUID and copy manually.");
  }
}

function submissionStatusLabel(item) {
  if (item.kind === "approved" || item.status === "approved") return "Published";
  if (item.kind === "rejected" || item.status === "rejected") return "Rejected";
  return "Pending review";
}

function submissionStatusClass(item) {
  if (item.kind === "approved" || item.status === "approved") return "submission-status--published";
  if (item.kind === "rejected" || item.status === "rejected") return "submission-status--rejected";
  return "";
}

const MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;
const MAX_SCREENSHOTS_PER_OWNER = 6;
const SCREENSHOT_MAX_SIZE_LABEL = "5 MB";
const ALLOWED_SCREENSHOT_TYPES = ["image/jpeg", "image/png", "image/webp"];

function screenshotStatusLabel(status) {
  if (status === "approved") return "Approved";
  if (status === "rejected") return "Rejected";
  return "Pending review";
}

function renderOwnerScreenshots(item, options = {}) {
  if (item.kind !== "pending" && item.kind !== "approved") {
    return "";
  }
  const asAdmin = Boolean(options.asAdmin);
  const compact = Boolean(options.compact);
  const reloadFn = options.reloadFn || "loadSubmissions";
  const shots = Array.isArray(item.screenshots) ? item.screenshots : [];
  const ownerKind = item.kind;
  const ownerId = item.kind === "approved" ? item.id : item.submissionId;
  const coverId = item.coverScreenshotId || "";
  const atLimit = shots.length >= MAX_SCREENSHOTS_PER_OWNER;
  const thumbs = shots.length
    ? shots
        .map((shot, index) => {
          const thumbSrc = shot.cardUrl || shot.url;
          const img = thumbSrc
            ? `<img src="${escapeAttr(thumbSrc)}" alt="" ${index === 0 && !compact ? `id="editGalleryMainPreview"` : ""} />`
            : `<div class="screenshot-thumb-placeholder" aria-hidden="true"></div>`;
          const isCover = ownerKind === "approved" && shot.status === "approved" && shot.screenshotId === coverId;
          const coverBtn =
            ownerKind === "approved" && shot.status === "approved"
              ? isCover
                ? `<button type="button" class="secondary screenshot-cover-btn screenshot-cover-btn--active" onclick="setBuildingCover('${escapeAttr(ownerId)}', '', ${asAdmin}, '${escapeAttr(reloadFn)}')" title="Clear card image">Card image</button>`
                : `<button type="button" class="secondary screenshot-cover-btn" onclick="setBuildingCover('${escapeAttr(ownerId)}', '${escapeAttr(shot.screenshotId)}', ${asAdmin}, '${escapeAttr(reloadFn)}')" title="Use as marketplace card image">Set card</button>`
              : "";
          return `
      <div class="screenshot-thumb${isCover ? " screenshot-thumb--cover" : ""}${!compact ? " screenshot-thumb--large" : ""}" data-status="${escapeAttr(shot.status || "pending")}">
        ${img}
        <span class="screenshot-thumb-status">${escapeHtml(screenshotStatusLabel(shot.status))}${isCover ? " · Card" : ""}</span>
        ${coverBtn}
        <button type="button" class="screenshot-thumb-delete" title="Remove screenshot" aria-label="Remove screenshot" onclick="deleteMyScreenshot('${escapeAttr(shot.screenshotId)}', ${asAdmin}, '${escapeAttr(reloadFn)}')">×</button>
      </div>`;
        })
        .join("")
    : `<p class="meta screenshot-empty">No screenshots yet.</p>`;

  const uploadDisabled = atLimit ? "disabled" : "";
  const approvalNote = asAdmin
    ? " Admin uploads are approved immediately."
    : " Screenshots need admin approval before they appear publicly.";
  return `
    <div class="screenshot-manager${compact ? "" : " screenshot-manager--edit"}">
      <div class="screenshot-strip${compact ? "" : " screenshot-strip--edit"}">${thumbs}</div>
      <div class="screenshot-upload-row">
        <label class="screenshot-upload-label">
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
            ${uploadDisabled}
            onchange="uploadMyScreenshot('${escapeAttr(ownerKind)}', '${escapeAttr(ownerId)}', this, ${asAdmin}, '${escapeAttr(reloadFn)}')"
          />
          <span class="screenshot-upload-btn">${atLimit ? "Screenshot limit reached" : "Add screenshot"}</span>
        </label>
        <p class="meta">JPEG, PNG, or WebP · max ${SCREENSHOT_MAX_SIZE_LABEL} · up to ${MAX_SCREENSHOTS_PER_OWNER} per build.${approvalNote}${
          ownerKind === "approved"
            ? " Use an approved screenshot as the marketplace card image."
            : ""
        }</p>
      </div>
    </div>`;
}

function renderMySubmissionItem(item) {
  const kind = submissionKind(item);
  const status = submissionStatusLabel(item);
  const statusClass = submissionStatusClass(item);
  const title = escapeHtml(item.displayName || "Untitled");
  const cardImg = buildingCardImageUrl(item);
  const icon = cardImg
    ? `<img class="submission-card-thumb" src="${escapeAttr(cardImg)}" alt="" onerror="this.outerHTML='<div class=\\'submission-card-thumb submission-card-thumb--placeholder\\' aria-hidden=\\'true\\'></div>';" />`
    : `<div class="submission-card-thumb submission-card-thumb--placeholder" aria-hidden="true"></div>`;
  const goldBadge = goldCostHtml(item, "gold-cost--inline");
  const dateLabel = formatRelativeDate(submissionDateValue(item));
  const dateHtml = dateLabel ? `<span class="submission-card-date">${escapeHtml(dateLabel)}</span>` : "";

  let meta = "";
  if (kind === "approved") {
    meta = `<p class="meta submission-card-meta">${escapeHtml(item.id)} · ${formatBytes(item.prefabBytes || 0)} · ${escapeHtml(formatDownloadCount(item.downloadCount))} · v${escapeHtml(item.version || "1")}${goldBadge ? ` · ${goldBadge}` : ""}</p>`;
  } else if (kind === "rejected") {
    const reason = String(item.reason || "").trim();
    meta = `
      <p class="meta submission-card-meta">${escapeHtml(item.submissionId)}${item.proposedId ? ` · proposed id ${escapeHtml(item.proposedId)}` : ""}</p>
      ${reason ? `<p class="submission-card-reason">${escapeHtml(reason)}</p>` : `<p class="meta">No rejection reason provided.</p>`}`;
  } else {
    meta = `<p class="meta submission-card-meta">${escapeHtml(item.submissionId)}${item.proposedId ? ` · proposed id ${escapeHtml(item.proposedId)}` : ""}</p>`;
  }

  let actions = "";
  if (kind === "pending") {
    actions = `<button type="button" class="secondary" onclick="event.stopPropagation(); withdrawMySubmission(${jsString(item.submissionId)}, ${jsString(item.displayName || "")})">Withdraw</button>`;
  } else if (kind === "approved") {
    const editHref = `/edit.html?id=${encodeURIComponent(item.id)}&from=submissions`;
    actions = `
      <a class="secondary" href="${escapeAttr(editHref)}" onclick="event.stopPropagation()">Edit</a>
      <button type="button" class="danger" onclick="event.stopPropagation(); removeMyBuilding(${jsString(item.id)}, ${jsString(item.displayName || "")})">Remove from marketplace</button>`;
  } else if (kind === "rejected") {
    actions = `<button type="button" class="secondary" onclick="event.stopPropagation(); dismissMySubmission(${jsString(item.submissionId)}, ${jsString(item.displayName || "")})">Dismiss</button>`;
  }

  const isPublished = kind === "approved";
  const editPath = isPublished
    ? `/edit.html?id=${encodeURIComponent(item.id)}&from=submissions`
    : "";
  const clickAttrs = isPublished
    ? `role="link" tabindex="0" onclick="window.location.href='${editPath}'" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();window.location.href='${editPath}';}"`
    : "";

  return `
    <article class="submission-card${isPublished ? " submission-card--clickable" : ""}" ${clickAttrs}>
      ${icon}
      <div class="submission-card-body">
        <div class="submission-card-header">
          <h4 class="submission-card-title">${title}</h4>
          <span class="submission-status submission-status-badge ${statusClass}">${escapeHtml(status)}</span>
        </div>
        ${dateHtml ? `<p class="meta submission-card-date-row">${dateHtml}</p>` : ""}
        ${meta}
        ${kind === "pending" ? renderOwnerScreenshots(item, { compact: true }) : ""}
        <div class="submission-card-actions" onclick="event.stopPropagation()">${actions}</div>
      </div>
    </article>`;
}

async function reloadAfterScreenshotAction(reloadFn) {
  if (reloadFn === "loadEditPage" && typeof loadEditPage === "function") {
    await loadEditPage();
    return;
  }
  if (reloadFn === "loadAdminPage" && typeof loadAdminPage === "function") {
    await loadAdminPage();
    return;
  }
  await loadSubmissions();
}

async function uploadMyScreenshot(ownerKind, ownerId, inputEl, asAdmin = false, reloadFn = "loadSubmissions") {
  const file = inputEl?.files?.[0];
  if (!file) {
    return;
  }
  if (!ALLOWED_SCREENSHOT_TYPES.includes(file.type)) {
    alert("Screenshots must be JPEG, PNG, or WebP.");
    inputEl.value = "";
    return;
  }
  if (file.size > MAX_SCREENSHOT_BYTES) {
    alert(`Screenshot too large (max ${SCREENSHOT_MAX_SIZE_LABEL}).`);
    inputEl.value = "";
    return;
  }
  let endpoint;
  if (asAdmin) {
    endpoint =
      ownerKind === "approved"
        ? `/api/admin/buildings/${encodeURIComponent(ownerId)}/screenshots`
        : `/api/admin/submissions/${encodeURIComponent(ownerId)}/screenshots`;
  } else {
    endpoint =
      ownerKind === "approved"
        ? `/api/my-buildings/${encodeURIComponent(ownerId)}/screenshots`
        : `/api/my-submissions/${encodeURIComponent(ownerId)}/screenshots`;
  }
  const form = new FormData();
  form.append("screenshot", file);
  const res = await fetch(endpoint, { method: "POST", body: form });
  const body = await res.json().catch(() => ({}));
  inputEl.value = "";
  if (!res.ok) {
    alert(body.message || body.error || "Upload failed");
    return;
  }
  await reloadAfterScreenshotAction(reloadFn);
}

async function deleteMyScreenshot(screenshotId, asAdmin = false, reloadFn = "loadSubmissions") {
  if (!confirm("Remove this screenshot?")) {
    return;
  }
  const endpoint = asAdmin
    ? `/api/admin/screenshots/${encodeURIComponent(screenshotId)}`
    : `/api/my-screenshots/${encodeURIComponent(screenshotId)}`;
  const res = await fetch(endpoint, { method: "DELETE" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Delete failed");
    return;
  }
  await reloadAfterScreenshotAction(reloadFn);
}

async function setBuildingCover(buildingId, screenshotId, asAdmin = false, reloadFn = "loadSubmissions") {
  const endpoint = asAdmin
    ? `/api/admin/buildings/${encodeURIComponent(buildingId)}/cover`
    : `/api/my-buildings/${encodeURIComponent(buildingId)}/cover`;
  const res = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ screenshotId: screenshotId || null }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.message || body.error || "Could not update card image");
    return;
  }
  await reloadAfterScreenshotAction(reloadFn);
}

async function withdrawMySubmission(submissionId, displayName) {
  const label = displayName || submissionId;
  if (!confirm(`Withdraw "${label}" from the review queue?`)) {
    return;
  }
  const res = await fetch(`/api/my-submissions/${encodeURIComponent(submissionId)}/withdraw`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Withdraw failed");
    return;
  }
  loadSubmissions();
}

async function dismissMySubmission(submissionId, displayName) {
  const label = displayName || submissionId;
  if (!confirm(`Remove "${label}" from your rejected submissions list?`)) {
    return;
  }
  const res = await fetch(`/api/my-submissions/${encodeURIComponent(submissionId)}/dismiss`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Dismiss failed");
    return;
  }
  loadSubmissions();
}

async function removeMyBuilding(buildingId, displayName) {
  const label = displayName || buildingId;
  if (
    !confirm(
      `Remove "${label}" from the public marketplace?\n\nThis deletes the published files. Players who already downloaded it keep their local copy.`
    )
  ) {
    return;
  }
  const res = await fetch(`/api/my-buildings/${encodeURIComponent(buildingId)}/remove`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Remove failed");
    return;
  }
  loadSubmissions();
}

async function loadAdminPage() {
  const me = await fetch("/api/me").then((r) => r.json());
  if (!me.user) {
    window.location.href = "/auth/login";
    return;
  }
  if (!me.isAdmin) {
    window.location.href = "/";
    return;
  }
  renderAccountMenu(me.user, me.isAdmin);
  const restoredState = restoreAdminViewState();
  await Promise.all([loadAdminQueue(), loadAdminScreenshotQueue(), loadAdminCatalog()]);
  updateAdminJumpCounts();
  if (restoredState) {
    requestAnimationFrame(() => {
      window.scrollTo({ top: restoredState.scrollY, behavior: "auto" });
    });
  }
}

const ADMIN_VIEW_STATE_KEY = "aetherhaven.adminViewState";
const ADMIN_PUBLISHED_PAGE_SIZE = 12;

let adminPendingCache = [];
let adminPendingFilters = { query: "", sort: "newest" };
let adminPendingFiltersBound = false;

let adminScreenshotsCache = [];
let adminScreenshotsQuery = "";
let adminScreenshotsFiltersBound = false;

let adminPublishedCache = [];
let adminPublishedFilters = { query: "", author: "", sort: "newest" };
let adminPublishedFiltersBound = false;
let adminPublishedPage = 1;

function rememberAdminPosition() {
  const state = {
    scrollY: Math.max(0, window.scrollY || 0),
    pendingFilters: adminPendingFilters,
    screenshotsQuery: adminScreenshotsQuery,
    publishedFilters: adminPublishedFilters,
    publishedPage: adminPublishedPage,
  };
  try {
    sessionStorage.setItem(ADMIN_VIEW_STATE_KEY, JSON.stringify(state));
  } catch {
    // Navigation still works when session storage is unavailable.
  }
}

function restoreAdminViewState() {
  let state;
  try {
    state = JSON.parse(sessionStorage.getItem(ADMIN_VIEW_STATE_KEY) || "null");
    sessionStorage.removeItem(ADMIN_VIEW_STATE_KEY);
  } catch {
    state = null;
  }
  if (!state || typeof state !== "object") {
    return null;
  }
  adminPendingFilters = {
    query: String(state.pendingFilters?.query || ""),
    sort: String(state.pendingFilters?.sort || "newest"),
  };
  adminScreenshotsQuery = String(state.screenshotsQuery || "");
  adminPublishedFilters = {
    query: String(state.publishedFilters?.query || ""),
    author: String(state.publishedFilters?.author || ""),
    sort: String(state.publishedFilters?.sort || "newest"),
  };
  adminPublishedPage = Math.max(1, Number(state.publishedPage) || 1);
  return { scrollY: Math.max(0, Number(state.scrollY) || 0) };
}

function updateAdminJumpCounts() {
  const counts = {
    pending: adminPendingCache.length,
    screenshots: adminScreenshotsCache.length,
    published: adminPublishedCache.length,
  };
  document.querySelectorAll("[data-admin-count]").forEach((el) => {
    const key = el.getAttribute("data-admin-count");
    el.textContent = String(counts[key] ?? 0);
  });
}

function setAdminResultCount(elId, visible, total, emptyLabel) {
  const el = document.getElementById(elId);
  if (!el) return;
  if (!total) {
    el.textContent = emptyLabel || "";
    return;
  }
  if (visible === total) {
    el.textContent = `${total} item${total === 1 ? "" : "s"}`;
    return;
  }
  el.textContent = `Showing ${visible} of ${total}`;
}

function adminPendingMatchesQuery(item, query) {
  if (!query) return true;
  const haystack = [item.displayName, item.creatorName, item.submissionId, item.proposedId, item.description]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(query);
}

function sortAdminPending(entries, sort) {
  return [...entries].sort((a, b) => {
    if (sort === "name-asc") {
      return String(a.displayName || "").localeCompare(String(b.displayName || ""), undefined, {
        sensitivity: "base",
      });
    }
    if (sort === "name-desc") {
      return String(b.displayName || "").localeCompare(String(a.displayName || ""), undefined, {
        sensitivity: "base",
      });
    }
    const byDate = String(b.submittedAt || "").localeCompare(String(a.submittedAt || ""));
    if (byDate !== 0) return byDate;
    return String(a.displayName || "").localeCompare(String(b.displayName || ""), undefined, {
      sensitivity: "base",
    });
  });
}

function getFilteredAdminPending() {
  const query = String(adminPendingFilters.query || "")
    .trim()
    .toLowerCase();
  const filtered = adminPendingCache.filter((item) => adminPendingMatchesQuery(item, query));
  return sortAdminPending(filtered, adminPendingFilters.sort || "newest");
}

function renderAdminPendingCard(s) {
  const cardImg = s.iconUrl || buildingCardImageUrl(s) || "";
  const icon = cardImg
    ? `<img class="submission-card-thumb" src="${escapeAttr(cardImg)}" alt="" onerror="this.outerHTML='<div class=\\'submission-card-thumb submission-card-thumb--placeholder\\' aria-hidden=\\'true\\'></div>';" />`
    : `<div class="submission-card-thumb submission-card-thumb--placeholder" aria-hidden="true"></div>`;
  const dateLabel = formatRelativeDate(s.submittedAt);
  const description = String(s.description || "").trim();
  const descriptionHtml = description
    ? `<p class="building-description building-description--pending">${escapeHtml(description)}</p>`
    : `<p class="meta">No description provided.</p>`;
  return `
    <article class="submission-card">
      ${icon}
      <div class="submission-card-body">
        <div class="submission-card-header">
          <h4 class="submission-card-title">${escapeHtml(s.displayName || "Untitled")}</h4>
          <span class="submission-status submission-status-badge">Pending review</span>
        </div>
        ${dateLabel ? `<p class="meta submission-card-date-row">${escapeHtml(dateLabel)}</p>` : ""}
        <p class="meta submission-card-meta">${escapeHtml(s.submissionId)} · by ${escapeHtml(s.creatorName || "Unknown")}</p>
        <p class="meta submission-card-meta">Proposed id: ${escapeHtml(s.proposedId || "—")}</p>
        ${requiredModsHtml(s)}
        ${descriptionHtml}
        <div class="submission-card-actions">
          <a class="secondary" href="/edit.html?submissionId=${encodeURIComponent(s.submissionId)}&from=admin" onclick="rememberAdminPosition()">Edit</a>
          <button type="button" onclick="approveSubmission(${jsString(s.submissionId)}, ${jsString(s.proposedId || "")})">Approve</button>
          <button type="button" class="secondary" onclick="rejectSubmission(${jsString(s.submissionId)})">Reject</button>
        </div>
      </div>
    </article>`;
}

function renderFilteredAdminPending() {
  const el = document.getElementById("adminQueue");
  const toolbar = document.getElementById("adminPendingToolbar");
  if (!el) return;

  if (toolbar) {
    toolbar.hidden = adminPendingCache.length === 0;
  }
  updateAdminJumpCounts();

  if (!adminPendingCache.length) {
    el.innerHTML = emptyStateHtml("Queue empty.");
    setAdminResultCount("adminPendingResultCount", 0, 0);
    return;
  }

  const filtered = getFilteredAdminPending();
  setAdminResultCount("adminPendingResultCount", filtered.length, adminPendingCache.length);
  if (!filtered.length) {
    el.innerHTML = emptyStateHtml("No pending submissions match your filters.");
    return;
  }
  el.innerHTML = filtered.map(renderAdminPendingCard).join("");
}

function setupAdminPendingFilters() {
  const search = document.getElementById("adminPendingSearch");
  const sort = document.getElementById("adminPendingSort");
  const clear = document.getElementById("adminPendingClear");
  if (!search || !sort) return;
  search.value = adminPendingFilters.query || "";
  sort.value = adminPendingFilters.sort || "newest";
  if (adminPendingFiltersBound) return;
  adminPendingFiltersBound = true;
  search.addEventListener("input", () => {
    adminPendingFilters.query = search.value || "";
    renderFilteredAdminPending();
  });
  sort.addEventListener("change", () => {
    adminPendingFilters.sort = sort.value || "newest";
    renderFilteredAdminPending();
  });
  clear?.addEventListener("click", () => {
    adminPendingFilters = { query: "", sort: "newest" };
    search.value = "";
    sort.value = "newest";
    renderFilteredAdminPending();
  });
}

function adminScreenshotMatchesQuery(item, query) {
  if (!query) return true;
  const haystack = [
    item.displayName,
    item.creatorName,
    item.ownerLabel,
    item.ownerId,
    item.screenshotId,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(query);
}

function renderAdminScreenshotItem(s) {
  const ownerKindLabel = s.ownerKind === "approved" ? "Published build" : "Pending submission";
  const fullUrl = s.imageUrl || "";
  const previewUrl = s.cardUrl || fullUrl;
  return `
    <div class="queue-item screenshot-queue-item">
      <a class="screenshot-queue-preview" href="${escapeAttr(fullUrl)}" target="_blank" rel="noopener">
        <img src="${escapeAttr(previewUrl)}" alt="Screenshot preview" />
      </a>
      <div class="submission-body">
        <strong>${escapeHtml(s.displayName || "Untitled")}</strong>
        <p class="meta">${escapeHtml(ownerKindLabel)} · ${escapeHtml(s.ownerLabel || s.ownerId || "")}</p>
        <p class="meta">by ${escapeHtml(s.creatorName || "Unknown")} · ${formatBytes(s.bytes || 0)}</p>
        <div class="queue-actions">
          <button type="button" onclick="approveScreenshot('${escapeAttr(s.screenshotId)}')">Approve</button>
          <button type="button" class="secondary" onclick="rejectScreenshot('${escapeAttr(s.screenshotId)}')">Reject</button>
        </div>
      </div>
    </div>`;
}

function renderFilteredAdminScreenshots() {
  const el = document.getElementById("adminScreenshotQueue");
  const toolbar = document.getElementById("adminScreenshotsToolbar");
  if (!el) return;

  if (toolbar) {
    toolbar.hidden = adminScreenshotsCache.length === 0;
  }
  updateAdminJumpCounts();

  if (!adminScreenshotsCache.length) {
    el.innerHTML = emptyStateHtml("No screenshots waiting for review.");
    setAdminResultCount("adminScreenshotsResultCount", 0, 0);
    return;
  }

  const query = String(adminScreenshotsQuery || "")
    .trim()
    .toLowerCase();
  const filtered = adminScreenshotsCache.filter((item) => adminScreenshotMatchesQuery(item, query));
  setAdminResultCount("adminScreenshotsResultCount", filtered.length, adminScreenshotsCache.length);
  if (!filtered.length) {
    el.innerHTML = emptyStateHtml("No screenshots match your search.");
    return;
  }
  el.innerHTML = filtered.map(renderAdminScreenshotItem).join("");
}

function setupAdminScreenshotsFilters() {
  const search = document.getElementById("adminScreenshotsSearch");
  const clear = document.getElementById("adminScreenshotsClear");
  if (!search) return;
  search.value = adminScreenshotsQuery || "";
  if (adminScreenshotsFiltersBound) return;
  adminScreenshotsFiltersBound = true;
  search.addEventListener("input", () => {
    adminScreenshotsQuery = search.value || "";
    renderFilteredAdminScreenshots();
  });
  clear?.addEventListener("click", () => {
    adminScreenshotsQuery = "";
    search.value = "";
    renderFilteredAdminScreenshots();
  });
}

function adminPublishedMatches(entry, filters) {
  if (filters.author && (entry.creatorName || "Unknown") !== filters.author) {
    return false;
  }
  const query = String(filters.query || "")
    .trim()
    .toLowerCase();
  if (!query) return true;
  const haystack = [entry.displayName, entry.id, entry.creatorName, entry.styleId, ...(entry.tags || [])]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(query);
}

function getFilteredAdminPublished() {
  const filtered = adminPublishedCache.filter((e) => adminPublishedMatches(e, adminPublishedFilters));
  return sortCatalogEntries(filtered, adminPublishedFilters.sort || "newest");
}

function renderAdminPublishedPagination(totalPages) {
  const nav = document.getElementById("adminPublishedPagination");
  if (!nav) return;
  if (totalPages <= 1) {
    nav.hidden = true;
    nav.innerHTML = "";
    return;
  }
  nav.hidden = false;
  const pages = catalogPageWindow(adminPublishedPage, totalPages);
  let html = `<button type="button" class="secondary" ${
    adminPublishedPage <= 1 ? "disabled" : ""
  } onclick="goToAdminPublishedPage(${adminPublishedPage - 1})">Prev</button>`;
  let previous = 0;
  for (const page of pages) {
    if (previous && page - previous > 1) {
      html += `<span class="catalog-pagination-ellipsis" aria-hidden="true">…</span>`;
    }
    html += `<button type="button" class="secondary" ${
      page === adminPublishedPage ? 'aria-current="page"' : ""
    } onclick="goToAdminPublishedPage(${page})" aria-label="Page ${page}">${page}</button>`;
    previous = page;
  }
  html += `<button type="button" class="secondary" ${
    adminPublishedPage >= totalPages ? "disabled" : ""
  } onclick="goToAdminPublishedPage(${adminPublishedPage + 1})">Next</button>`;
  nav.innerHTML = html;
}

function goToAdminPublishedPage(page) {
  const filteredTotal = getFilteredAdminPublished().length;
  const totalPages = Math.max(1, Math.ceil(filteredTotal / ADMIN_PUBLISHED_PAGE_SIZE));
  adminPublishedPage = Math.min(Math.max(1, Number(page) || 1), totalPages);
  renderFilteredAdminPublished();
  document.getElementById("adminPublishedToolbar")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderFilteredAdminPublished() {
  const el = document.getElementById("adminCatalog");
  const toolbar = document.getElementById("adminPublishedToolbar");
  if (!el) return;

  if (toolbar) {
    toolbar.hidden = adminPublishedCache.length === 0;
  }
  updateAdminJumpCounts();

  if (!adminPublishedCache.length) {
    el.innerHTML = emptyStateHtml("No published buildings.");
    setAdminResultCount("adminPublishedResultCount", 0, 0);
    renderAdminPublishedPagination(0);
    return;
  }

  const filtered = getFilteredAdminPublished();
  if (!filtered.length) {
    el.innerHTML = emptyStateHtml("No published buildings match your filters.");
    setAdminResultCount("adminPublishedResultCount", 0, adminPublishedCache.length);
    renderAdminPublishedPagination(0);
    return;
  }
  const totalPages = Math.max(1, Math.ceil(filtered.length / ADMIN_PUBLISHED_PAGE_SIZE));
  adminPublishedPage = Math.min(Math.max(1, adminPublishedPage), totalPages);
  const start = (adminPublishedPage - 1) * ADMIN_PUBLISHED_PAGE_SIZE;
  const pageEntries = filtered.slice(start, start + ADMIN_PUBLISHED_PAGE_SIZE);
  const resultCount = document.getElementById("adminPublishedResultCount");
  if (resultCount) {
    const range = pageEntries.length === 1 ? `${start + 1}` : `${start + 1}–${start + pageEntries.length}`;
    resultCount.textContent =
      filtered.length === adminPublishedCache.length
        ? `Showing ${range} of ${filtered.length}`
        : `Showing ${range} of ${filtered.length} matches (${adminPublishedCache.length} total)`;
  }
  el.innerHTML = pageEntries
    .map((e) => renderBuildingCard(e, { canVote: false, adminDelete: true, adminEdit: true, showId: true }))
    .join("");
  renderAdminPublishedPagination(totalPages);
}

function setupAdminPublishedFilters() {
  const search = document.getElementById("adminPublishedSearch");
  const author = document.getElementById("adminPublishedAuthor");
  const sort = document.getElementById("adminPublishedSort");
  const clear = document.getElementById("adminPublishedClear");
  if (!search || !sort) return;
  search.value = adminPublishedFilters.query || "";
  if (author) author.value = adminPublishedFilters.author || "";
  sort.value = adminPublishedFilters.sort || "newest";
  if (adminPublishedFiltersBound) return;
  adminPublishedFiltersBound = true;
  const onChange = () => {
    adminPublishedFilters.query = search.value || "";
    adminPublishedFilters.author = author?.value || "";
    adminPublishedFilters.sort = sort.value || "newest";
    adminPublishedPage = 1;
    renderFilteredAdminPublished();
  };
  search.addEventListener("input", onChange);
  author?.addEventListener("change", onChange);
  sort.addEventListener("change", onChange);
  clear?.addEventListener("click", () => {
    adminPublishedFilters = { query: "", author: "", sort: "newest" };
    adminPublishedPage = 1;
    search.value = "";
    if (author) author.value = "";
    sort.value = "newest";
    renderFilteredAdminPublished();
  });
}

async function loadAdminScreenshotQueue() {
  const el = document.getElementById("adminScreenshotQueue");
  if (!el) return;
  try {
    const res = await fetch("/api/admin/screenshots/pending");
    if (res.status === 401) {
      window.location.href = "/auth/login";
      return;
    }
    if (res.status === 403) {
      el.innerHTML =
        "<p>Admin access required. Add your profile UUID to <code>ADMIN_HYTALE_UUIDS</code> on Railway.</p>";
      return;
    }
    if (!res.ok) {
      el.innerHTML = emptyStateHtml("Could not load pending screenshots.");
      return;
    }
    const data = await res.json();
    adminScreenshotsCache = data.screenshots || [];
    setupAdminScreenshotsFilters();
    renderFilteredAdminScreenshots();
  } catch {
    el.innerHTML = emptyStateHtml("Could not load pending screenshots.");
  }
}

async function approveScreenshot(screenshotId) {
  const res = await fetch(`/api/admin/screenshots/${encodeURIComponent(screenshotId)}/approve`, {
    method: "POST",
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Approve failed");
    return;
  }
  loadAdminScreenshotQueue();
}

async function rejectScreenshot(screenshotId) {
  if (!confirm("Reject and delete this screenshot?")) {
    return;
  }
  const res = await fetch(`/api/admin/screenshots/${encodeURIComponent(screenshotId)}/reject`, {
    method: "POST",
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Reject failed");
    return;
  }
  loadAdminScreenshotQueue();
}

async function loadAdminQueue() {
  const el = document.getElementById("adminQueue");
  if (!el) return;
  try {
    const res = await fetch("/api/admin/pending");
    if (res.status === 401) {
      window.location.href = "/auth/login";
      return;
    }
    if (res.status === 403) {
      el.innerHTML =
        "<p>Admin access required. Add your profile UUID to <code>ADMIN_HYTALE_UUIDS</code> on Railway.</p>";
      return;
    }
    if (!res.ok) {
      el.innerHTML = emptyStateHtml("Could not load pending submissions.");
      return;
    }
    const data = await res.json();
    adminPendingCache = data.submissions || [];
    setupAdminPendingFilters();
    renderFilteredAdminPending();
  } catch {
    el.innerHTML = emptyStateHtml("Could not load pending submissions.");
  }
}

async function approveSubmission(submissionId, proposedId) {
  await fetch(`/api/admin/approve/${encodeURIComponent(submissionId)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: proposedId }),
  });
  loadAdminQueue();
  loadAdminCatalog();
}

async function rejectSubmission(submissionId) {
  const reasonRaw = window.prompt("Reason for rejection (shown to the creator):");
  if (reasonRaw === null) {
    return;
  }
  const reason = String(reasonRaw).trim();
  if (!reason) {
    alert("Please enter a rejection reason.");
    return;
  }
  const res = await fetch(`/api/admin/reject/${encodeURIComponent(submissionId)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) {
    alert("Could not reject submission.");
    return;
  }
  loadAdminQueue();
}

async function loadAdminCatalog() {
  const el = document.getElementById("adminCatalog");
  if (!el) return;
  el.innerHTML = emptyStateHtml("Loading published buildings…");
  try {
    const data = await fetchCatalog();
    adminPublishedCache = data.entries || [];
    const authors = uniqueSortedValues(adminPublishedCache.map((e) => e.creatorName || "Unknown"));
    fillSelectOptions(document.getElementById("adminPublishedAuthor"), authors, "All authors");
    setupAdminPublishedFilters();
    renderFilteredAdminPublished();
  } catch {
    el.innerHTML = emptyStateHtml("Could not load published buildings.");
  }
}

async function deleteApprovedBuilding(buildingId, displayName) {
  const label = displayName || buildingId;
  if (!confirm(`Permanently remove "${label}" from the public marketplace?\n\nThis deletes the approved files and removes it from the catalog. It cannot be undone.`)) {
    return;
  }
  const res = await fetch(`/api/admin/delete/${encodeURIComponent(buildingId)}`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Delete failed");
    return;
  }
  loadAdminCatalog();
}

let catalogEntriesById = new Map();
let detailModalEscBound = false;

function ensureBuildingDetailModal() {
  if (document.getElementById("buildingDetailModal")) {
    return;
  }
  const modal = document.createElement("div");
  modal.id = "buildingDetailModal";
  modal.className = "building-modal";
  modal.hidden = true;
  modal.innerHTML = `
    <div class="building-modal-backdrop" data-close-modal="true"></div>
    <div class="building-modal-dialog" role="dialog" aria-modal="true" aria-labelledby="buildingDetailTitle">
      <button type="button" class="building-modal-close" aria-label="Close" data-close-modal="true">×</button>
      <div id="buildingDetailContent" class="building-modal-content"></div>
    </div>`;
  modal.addEventListener("click", (event) => {
    if (event.target?.dataset?.closeModal === "true") {
      closeBuildingDetail();
    }
  });
  document.body.appendChild(modal);
  if (!detailModalEscBound) {
    detailModalEscBound = true;
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        closeBuildingDetail();
      }
    });
  }
}

async function openBuildingDetail(buildingId) {
  ensureBuildingDetailModal();
  const modal = document.getElementById("buildingDetailModal");
  const content = document.getElementById("buildingDetailContent");
  if (!modal || !content) {
    return;
  }

  let entry = catalogEntriesById.get(buildingId);
  if (!entry) {
    try {
      const data = await fetchCatalog();
      allCatalogEntries = data.entries || [];
      catalogEntriesById = new Map(allCatalogEntries.map((e) => [e.id, e]));
      entry = catalogEntriesById.get(buildingId);
    } catch {
      entry = null;
    }
  }
  if (!entry) {
    alert("Could not load building details.");
    return;
  }

  const modalGold = goldCostHtml(entry, "gold-cost--inline");
  content.innerHTML = `
    <div class="building-modal-header">
      ${buildingIconHtml(buildingCardImageUrl(entry), "building-icon building-icon--modal", Boolean(entry.usesCoverImage))}
      <div class="building-modal-header-main">
        <div class="building-modal-title-row">
          <h2 id="buildingDetailTitle">${escapeHtml(entry.displayName)}</h2>
          <div class="building-card-actions building-modal-actions">
            ${favoriteControlHtml(entry, catalogCanVote)}
            ${upvoteControlHtml(entry, catalogCanVote)}
          </div>
        </div>
        <p class="meta">by ${escapeHtml(entry.creatorName || "Unknown")}</p>
        <p class="meta building-modal-id"><code>${escapeHtml(entry.id)}</code></p>
        <p class="meta">${formatBytes(entry.prefabBytes || 0)} · ${escapeHtml(formatDownloadCount(entry.downloadCount))} · v${escapeHtml(entry.version)}${modalGold ? ` · ${modalGold}` : ""}</p>
        ${requiredModsHtml(entry)}
      </div>
    </div>
    ${
      String(entry.description || "").trim()
        ? `<div class="building-modal-description"><p>${escapeHtml(entry.description)}</p></div>`
        : ""
    }
    <div class="building-gallery">
      <p class="meta">Loading screenshots…</p>
    </div>`;
  modal.hidden = false;
  document.body.classList.add("modal-open");

  try {
    const res = await fetch(`/api/buildings/${encodeURIComponent(buildingId)}/screenshots`);
    const data = await res.json().catch(() => ({}));
    const shots = res.ok ? data.screenshots || [] : [];
    const gallery = content.querySelector(".building-gallery");
    if (!gallery) {
      return;
    }
    if (!shots.length) {
      gallery.innerHTML = `<p class="meta">No screenshots yet.</p>`;
      return;
    }
    const first = shots[0];
    gallery.innerHTML = `
      <div class="building-gallery-main">
        <img id="buildingGalleryMain" src="${escapeAttr(first.url)}" alt="Screenshot of ${escapeAttr(entry.displayName)}" />
      </div>
      <div class="building-gallery-thumbs" role="list">
        ${shots
          .map(
            (shot, index) => `
          <button
            type="button"
            class="building-gallery-thumb${index === 0 ? " building-gallery-thumb--active" : ""}"
            role="listitem"
            aria-label="Screenshot ${index + 1}"
            onclick="selectBuildingGalleryImage(this, '${escapeAttr(shot.url)}')"
          >
            <img src="${escapeAttr(shot.cardUrl || shot.url)}" alt="" />
          </button>`
          )
          .join("")}
      </div>`;
  } catch {
    const gallery = content.querySelector(".building-gallery");
    if (gallery) {
      gallery.innerHTML = `<p class="meta">Could not load screenshots.</p>`;
    }
  }
}

function selectBuildingGalleryImage(buttonEl, url) {
  const main = document.getElementById("buildingGalleryMain");
  if (main) {
    main.src = url;
  }
  const thumbs = buttonEl?.closest(".building-gallery-thumbs");
  if (thumbs) {
    thumbs.querySelectorAll(".building-gallery-thumb").forEach((el) => {
      el.classList.remove("building-gallery-thumb--active");
    });
  }
  buttonEl?.classList.add("building-gallery-thumb--active");
}

function closeBuildingDetail() {
  const modal = document.getElementById("buildingDetailModal");
  if (!modal || modal.hidden) {
    return;
  }
  modal.hidden = true;
  document.body.classList.remove("modal-open");
}

/** @type {{ isAdmin: boolean, kind: string, id: string, submissionId?: string } | null} */
let editPageContext = null;

const COMMON_RESOURCE_TYPES = ["Wood_All", "Rock", "Soils", "Rubble"];

function materialRowHtml(row = {}, index = 0) {
  const isItem = Boolean(row.itemId && !row.resourceTypeId);
  const idValue = isItem ? row.itemId || "" : row.resourceTypeId || "";
  const count = Number(row.count) > 0 ? Number(row.count) : 1;
  return `
    <div class="material-row" data-index="${index}">
      <select class="material-kind" aria-label="Material kind">
        <option value="resource"${isItem ? "" : " selected"}>Resource type</option>
        <option value="item"${isItem ? " selected" : ""}>Item id</option>
      </select>
      <input class="material-id" type="text" list="resourceTypeDatalist" value="${escapeAttr(idValue)}" placeholder="${isItem ? "Item id" : "Resource type"}" aria-label="Material id" />
      <input class="material-count" type="number" min="1" step="1" value="${escapeAttr(String(count))}" aria-label="Count" />
      <button type="button" class="secondary" onclick="removeMaterialRow(this)">Remove</button>
    </div>`;
}

function collectMaterialsFromForm() {
  const rows = Array.from(document.querySelectorAll("#materialsEditor .material-row"));
  return rows.map((row) => {
    const kind = row.querySelector(".material-kind")?.value || "resource";
    const id = String(row.querySelector(".material-id")?.value || "").trim();
    const count = Number(row.querySelector(".material-count")?.value);
    if (kind === "item") {
      return { itemId: id, count };
    }
    return { resourceTypeId: id, count };
  });
}

function addMaterialRow() {
  const editor = document.getElementById("materialsEditor");
  if (!editor) {
    return;
  }
  const wrap = document.createElement("div");
  wrap.innerHTML = materialRowHtml({}, editor.querySelectorAll(".material-row").length);
  editor.appendChild(wrap.firstElementChild);
}

function removeMaterialRow(buttonEl) {
  const row = buttonEl?.closest(".material-row");
  row?.remove();
}

function parseTagsInput(raw) {
  return String(raw || "")
    .split(",")
    .map((t) => t.trim().toLowerCase())
    .filter(Boolean);
}

let adminRawEditorLoaded = { building: false, prefab: false };

function adminRawEditorIds(fileKind) {
  const name = fileKind === "prefab" ? "Prefab" : "Building";
  return {
    editor: `raw${name}Editor`,
    load: `raw${name}Load`,
    save: `raw${name}Save`,
    status: `raw${name}Status`,
  };
}

function adminRawFileEndpoint(fileKind) {
  if (!editPageContext?.isAdmin) return "";
  if (editPageContext.kind === "pending") {
    const id = editPageContext.submissionId || editPageContext.id;
    return `/api/admin/submissions/${encodeURIComponent(id)}/files/${fileKind}`;
  }
  return `/api/admin/buildings/${encodeURIComponent(editPageContext.id)}/files/${fileKind}`;
}

function setAdminRawStatus(fileKind, message, isError = false) {
  const el = document.getElementById(adminRawEditorIds(fileKind).status);
  if (!el) return;
  el.hidden = false;
  el.textContent = message;
  el.classList.toggle("raw-file-status--error", isError);
}

async function loadAdminRawFile(fileKind) {
  if (adminRawEditorLoaded[fileKind]) return;
  const ids = adminRawEditorIds(fileKind);
  const editor = document.getElementById(ids.editor);
  const loadButton = document.getElementById(ids.load);
  const saveButton = document.getElementById(ids.save);
  const endpoint = adminRawFileEndpoint(fileKind);
  if (!editor || !endpoint) return;
  if (loadButton) loadButton.disabled = true;
  setAdminRawStatus(fileKind, "Loading…");
  try {
    const res = await fetch(endpoint, { headers: { Accept: "text/plain" } });
    const text = await res.text();
    if (!res.ok) {
      let message = text || "Could not load file.";
      try {
        const body = JSON.parse(text);
        message = body.message || body.error || message;
      } catch {
        // Use response text.
      }
      throw new Error(message);
    }
    editor.value = text;
    adminRawEditorLoaded[fileKind] = true;
    editor.disabled = false;
    if (saveButton) saveButton.disabled = false;
    setAdminRawStatus(fileKind, `${formatBytes(new Blob([text]).size)} loaded.`);
  } catch (err) {
    if (loadButton) loadButton.disabled = false;
    setAdminRawStatus(fileKind, err.message || "Could not load file.", true);
  }
}

function formatAdminRawFile(fileKind) {
  const editor = document.getElementById(adminRawEditorIds(fileKind).editor);
  if (!editor || !adminRawEditorLoaded[fileKind]) return;
  try {
    const parsed = JSON.parse(editor.value);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("File must contain a JSON object.");
    }
    editor.value = `${JSON.stringify(parsed, null, 2)}\n`;
    setAdminRawStatus(fileKind, "JSON formatted. Save to apply it.");
  } catch (err) {
    setAdminRawStatus(fileKind, err.message || "Invalid JSON.", true);
  }
}

async function saveAdminRawFile(fileKind) {
  const ids = adminRawEditorIds(fileKind);
  const editor = document.getElementById(ids.editor);
  const saveButton = document.getElementById(ids.save);
  const endpoint = adminRawFileEndpoint(fileKind);
  if (!editor || !endpoint || !adminRawEditorLoaded[fileKind]) return;
  try {
    const parsed = JSON.parse(editor.value);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("File must contain a JSON object.");
    }
  } catch (err) {
    setAdminRawStatus(fileKind, err.message || "Invalid JSON.", true);
    return;
  }
  const fileName = fileKind === "prefab" ? "prefab.prefab.json" : "building.json";
  if (!confirm(`Replace ${fileName} with the text in this editor?`)) return;

  if (saveButton) saveButton.disabled = true;
  setAdminRawStatus(fileKind, "Saving…");
  try {
    const res = await fetch(endpoint, {
      method: "PUT",
      headers: { "Content-Type": "text/plain; charset=utf-8", Accept: "application/json" },
      body: editor.value,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.message || body.error || "Save failed.");
    }
    setAdminRawStatus(fileKind, "Saved. Refreshing build data…");
    const scrollY = window.scrollY;
    await loadEditPage();
    requestAnimationFrame(() => window.scrollTo({ top: scrollY, behavior: "auto" }));
  } catch (err) {
    if (saveButton) saveButton.disabled = false;
    setAdminRawStatus(fileKind, err.message || "Save failed.", true);
  }
}

function adminRawEditorsHtml() {
  const editor = (fileKind, fileName, description) => {
    const ids = adminRawEditorIds(fileKind);
    return `
      <details class="raw-file-editor" ontoggle="if(this.open) loadAdminRawFile('${fileKind}')">
        <summary>${fileName}</summary>
        <p class="meta">${description}</p>
        <textarea id="${ids.editor}" class="raw-json-textarea" aria-label="${fileName} JSON" spellcheck="false" wrap="off" disabled></textarea>
        <div class="raw-file-actions">
          <button type="button" class="secondary" id="${ids.load}" onclick="loadAdminRawFile('${fileKind}')">Load file</button>
          <button type="button" class="secondary" onclick="formatAdminRawFile('${fileKind}')">Format JSON</button>
          <button type="button" id="${ids.save}" onclick="saveAdminRawFile('${fileKind}')" disabled>Save raw file</button>
          <span id="${ids.status}" class="meta raw-file-status" hidden></span>
        </div>
      </details>`;
  };
  return `
    <section class="edit-raw-files card">
      <h3>Raw files</h3>
      <p class="meta">Admin only. Invalid files are rejected, but valid changes take effect immediately.</p>
      ${editor("building", "building.json", "Building definition and marketplace metadata. Published id and prefabPath cannot be changed.")}
      ${editor("prefab", "prefab.prefab.json", "Full prefab block and entity data. Large files may take a moment to load or format.")}
    </section>`;
}

async function loadEditPage() {
  const root = document.getElementById("editRoot");
  if (!root) {
    return;
  }
  const params = new URLSearchParams(window.location.search);
  const buildingId = String(params.get("id") || "").trim();
  const submissionId = String(params.get("submissionId") || "").trim();
  const fromParam = String(params.get("from") || "").trim().toLowerCase();

  const me = await fetch("/api/me").then((r) => r.json());
  if (!me.user) {
    window.location.href = "/auth/login";
    return;
  }
  renderAccountMenu(me.user, me.isAdmin);
  const isAdmin = Boolean(me.isAdmin);

  if (!buildingId && !submissionId) {
    root.innerHTML = emptyStateHtml("Missing building id.");
    return;
  }
  if (submissionId && !isAdmin) {
    root.innerHTML = emptyStateHtml("Admin access required to edit pending submissions.");
    return;
  }

  let endpoint;
  if (submissionId) {
    endpoint = `/api/admin/submissions/${encodeURIComponent(submissionId)}`;
    editPageContext = { isAdmin: true, kind: "pending", id: submissionId, submissionId };
  } else if (isAdmin) {
    endpoint = `/api/admin/buildings/${encodeURIComponent(buildingId)}`;
    editPageContext = { isAdmin: true, kind: "approved", id: buildingId };
  } else {
    endpoint = `/api/my-buildings/${encodeURIComponent(buildingId)}`;
    editPageContext = { isAdmin: false, kind: "approved", id: buildingId };
  }

  root.innerHTML = `<p class="meta">Loading…</p>`;
  const res = await fetch(endpoint);
  if (res.status === 401) {
    window.location.href = "/auth/login";
    return;
  }
  if (res.status === 403) {
    root.innerHTML = emptyStateHtml("You do not have permission to edit this build.");
    return;
  }
  if (res.status === 404) {
    root.innerHTML = emptyStateHtml("Build not found.");
    return;
  }
  if (!res.ok) {
    root.innerHTML = emptyStateHtml("Could not load build for editing.");
    return;
  }
  const data = await res.json();
  const materials = Array.isArray(data.materials) ? data.materials : [];
  const adminFields = isAdmin
    ? `
      <label class="edit-field">
        <span class="edit-field-label">Style</span>
        <input id="editStyleId" type="text" value="${escapeAttr(data.styleId || "misc")}" placeholder="e.g. Coastal Ruins" />
      </label>
      <label class="edit-field">
        <span class="edit-field-label">Tags (comma-separated)</span>
        <input id="editTags" type="text" value="${escapeAttr((data.tags || []).join(", "))}" />
      </label>`
    : "";
  let backHref = "/submissions.html";
  if (fromParam === "admin" || submissionId) {
    backHref = "/admin.html";
  } else if (fromParam === "submissions") {
    backHref = "/submissions.html";
  }
  const heroImg = buildingCardImageUrl(data) || data.iconUrl || "";
  const hero = heroImg
    ? `<div class="edit-hero"><img src="${escapeAttr(heroImg)}" alt="" /></div>`
    : `<div class="edit-hero edit-hero--placeholder" aria-hidden="true"></div>`;
  adminRawEditorLoaded = { building: false, prefab: false };

  root.innerHTML = `
    <div class="edit-layout">
      <div class="edit-main card">
        <p class="meta"><a href="${escapeAttr(backHref)}">← Back</a>${data.creatorName ? ` · by ${escapeHtml(data.creatorName)}` : ""}</p>
        <h2>${escapeHtml(data.displayName || "Edit build")}</h2>
        ${hero}
        <form id="editBuildingForm" class="edit-form" onsubmit="event.preventDefault(); saveEditPage();">
          <label class="edit-field">
            <span class="edit-field-label">Name</span>
            <input id="editDisplayName" type="text" required maxlength="80" value="${escapeAttr(data.displayName || "")}" />
          </label>
          <label class="edit-field">
            <span class="edit-field-label">Description</span>
            <textarea id="editDescription" rows="5" maxlength="2000">${escapeHtml(data.description || "")}</textarea>
          </label>
          <label class="edit-field">
            <span class="edit-field-label">Gold cost</span>
            <input id="editGoldCost" type="number" min="0" step="1" value="${escapeAttr(String(data.treasuryGoldCoinCost || 0))}" />
          </label>
          ${adminFields}
          <div class="edit-field">
            <span class="edit-field-label">Resource costs</span>
            <datalist id="resourceTypeDatalist">
              ${COMMON_RESOURCE_TYPES.map((t) => `<option value="${escapeAttr(t)}"></option>`).join("")}
            </datalist>
            <div id="materialsEditor" class="materials-editor">
              ${materials.length ? materials.map((m, i) => materialRowHtml(m, i)).join("") : materialRowHtml({}, 0)}
            </div>
            <button type="button" class="secondary" onclick="addMaterialRow()">Add material</button>
          </div>
          <div class="edit-actions">
            <button type="submit">Save changes</button>
            <a class="secondary edit-cancel" href="${escapeAttr(backHref)}">Cancel</a>
            <span id="editStatus" class="meta" hidden></span>
          </div>
        </form>
      </div>
      <div class="edit-side card">
        <h3>Screenshots</h3>
        ${renderOwnerScreenshots(data, {
          asAdmin: isAdmin,
          compact: false,
          reloadFn: "loadEditPage",
        })}
      </div>
      ${isAdmin ? adminRawEditorsHtml() : ""}
    </div>`;
}

async function saveEditPage() {
  if (!editPageContext) {
    return;
  }
  const statusEl = document.getElementById("editStatus");
  const displayName = String(document.getElementById("editDisplayName")?.value || "").trim();
  const description = String(document.getElementById("editDescription")?.value || "").trim();
  const goldRaw = document.getElementById("editGoldCost")?.value;
  const treasuryGoldCoinCost = goldRaw === "" ? 0 : Number(goldRaw);
  const materials = collectMaterialsFromForm().filter((m) => {
    const id = m.itemId || m.resourceTypeId;
    return id && Number(m.count) >= 1;
  });
  if (!displayName) {
    alert("Display name is required.");
    return;
  }
  const body = {
    displayName,
    description,
    treasuryGoldCoinCost,
    materials,
  };
  if (editPageContext.isAdmin) {
    body.styleId = String(document.getElementById("editStyleId")?.value || "misc").trim() || "misc";
    body.tags = parseTagsInput(document.getElementById("editTags")?.value);
  }

  let endpoint;
  if (editPageContext.kind === "pending") {
    endpoint = `/api/admin/submissions/${encodeURIComponent(editPageContext.submissionId || editPageContext.id)}`;
  } else if (editPageContext.isAdmin) {
    endpoint = `/api/admin/buildings/${encodeURIComponent(editPageContext.id)}`;
  } else {
    endpoint = `/api/my-buildings/${encodeURIComponent(editPageContext.id)}`;
  }

  if (statusEl) {
    statusEl.hidden = false;
    statusEl.textContent = "Saving…";
  }
  const res = await fetch(endpoint, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (statusEl) {
      statusEl.textContent = data.message || data.error || "Save failed";
    }
    alert(data.message || data.error || "Save failed");
    return;
  }
  if (statusEl) {
    statusEl.textContent = "Saved.";
  }
  await loadEditPage();
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/'/g, "&#39;");
}

/** JS string literal safe inside a double-quoted HTML attribute (e.g. onclick="fn(${jsString(name)})"). */
function jsString(value) {
  return JSON.stringify(String(value ?? ""))
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

window.toggleFavorite = toggleFavorite;
window.toggleUpvote = toggleUpvote;
window.goToCatalogPage = goToCatalogPage;
