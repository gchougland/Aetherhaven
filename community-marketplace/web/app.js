async function refreshAuthNav() {
  const res = await fetch("/api/me");
  const { user } = await res.json();
  const login = document.getElementById("loginLink");
  const logout = document.getElementById("logoutLink");
  if (user) {
    if (login) login.hidden = true;
    if (logout) logout.hidden = false;
  }
}

function formatBytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

async function loadCatalog() {
  const el = document.getElementById("catalog");
  const status = document.getElementById("status");
  if (!el) return;
  try {
    const res = await fetch("/api/catalog");
    const data = await res.json();
    if (!data.entries?.length) {
      el.innerHTML = "<p class='meta'>No approved buildings yet.</p>";
      return;
    }
    el.innerHTML = data.entries
      .map(
        (e) => `
      <article class="card building-card">
        <img src="${e.iconUrl}" alt="" onerror="this.style.display='none'" />
        <h3>${escapeHtml(e.displayName)}</h3>
        <p class="meta">by ${escapeHtml(e.creatorName || "Unknown")}</p>
        <p class="meta">${formatBytes(e.prefabBytes || 0)} · v${escapeHtml(e.version)}</p>
      </article>`
      )
      .join("");
  } catch (e) {
    if (status) {
      status.hidden = false;
      status.textContent = "Could not load catalog.";
    }
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
  profile.innerHTML = `<h2>Signed in as ${escapeHtml(name)}</h2><p class="meta">Profile UUID: <code>${escapeHtml(uuid)}</code></p><p class="meta">Use this value for <code>ADMIN_HYTALE_UUIDS</code> on Railway if you are a moderator.</p>`;

  const pending = await fetch("/api/my-submissions").then((r) => r.json());
  const mine = (pending.submissions || []).filter((s) => s.creatorUuid === uuid);
  if (!mine.length) {
    list.innerHTML = "<p class='meta'>No pending submissions.</p>";
    return;
  }
  list.innerHTML = mine
    .map(
      (s) => `<div class="queue-item"><strong>${escapeHtml(s.displayName)}</strong>
      <p class="meta">${escapeHtml(s.submissionId)} · ${escapeHtml(s.status)}</p></div>`
    )
    .join("");
}

async function loadAdminQueue() {
  const el = document.getElementById("adminQueue");
  if (!el) return;
  const res = await fetch("/api/admin/pending");
  if (res.status === 403) {
    el.innerHTML = "<p>Admin access required. Set ADMIN_HYTALE_UUIDS in .env</p>";
    return;
  }
  const data = await res.json();
  const items = data.submissions || [];
  if (!items.length) {
    el.innerHTML = "<p class='meta'>Queue empty.</p>";
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
}

async function approveSubmission(submissionId, proposedId) {
  await fetch(`/api/admin/approve/${encodeURIComponent(submissionId)}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: proposedId }),
  });
  loadAdminQueue();
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
  const res = await fetch("/api/admin/catalog");
  if (res.status === 403) {
    el.innerHTML = "<p>Admin access required.</p>";
    return;
  }
  const data = await res.json();
  const entries = data.entries || [];
  if (!entries.length) {
    el.innerHTML = "<p class='meta'>No published buildings.</p>";
    return;
  }
  el.innerHTML = entries
    .map(
      (e) => `
    <div class="queue-item">
      <strong>${escapeHtml(e.displayName)}</strong>
      <p class="meta">${escapeHtml(e.id)} · by ${escapeHtml(e.creatorName || "Unknown")}</p>
      <button class="secondary" onclick="deleteApprovedBuilding('${escapeAttr(e.id)}')">Remove from marketplace</button>
    </div>`
    )
    .join("");
}

async function deleteApprovedBuilding(buildingId) {
  if (!confirm(`Remove "${buildingId}" from the public marketplace?`)) {
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
