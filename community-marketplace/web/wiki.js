/* global marked, refreshAuthNav, escapeHtml, escapeAttr */

(function () {
  const DEFAULT_TOPIC = "welcome";

  /** @type {{ tree: any[], flat: {id:string,name:string,depth:number}[], titleMap: Record<string,string>, portraitRemap: Record<string,string> } | null} */
  let navData = null;
  /** @type {{ id: string, name: string, description: string, text: string }[] | null} */
  let searchIndex = null;
  let currentTopicId = DEFAULT_TOPIC;

  function topicFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const t = (params.get("topic") || "").trim();
    return t || DEFAULT_TOPIC;
  }

  function topicUrl(id) {
    return `/wiki.html?topic=${encodeURIComponent(id)}`;
  }

  function stripFrontmatter(raw) {
    const text = raw.replace(/\r\n/g, "\n");
    if (!text.startsWith("---\n")) return text;
    const end = text.indexOf("\n---\n", 4);
    if (end < 0) return text;
    return text.slice(end + 5).trim();
  }

  function rewriteMarkdownImages(md) {
    const remap = navData?.portraitRemap || {};
    return md.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (full, alt, src) => {
      const trimmed = src.trim();
      if (remap[trimmed]) {
        return `![${alt}](${remap[trimmed]})`;
      }
      if (trimmed.startsWith("wiki/")) {
        return `![${alt}](/wiki/images/${trimmed.slice(5)})`;
      }
      if (trimmed.startsWith("/wiki/")) {
        return full;
      }
      return full;
    });
  }

  function rewriteMarkdownLinks(md) {
    // [?topic=id] already valid; normalize (topic=id) relative links
    return md.replace(/\[([^\]]+)\]\(\?topic=([^)]+)\)/g, (_, label, id) => {
      return `[${label}](${topicUrl(id.trim())})`;
    });
  }

  function slugifyHeading(text) {
    return text
      .toLowerCase()
      .replace(/[^\w\s-]/g, "")
      .trim()
      .replace(/\s+/g, "-")
      .slice(0, 80);
  }

  function renderMarkdown(md) {
    const prepared = rewriteMarkdownLinks(rewriteMarkdownImages(stripFrontmatter(md)));
    if (typeof marked === "undefined") {
      return `<pre>${escapeHtml(prepared)}</pre>`;
    }
    marked.setOptions({ gfm: true, breaks: false });
    return marked.parse(prepared);
  }

  function findAncestors(tree, targetId, path = []) {
    for (const node of tree) {
      const next = path.concat(node);
      if (node.id === targetId) return next;
      const found = findAncestors(node.children || [], targetId, next);
      if (found) return found;
    }
    return null;
  }

  function collectIdsWithQuery(nodes, q, out = new Set()) {
    const lower = q.toLowerCase();
    for (const n of nodes) {
      const match = n.name.toLowerCase().includes(lower);
      let childMatch = false;
      if (n.children?.length) {
        const before = out.size;
        collectIdsWithQuery(n.children, q, out);
        childMatch = out.size > before;
      }
      if (match || childMatch) {
        out.add(n.id);
        if (match && n.children?.length) {
          // Show full subtree when a hub title matches
          const stack = [...n.children];
          while (stack.length) {
            const c = stack.pop();
            out.add(c.id);
            if (c.children?.length) stack.push(...c.children);
          }
        }
      }
    }
    return out;
  }

  function ancestorsToExpand(tree, targetId) {
    const path = findAncestors(tree, targetId) || [];
    return new Set(path.map((n) => n.id));
  }

  function renderDirectory(filterQuery = "") {
    const root = document.getElementById("wikiDirectory");
    if (!root || !navData) return;
    const expand = ancestorsToExpand(navData.tree, currentTopicId);
    let visible = null;
    if (filterQuery.trim()) {
      visible = collectIdsWithQuery(navData.tree, filterQuery.trim());
      // Also expand matches
      for (const id of visible) {
        const path = findAncestors(navData.tree, id) || [];
        for (const n of path) expand.add(n.id);
      }
    }

    function shouldShow(node) {
      if (!visible) return true;
      return visible.has(node.id);
    }

    function renderNode(node) {
      if (!shouldShow(node)) return "";
      const hasKids = node.children && node.children.length > 0;
      const isOpen = expand.has(node.id) || Boolean(filterQuery.trim());
      const isCurrent = node.id === currentTopicId;
      const kidsHtml = hasKids
        ? `<ul class="wiki-dir-children"${isOpen ? "" : " hidden"}>${node.children
            .map(renderNode)
            .join("")}</ul>`
        : "";
      const toggle = hasKids
        ? `<button type="button" class="wiki-dir-toggle" aria-expanded="${isOpen}" data-toggle="${escapeAttr(
            node.id
          )}" aria-label="Expand ${escapeAttr(node.name)}">${isOpen ? "▾" : "▸"}</button>`
        : `<span class="wiki-dir-toggle-spacer" aria-hidden="true"></span>`;
      return `<li class="wiki-dir-item" data-id="${escapeAttr(node.id)}">
        <div class="wiki-dir-row">
          ${toggle}
          <a href="${topicUrl(node.id)}" class="wiki-dir-link"${
        isCurrent ? ' aria-current="page"' : ""
      } data-topic="${escapeAttr(node.id)}">${escapeHtml(node.name)}</a>
        </div>
        ${kidsHtml}
      </li>`;
    }

    root.innerHTML = `<ul class="wiki-dir-root">${navData.tree.map(renderNode).join("")}</ul>`;
  }

  function renderBreadcrumbs() {
    const el = document.getElementById("wikiBreadcrumbs");
    if (!el || !navData) return;
    const path = findAncestors(navData.tree, currentTopicId) || [];
    const crumbs = [{ id: null, name: "Wiki" }].concat(path);
    el.innerHTML = crumbs
      .map((c, i) => {
        const last = i === crumbs.length - 1;
        if (last || !c.id) {
          return `<span class="wiki-crumb${last ? " wiki-crumb--current" : ""}">${escapeHtml(
            c.name
          )}</span>`;
        }
        return `<a class="wiki-crumb" href="${topicUrl(c.id)}">${escapeHtml(c.name)}</a>`;
      })
      .join('<span class="wiki-crumb-sep" aria-hidden="true">›</span>');
  }

  function renderPrevNext() {
    const el = document.getElementById("wikiPrevNext");
    if (!el || !navData) return;
    const flat = navData.flat;
    const idx = flat.findIndex((e) => e.id === currentTopicId);
    if (idx < 0) {
      el.innerHTML = "";
      return;
    }
    const prev = idx > 0 ? flat[idx - 1] : null;
    const next = idx < flat.length - 1 ? flat[idx + 1] : null;
    el.innerHTML = `
      <div class="wiki-prev-next-inner">
        ${
          prev
            ? `<a class="wiki-prev" href="${topicUrl(prev.id)}"><span class="meta">Previous</span><strong>${escapeHtml(
                prev.name
              )}</strong></a>`
            : `<span></span>`
        }
        ${
          next
            ? `<a class="wiki-next" href="${topicUrl(next.id)}"><span class="meta">Next</span><strong>${escapeHtml(
                next.name
              )}</strong></a>`
            : `<span></span>`
        }
      </div>`;
  }

  function buildPageToc(article) {
    const toc = document.getElementById("wikiPageToc");
    if (!toc) return;
    const headings = [...article.querySelectorAll("h2, h3")];
    if (headings.length < 2) {
      toc.hidden = true;
      toc.innerHTML = "";
      return;
    }
    const used = new Set();
    const items = headings.map((h) => {
      let id = h.id || slugifyHeading(h.textContent || "section");
      let base = id;
      let n = 2;
      while (used.has(id)) {
        id = `${base}-${n++}`;
      }
      used.add(id);
      h.id = id;
      const level = h.tagName === "H3" ? 3 : 2;
      return `<li class="wiki-toc-item wiki-toc-item--h${level}"><a href="#${escapeAttr(
        id
      )}">${escapeHtml(h.textContent || "")}</a></li>`;
    });
    toc.hidden = false;
    toc.innerHTML = `<h2 class="wiki-toc-title">On this page</h2><ul class="wiki-toc-list">${items.join(
      ""
    )}</ul>`;
  }

  function autoLinkArticle(article) {
    if (!navData?.titleMap) return;
    // Longest titles first to prefer specific matches
    const entries = Object.entries(navData.titleMap)
      .filter(([title, id]) => title.length >= 4 && id !== currentTopicId)
      .sort((a, b) => b[0].length - a[0].length);

    const walker = document.createTreeWalker(article, NodeFilter.SHOW_ELEMENT, {
      acceptNode(node) {
        const tag = node.tagName;
        if (tag === "A" || tag === "CODE" || tag === "PRE" || tag === "STRONG" || tag === "SCRIPT") {
          return NodeFilter.FILTER_REJECT;
        }
        return NodeFilter.FILTER_SKIP;
      },
    });

    // Convert matching <strong> text to links
    for (const strong of [...article.querySelectorAll("strong")]) {
      if (strong.closest("a")) continue;
      const text = (strong.textContent || "").trim();
      const id = navData.titleMap[text];
      if (!id || id === currentTopicId) continue;
      const a = document.createElement("a");
      a.href = topicUrl(id);
      a.className = "wiki-autolink";
      a.textContent = text;
      strong.replaceWith(a);
    }

    // Also link plain title mentions in paragraph text nodes (conservative: whole-word only for exact titleMap keys)
    void entries;
    void walker;
  }

  function softFailImages(article) {
    for (const img of article.querySelectorAll("img")) {
      img.addEventListener("error", () => {
        img.style.display = "none";
      });
      img.loading = "lazy";
    }
  }

  async function loadTopic(id, { push = false } = {}) {
    currentTopicId = id;
    if (push) {
      history.pushState({ topic: id }, "", topicUrl(id));
    }

    const article = document.getElementById("wikiArticle");
    if (!article) return;
    article.innerHTML = `<p class="meta">Loading…</p>`;

    renderDirectory(document.getElementById("wikiSearch")?.value || "");
    renderBreadcrumbs();
    renderPrevNext();

    try {
      const res = await fetch(`/wiki/topics/${encodeURIComponent(id)}.md`);
      if (!res.ok) throw new Error(`Topic not found (${res.status})`);
      const raw = await res.text();
      const meta = navData?.flat.find((e) => e.id === id);
      document.title = `${meta?.name || id} — Aetherhaven Wiki`;
      article.innerHTML = renderMarkdown(raw);
      autoLinkArticle(article);
      softFailImages(article);
      buildPageToc(article);
      window.scrollTo(0, 0);
    } catch (err) {
      document.title = "Not found — Aetherhaven Wiki";
      article.innerHTML = `<h1>Page not found</h1><p class="meta">${escapeHtml(
        err.message || String(err)
      )}</p><p><a href="${topicUrl(DEFAULT_TOPIC)}">Back to Welcome</a></p>`;
      document.getElementById("wikiPageToc").hidden = true;
    }
  }

  function runSearch(query) {
    const box = document.getElementById("wikiSearchResults");
    if (!box) return;
    const q = query.trim().toLowerCase();
    if (!q || !searchIndex) {
      box.hidden = true;
      box.innerHTML = "";
      renderDirectory(query);
      return;
    }

    const hits = [];
    for (const item of searchIndex) {
      const hay = `${item.name} ${item.description} ${item.text}`.toLowerCase();
      const idx = hay.indexOf(q);
      if (idx < 0) continue;
      let snippet = item.description || "";
      if (!snippet && item.text) {
        const start = Math.max(0, item.text.toLowerCase().indexOf(q) - 40);
        snippet = (start > 0 ? "…" : "") + item.text.slice(start, start + 120) + "…";
      }
      const score =
        item.name.toLowerCase().includes(q) ? 0 : item.description.toLowerCase().includes(q) ? 1 : 2;
      hits.push({ item, snippet, score });
    }
    hits.sort((a, b) => a.score - b.score || a.item.name.localeCompare(b.item.name));
    const top = hits.slice(0, 12);

    renderDirectory(query);

    if (!top.length) {
      box.hidden = false;
      box.innerHTML = `<p class="wiki-search-empty meta">No matching pages.</p>`;
      return;
    }

    box.hidden = false;
    box.innerHTML = `<ul class="wiki-search-list">${top
      .map(
        (h) => `<li>
        <a href="${topicUrl(h.item.id)}" data-topic="${escapeAttr(h.item.id)}">
          <strong>${escapeHtml(h.item.name)}</strong>
          <span class="meta">${escapeHtml(h.snippet)}</span>
        </a>
      </li>`
      )
      .join("")}</ul>`;
  }

  function wireEvents() {
    document.getElementById("wikiDirectory")?.addEventListener("click", (e) => {
      const toggle = e.target.closest("[data-toggle]");
      if (toggle) {
        e.preventDefault();
        const li = toggle.closest(".wiki-dir-item");
        const kids = li?.querySelector(":scope > .wiki-dir-children");
        if (!kids) return;
        const open = kids.hasAttribute("hidden");
        if (open) kids.removeAttribute("hidden");
        else kids.setAttribute("hidden", "");
        toggle.setAttribute("aria-expanded", open ? "true" : "false");
        toggle.textContent = open ? "▾" : "▸";
        return;
      }
      const link = e.target.closest("a[data-topic]");
      if (link) {
        e.preventDefault();
        loadTopic(link.getAttribute("data-topic"), { push: true });
      }
    });

    document.getElementById("wikiSearchResults")?.addEventListener("click", (e) => {
      const link = e.target.closest("a[data-topic]");
      if (link) {
        e.preventDefault();
        document.getElementById("wikiSearch").value = "";
        document.getElementById("wikiSearchResults").hidden = true;
        loadTopic(link.getAttribute("data-topic"), { push: true });
      }
    });

    document.getElementById("wikiPrevNext")?.addEventListener("click", (e) => {
      const link = e.target.closest("a[href*='topic=']");
      if (!link) return;
      e.preventDefault();
      const u = new URL(link.href, window.location.origin);
      loadTopic(u.searchParams.get("topic") || DEFAULT_TOPIC, { push: true });
    });

    document.getElementById("wikiBreadcrumbs")?.addEventListener("click", (e) => {
      const link = e.target.closest("a[href*='topic=']");
      if (!link) return;
      e.preventDefault();
      const u = new URL(link.href, window.location.origin);
      loadTopic(u.searchParams.get("topic") || DEFAULT_TOPIC, { push: true });
    });

    document.getElementById("wikiArticle")?.addEventListener("click", (e) => {
      const link = e.target.closest("a[href*='wiki.html?topic=']");
      if (!link) return;
      e.preventDefault();
      const u = new URL(link.href, window.location.origin);
      loadTopic(u.searchParams.get("topic") || DEFAULT_TOPIC, { push: true });
    });

    const search = document.getElementById("wikiSearch");
    let searchTimer = null;
    search?.addEventListener("input", () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => runSearch(search.value), 120);
    });
    search?.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        search.value = "";
        runSearch("");
        search.blur();
      }
      if (e.key === "Enter") {
        e.preventDefault();
        const first = document.querySelector("#wikiSearchResults a[data-topic]");
        if (first) {
          loadTopic(first.getAttribute("data-topic"), { push: true });
          search.value = "";
          runSearch("");
        }
      }
    });

    document.addEventListener("keydown", (e) => {
      if (e.key !== "/" || e.ctrlKey || e.metaKey || e.altKey) return;
      const tag = (e.target && e.target.tagName) || "";
      if (tag === "INPUT" || tag === "TEXTAREA" || e.target?.isContentEditable) return;
      e.preventDefault();
      search?.focus();
      search?.select();
    });

    document.getElementById("wikiCopyLink")?.addEventListener("click", async () => {
      const url = window.location.href;
      const btn = document.getElementById("wikiCopyLink");
      try {
        await navigator.clipboard.writeText(url);
        if (btn) {
          const prev = btn.textContent;
          btn.textContent = "Copied";
          setTimeout(() => {
            btn.textContent = prev;
          }, 1500);
        }
      } catch {
        window.prompt("Copy this link:", url);
      }
    });

    const back = document.getElementById("wikiBackToTop");
    window.addEventListener("scroll", () => {
      if (!back) return;
      back.hidden = window.scrollY < 400;
    });
    back?.addEventListener("click", () => window.scrollTo({ top: 0, behavior: "smooth" }));

    window.addEventListener("popstate", () => {
      loadTopic(topicFromUrl(), { push: false });
    });
  }

  async function boot() {
    if (typeof refreshAuthNav === "function") refreshAuthNav();
    wireEvents();
    try {
      const [navRes, searchRes] = await Promise.all([
        fetch("/wiki/nav.json"),
        fetch("/wiki/search-index.json"),
      ]);
      if (!navRes.ok) throw new Error("Failed to load wiki navigation");
      navData = await navRes.json();
      searchIndex = searchRes.ok ? await searchRes.json() : [];
      await loadTopic(topicFromUrl(), { push: false });
    } catch (err) {
      const article = document.getElementById("wikiArticle");
      if (article) {
        article.innerHTML = `<h1>Wiki unavailable</h1><p class="meta">${escapeHtml(
          err.message || String(err)
        )}</p>`;
      }
    }
  }

  boot();
})();
