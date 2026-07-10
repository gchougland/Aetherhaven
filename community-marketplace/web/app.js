async function fetchMe() {
  const res = await fetch("/api/me");
  const { user } = await res.json();
  return user;
}

function updateAuthNav(user) {
  const login = document.getElementById("loginLink");
  const logout = document.getElementById("logoutLink");
  if (user) {
    if (login) login.hidden = true;
    if (logout) logout.hidden = false;
  } else {
    if (login) login.hidden = false;
    if (logout) logout.hidden = true;
  }
}

async function refreshAuthNav() {
  updateAuthNav(await fetchMe());
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

function buildingIconHtml(iconUrl, sizeClass) {
  const cls = sizeClass || "building-icon";
  if (!iconUrl) {
    return `<div class="building-icon-wrap building-icon-wrap--placeholder" aria-hidden="true"></div>`;
  }
  return `<div class="building-icon-wrap"><img class="${cls}" src="${escapeAttr(iconUrl)}" alt="" onerror="this.parentElement.classList.add('building-icon-wrap--placeholder');this.remove();" /></div>`;
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

function formatDownloadCount(count) {
  const n = Number(count) || 0;
  if (n === 1) {
    return "1 download";
  }
  return `${n} downloads`;
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
  const { canVote = false, adminDelete = false, showId = false, openDetail = false } = options;
  const deleteBtn = adminDelete
    ? `<button
        type="button"
        class="danger admin-delete-btn"
        onclick="event.stopPropagation(); deleteApprovedBuilding('${escapeAttr(entry.id)}', '${escapeAttr(entry.displayName)}')"
      >Remove permanently</button>`
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
  return `
    <article class="${cardClass}" data-building-id="${escapeAttr(entry.id)}" ${openAttrs}>
      <div class="building-card-header">
        ${buildingIconHtml(entry.iconUrl)}
        ${upvoteControlHtml(entry, canVote)}
      </div>
      <h3>${escapeHtml(entry.displayName)}</h3>
      ${idMeta}
      <p class="meta">by ${escapeHtml(entry.creatorName || "Unknown")}</p>
      <p class="meta">${formatBytes(entry.prefabBytes || 0)} · <span class="download-count">${escapeHtml(formatDownloadCount(entry.downloadCount))}</span> · v${escapeHtml(entry.version)}</p>
      ${shotHint}
      ${descriptionToggleHtml(entry)}
      ${deleteBtn}
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

async function loadCatalog() {
  const el = document.getElementById("catalog");
  const status = document.getElementById("status");
  if (!el) return;
  try {
    const user = await fetchMe();
    updateAuthNav(user);
    const data = await fetchCatalog();
    catalogEntriesById = new Map((data.entries || []).map((e) => [e.id, e]));
    if (!data.entries?.length) {
      el.innerHTML = emptyStateHtml("No approved buildings yet.");
      return;
    }
    el.innerHTML = data.entries.map((e) => renderBuildingCard(e, { canVote: Boolean(user), openDetail: true })).join("");
    ensureBuildingDetailModal();
  } catch (e) {
    showStatusError(status, "Could not load catalog.");
  }
}

async function loadDashboard() {
  const profile = document.getElementById("profile");
  const list = document.getElementById("mySubmissions");
  const me = await fetch("/api/me").then((r) => r.json());
  if (!me.user) {
    window.location.href = "/auth/login";
    return;
  }
  const name = me.user.profile?.username || me.user.sub || "Player";
  const uuid = me.user.profile?.uuid || "";
  profile.innerHTML = `<h2>Signed in as ${escapeHtml(name)}</h2><p class="meta">Profile UUID: <code>${escapeHtml(uuid)}</code></p><p class="meta">Submissions from the game are linked by profile UUID or your Hytale username (<strong>${escapeHtml(name)}</strong>). These can differ from your in-game session UUID.</p>`;

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
    const items = data.submissions || [];
    if (!items.length) {
      list.innerHTML = emptyStateHtml("No submissions yet.");
      return;
    }
    list.innerHTML = items.map(renderMySubmissionItem).join("");
  } catch {
    list.innerHTML = emptyStateHtml("Could not load your submissions.");
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

function renderOwnerScreenshots(item) {
  if (item.kind !== "pending" && item.kind !== "approved") {
    return "";
  }
  const shots = Array.isArray(item.screenshots) ? item.screenshots : [];
  const ownerKind = item.kind;
  const ownerId = item.kind === "approved" ? item.id : item.submissionId;
  const atLimit = shots.length >= MAX_SCREENSHOTS_PER_OWNER;
  const thumbs = shots.length
    ? shots
        .map((shot) => {
          const img = shot.url
            ? `<img src="${escapeAttr(shot.url)}" alt="" />`
            : `<div class="screenshot-thumb-placeholder" aria-hidden="true"></div>`;
          return `
      <div class="screenshot-thumb" data-status="${escapeAttr(shot.status || "pending")}">
        ${img}
        <span class="screenshot-thumb-status">${escapeHtml(screenshotStatusLabel(shot.status))}</span>
        <button type="button" class="screenshot-thumb-delete" title="Remove screenshot" aria-label="Remove screenshot" onclick="deleteMyScreenshot('${escapeAttr(shot.screenshotId)}')">×</button>
      </div>`;
        })
        .join("")
    : `<p class="meta screenshot-empty">No screenshots yet.</p>`;

  const uploadDisabled = atLimit ? "disabled" : "";
  return `
    <div class="screenshot-manager">
      <div class="screenshot-strip">${thumbs}</div>
      <div class="screenshot-upload-row">
        <label class="screenshot-upload-label">
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
            ${uploadDisabled}
            onchange="uploadMyScreenshot('${escapeAttr(ownerKind)}', '${escapeAttr(ownerId)}', this)"
          />
          <span class="screenshot-upload-btn">${atLimit ? "Screenshot limit reached" : "Add screenshot"}</span>
        </label>
        <p class="meta">JPEG, PNG, or WebP · max ${SCREENSHOT_MAX_SIZE_LABEL} · up to ${MAX_SCREENSHOTS_PER_OWNER} per build. Screenshots need admin approval before they appear publicly.</p>
      </div>
    </div>`;
}

function renderMySubmissionItem(item) {
  const status = submissionStatusLabel(item);
  const statusClass = submissionStatusClass(item);
  const title = escapeHtml(item.displayName || "Untitled");
  const icon = item.iconUrl
    ? `<img class="submission-icon" src="${escapeAttr(item.iconUrl)}" alt="" onerror="this.outerHTML='<div class=\\'submission-icon submission-icon--placeholder\\' aria-hidden=\\'true\\'></div>';" />`
    : `<div class="submission-icon submission-icon--placeholder" aria-hidden="true"></div>`;

  let meta = "";
  if (item.kind === "approved") {
    meta = `<p class="meta">${escapeHtml(item.id)} · ${formatBytes(item.prefabBytes || 0)} · ${escapeHtml(formatDownloadCount(item.downloadCount))} · v${escapeHtml(item.version || "1")}</p>`;
  } else {
    meta = `<p class="meta">${escapeHtml(item.submissionId)}${item.proposedId ? ` · proposed id ${escapeHtml(item.proposedId)}` : ""}</p>`;
  }

  let action = "";
  if (item.kind === "pending") {
    action = `<button type="button" class="secondary" onclick="withdrawMySubmission('${escapeAttr(item.submissionId)}', '${escapeAttr(item.displayName || "")}')">Withdraw</button>`;
  } else if (item.kind === "approved") {
    action = `<button type="button" class="danger" onclick="removeMyBuilding('${escapeAttr(item.id)}', '${escapeAttr(item.displayName || "")}')">Remove from marketplace</button>`;
  } else if (item.kind === "rejected") {
    action = `<button type="button" class="secondary" onclick="dismissMySubmission('${escapeAttr(item.submissionId)}', '${escapeAttr(item.displayName || "")}')">Dismiss</button>`;
  }

  return `
    <div class="queue-item submission-item">
      ${icon}
      <div class="submission-body">
        <strong>${title}</strong>
        <p class="meta"><span class="submission-status ${statusClass}">${escapeHtml(status)}</span></p>
        ${meta}
        ${renderOwnerScreenshots(item)}
        ${action}
      </div>
    </div>`;
}

async function uploadMyScreenshot(ownerKind, ownerId, inputEl) {
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
  const endpoint =
    ownerKind === "approved"
      ? `/api/my-buildings/${encodeURIComponent(ownerId)}/screenshots`
      : `/api/my-submissions/${encodeURIComponent(ownerId)}/screenshots`;
  const form = new FormData();
  form.append("screenshot", file);
  const res = await fetch(endpoint, { method: "POST", body: form });
  const body = await res.json().catch(() => ({}));
  inputEl.value = "";
  if (!res.ok) {
    alert(body.message || body.error || "Upload failed");
    return;
  }
  loadDashboard();
}

async function deleteMyScreenshot(screenshotId) {
  if (!confirm("Remove this screenshot?")) {
    return;
  }
  const res = await fetch(`/api/my-screenshots/${encodeURIComponent(screenshotId)}`, { method: "DELETE" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert(body.error || "Delete failed");
    return;
  }
  loadDashboard();
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
  loadDashboard();
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
  loadDashboard();
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
  loadDashboard();
}

async function loadAdminPage() {
  const me = await fetch("/api/me").then((r) => r.json());
  if (!me.user) {
    window.location.href = "/auth/login";
    return;
  }
  await Promise.all([loadAdminQueue(), loadAdminScreenshotQueue(), loadAdminCatalog()]);
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
    const items = data.screenshots || [];
    if (!items.length) {
      el.innerHTML = emptyStateHtml("No screenshots waiting for review.");
      return;
    }
    el.innerHTML = items
      .map((s) => {
        const ownerKindLabel = s.ownerKind === "approved" ? "Published build" : "Pending submission";
        return `
    <div class="queue-item screenshot-queue-item">
      <a class="screenshot-queue-preview" href="${escapeAttr(s.imageUrl)}" target="_blank" rel="noopener">
        <img src="${escapeAttr(s.imageUrl)}" alt="Screenshot preview" />
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
      })
      .join("");
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
    const items = data.submissions || [];
    if (!items.length) {
      el.innerHTML = emptyStateHtml("Queue empty.");
      return;
    }
    el.innerHTML = items
      .map((s) => {
        const icon = s.iconUrl
          ? `<img class="submission-icon" src="${escapeAttr(s.iconUrl)}" alt="" onerror="this.outerHTML='<div class=\\'submission-icon submission-icon--placeholder\\' aria-hidden=\\'true\\'></div>';" />`
          : `<div class="submission-icon submission-icon--placeholder" aria-hidden="true"></div>`;
        const description = String(s.description || "").trim();
        const descriptionHtml = description
          ? `<p class="building-description building-description--pending">${escapeHtml(description)}</p>`
          : `<p class="meta">No description provided.</p>`;
        return `
    <div class="queue-item submission-item">
      ${icon}
      <div class="submission-body">
        <strong>${escapeHtml(s.displayName)}</strong>
        <p class="meta">${escapeHtml(s.submissionId)} by ${escapeHtml(s.creatorName)}</p>
        <p class="meta">Proposed id: ${escapeHtml(s.proposedId)}</p>
        ${descriptionHtml}
        <div class="queue-actions">
          <button onclick="approveSubmission('${escapeAttr(s.submissionId)}', '${escapeAttr(s.proposedId)}')">Approve</button>
          <button class="secondary" onclick="rejectSubmission('${escapeAttr(s.submissionId)}')">Reject</button>
        </div>
      </div>
    </div>`;
      })
      .join("");
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
  await fetch(`/api/admin/reject/${encodeURIComponent(submissionId)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason: "rejected" }),
  });
  loadAdminQueue();
}

async function loadAdminCatalog() {
  const el = document.getElementById("adminCatalog");
  if (!el) return;
  el.innerHTML = emptyStateHtml("Loading published buildings…");
  try {
    const data = await fetchCatalog();
    const entries = data.entries || [];
    if (!entries.length) {
      el.innerHTML = emptyStateHtml("No published buildings.");
      return;
    }
    el.innerHTML = entries
      .map((e) => renderBuildingCard(e, { canVote: false, adminDelete: true, showId: true }))
      .join("");
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
      for (const e of data.entries || []) {
        catalogEntriesById.set(e.id, e);
      }
      entry = catalogEntriesById.get(buildingId);
    } catch {
      entry = null;
    }
  }
  if (!entry) {
    alert("Could not load building details.");
    return;
  }

  content.innerHTML = `
    <div class="building-modal-header">
      ${buildingIconHtml(entry.iconUrl, "building-icon building-icon--modal")}
      <div>
        <h2 id="buildingDetailTitle">${escapeHtml(entry.displayName)}</h2>
        <p class="meta">by ${escapeHtml(entry.creatorName || "Unknown")}</p>
        <p class="meta building-modal-id"><code>${escapeHtml(entry.id)}</code></p>
        <p class="meta">${formatBytes(entry.prefabBytes || 0)} · ${escapeHtml(formatDownloadCount(entry.downloadCount))} · v${escapeHtml(entry.version)}</p>
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
            <img src="${escapeAttr(shot.url)}" alt="" />
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
