const section = document.querySelector("#adminTownsfolkSection");
if (section) {
  const list = section.querySelector("[data-townsfolk-list]"), status = section.querySelector("[data-townsfolk-status]");
  const search = section.querySelector("input"), refresh = section.querySelector("[data-townsfolk-refresh]");
  let entries = [];
  const title = value => String(value).replaceAll("_", " ").replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^./, c => c.toUpperCase());
  function element(tag, className, text) {
    const node = document.createElement(tag); node.className = className;
    if (text != null) node.textContent = text; return node;
  }
  function scores(values, className) {
    const group = element("dl", className);
    for (const [key, value] of Object.entries(values || {})) {
      const stat = element("div", "townsfolk-stat");
      stat.append(element("dt", "", title(key)), element("dd", "", String(value)));
      if (value === 0) stat.append(element("span", "townsfolk-untrained", "Cannot learn"));
      group.append(stat);
    }
    return group;
  }
  function card(entry) {
    const c = entry.character, article = element("article", "townsfolk-sheet");
    const url = `/api/admin/townsfolk/${encodeURIComponent(entry.id)}`;
    const header = element("header", "townsfolk-heading"), portrait = element("div", "townsfolk-portrait");
    if (entry.portraitStatus === "ready") {
      const img = document.createElement("img"); img.src = `${url}/files/portrait`; img.alt = `${c.displayName}'s portrait`; img.width = 128; img.height = 128;
      portrait.append(img);
    } else portrait.append(element("span", "", entry.portraitStatus === "rendering" ? "Portrait in progress" : "Portrait unavailable"));
    const identity = element("div", "townsfolk-identity");
    identity.append(element("span", "townsfolk-eyebrow", "PRIVATE CHARACTER SUBMISSION"), element("h3", "", c.displayName),
      element("p", "", `${title(c.gender)} · ${title(c.race)} · ${title(c.speechVoiceId)}`),
      element("p", "townsfolk-credit", `Submitted by ${entry.creatorName} · ${new Date(entry.submittedAt).toLocaleDateString()}`));
    const traits = element("div", "townsfolk-traits");
    for (const trait of c.personalityIds) traits.append(element("span", "", title(trait)));
    identity.append(traits); header.append(portrait, identity); article.append(header);
    article.append(element("h4", "", "Attributes"), scores(c.startingAttributes, "townsfolk-attributes"));
    article.append(element("h4", "", "Profession skills"), scores(c.startingSkills, "townsfolk-skills"));
    article.append(element("h4", "", "Move-in gifts"));
    const items = element("ul", "townsfolk-gifts");
    for (const item of c.moveInRequirements) items.append(element("li", "", `${item.count} × ${title(item.itemId)}`));
    article.append(items);
    const downloads = element("footer", "townsfolk-downloads");
    for (const [kind, label] of [["townsfolk", "Character JSON"], ["model", "Model JSON"], ["portrait", "Portrait PNG"]]) {
      if (kind === "portrait" && entry.portraitStatus !== "ready") continue;
      const link = element("a", "btn", label); link.href = `${url}/files/${kind}`; link.download = ""; downloads.append(link);
    }
    if (entry.portraitStatus !== "ready") {
      const retry = element("button", "btn", entry.portraitStatus === "rendering" ? "Rendering portrait..." : "Retry portrait");
      retry.type = "button"; retry.disabled = entry.portraitStatus === "rendering";
      retry.addEventListener("click", async () => {
        retry.disabled = true; retry.textContent = "Rendering portrait...";
        try {
          const response = await fetch(`${url}/portrait`, { method: "POST", credentials: "same-origin" });
          if (!response.ok || (await response.json()).portraitStatus !== "ready") throw new Error("Portrait could not be created. Check the server's synced character assets, then retry.");
          await load();
        } catch (e) { retry.disabled = false; retry.textContent = "Retry portrait"; status.textContent = e.message; }
      });
      downloads.append(retry);
    }
    article.append(downloads); return article;
  }
  function render() {
    const query = search.value.trim().toLocaleLowerCase();
    const filtered = entries.filter(e => `${e.character.displayName} ${e.creatorName} ${e.character.race} ${e.character.personalityIds.join(" ")}`.toLocaleLowerCase().includes(query));
    list.replaceChildren(...filtered.map(card));
    status.textContent = `${filtered.length} of ${entries.length} private submissions`;
    if (!filtered.length) list.append(element("p", "townsfolk-empty", entries.length ? "No characters match your search." : "No characters submitted yet. Players can use /ah create-townsfolk in game."));
  }
  async function load() {
    refresh.disabled = true; status.textContent = "Loading private characters...";
    try {
      const response = await fetch("/api/admin/townsfolk", { credentials: "same-origin", cache: "no-store" });
      if (!response.ok) throw new Error(response.status === 401 || response.status === 403 ? "Sign in with an admin account to view character submissions." : "Could not load submissions. Please refresh to retry.");
      entries = (await response.json()).submissions || []; render();
      const count = document.querySelector("[data-townsfolk-count]"); if (count) count.textContent = entries.length;
    } catch (e) { entries = []; list.replaceChildren(); status.textContent = e.message; }
    finally { refresh.disabled = false; }
  }
  search.addEventListener("input", render); refresh.addEventListener("click", load);
  section.addEventListener("toggle", () => { if (section.open) void load(); });
  document.querySelector('a[href="#adminTownsfolkSection"]')?.addEventListener("click", () => { section.open = true; });
  if (section.open) void load();
}
