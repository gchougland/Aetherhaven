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
    return `<a class="upvote-btn" href="/auth/login" title="Sign in to upvote" aria-label="Sign in to upvote (${count} upvotes)"><span class="upvote-arrow" aria-hidden="true">▲</span><span class="upvote-count">${count}</span></a>`;
  }
  return `<button type="button" class="upvote-btn${active}" data-building-id="${escapeAttr(entry.id)}" onclick="toggleUpvote('${escapeAttr(entry.id)}', this)" aria-pressed="${entry.userHasUpvoted ? "true" : "false"}" aria-label="Upvote (${count})"><span class="upvote-arrow" aria-hidden="true">▲</span><span class="upvote-count">${count}</span></button>`;
}

function renderBuildingCard(entry, options = {}) {
  const { canVote = false, adminDelete = false, showId = false } = options;
  const deleteBtn = adminDelete
    ? `<button
        type="button"
        class="danger admin-delete-btn"
        onclick="deleteApprovedBuilding('${escapeAttr(entry.id)}', '${escapeAttr(entry.displayName)}')"
      >Remove permanently</button>`
    : "";
  const idMeta = showId ? `<p class="meta">${escapeHtml(entry.id)}</p>` : "";
  const cardClass = adminDelete ? "card building-card admin-building-card" : "card building-card";
  return `
    <article class="${cardClass}" data-building-id="${escapeAttr(entry.id)}">
      <div class="building-card-header">
        ${buildingIconHtml(entry.iconUrl)}
        ${upvoteControlHtml(entry, canVote)}
      </div>
      <h3>${escapeHtml(entry.displayName)}</h3>
      ${idMeta}
      <p class="meta">by ${escapeHtml(entry.creatorName || "Unknown")}</p>
      <p class="meta">${formatBytes(entry.prefabBytes || 0)} · v${escapeHtml(entry.version)}</p>
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
    if (!data.entries?.length) {
      el.innerHTML = emptyStateHtml("No approved buildings yet.");
      return;
    }
    el.innerHTML = data.entries.map((e) => renderBuildingCard(e, { canVote: Boolean(user) })).join("");
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

function renderMySubmissionItem(item) {
  const status = submissionStatusLabel(item);
  const statusClass = submissionStatusClass(item);
  const title = escapeHtml(item.displayName || "Untitled");
  const icon = item.iconUrl
    ? `<img class="submission-icon" src="${escapeAttr(item.iconUrl)}" alt="" onerror="this.outerHTML='<div class=\\'submission-icon submission-icon--placeholder\\' aria-hidden=\\'true\\'></div>';" />`
    : `<div class="submission-icon submission-icon--placeholder" aria-hidden="true"></div>`;

  let meta = "";
  if (item.kind === "approved") {
    meta = `<p class="meta">${escapeHtml(item.id)} · ${formatBytes(item.prefabBytes || 0)} · v${escapeHtml(item.version || "1")}</p>`;
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
        ${action}
      </div>
    </div>`;
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
  await Promise.all([loadAdminQueue(), loadAdminCatalog()]);
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
      .map(
        (s) => `
    <div class="queue-item">
      <strong>${escapeHtml(s.displayName)}</strong>
      <p class="meta">${escapeHtml(s.submissionId)} by ${escapeHtml(s.creatorName)}</p>
      <p class="meta">Proposed id: ${escapeHtml(s.proposedId)}</p>
      <button onclick="approveSubmission('${escapeAttr(s.submissionId)}', '${escapeAttr(s.proposedId)}')">Approve</button>
      <button class="secondary" onclick="rejectSubmission('${escapeAttr(s.submissionId)}')">Reject</button>
    </div>`
      )
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
