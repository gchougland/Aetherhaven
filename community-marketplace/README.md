# Aetherhaven Community Marketplace

Backend API and website for browsing, submitting, and moderating community plot buildings.

## Quick start (local)

```bash
cd community-marketplace
cp .env.example .env
# Fill in SESSION_SECRET and OIDC vars as needed
npm install
npm start
```

Single server on **http://127.0.0.1:3847** (API + website).

## Wiki

Public mod wiki at **`/wiki.html`** (Town Journal guide mirror + Addons + Crossmod). Content lives under `web/wiki/` and is regenerated from the mod repo:

```bash
npm run sync-wiki
```

Sources:

- Guide topics: `../src/main/resources/Common/Docs/Hexvane_AetherhavenWiki/en-US/`
- Guide images: `../src/main/resources/Common/UI/Custom/Aetherhaven/wiki/`
- Crossmod guide: `../tutorials/crossmod-integration.md`
- Website-only pages: `web/wiki/site-pages/`
- Optional sibling Addon GuideTopics (when present beside this repo):
  - `../Machinaria/.../GuideTopics/en-US/villager_mechanic.md`
  - `../CozyTales-Fishing/.../GuideTopics/en-US/villager_fisherman.md`

Re-run `sync-wiki` after guide or addon guide edits, then commit the updated `web/wiki/topics`, `images`, `nav.json`, and `search-index.json`.

## Production (Railway)

See **[docs/RailwayDeployment.md](../docs/RailwayDeployment.md)** for full setup.

## Mod configuration (no secrets)

In `mods/Hexvane_Aetherhaven/config.json`:

```json
"CommunityMarketplace": {
  "Enabled": true,
  "ApiBaseUrl": "https://aetherhaven.net",
  "ManifestRefreshMinutes": 5
}
```

No API key is required on player machines or game servers. Submissions are rate-limited and require admin approval before appearing in the catalog.

## Discord notifications (optional)

Set webhook URLs in Railway Variables (or local `.env`):

| Variable | Channel | Fires when |
|----------|---------|------------|
| `DISCORD_PENDING_WEBHOOK_URL` | Admin-only | A new building is submitted for review (not version updates to already published builds) |
| `DISCORD_APPROVED_WEBHOOK_URL` | Public announcements | A building is approved for the first time (not re-approval after an update) |

Leave either unset to disable that notification. Webhook failures never block submit or approve.

## OAuth

See [docs/CommunityMarketplaceOAuthSetup.md](../docs/CommunityMarketplaceOAuthSetup.md).

## API

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/health` | Health check |
| `GET /api/v1/manifest` | Catalog metadata (sorted by upvote count; includes `upvoteCount`, `downloadCount`, `description`, `treasuryGoldCoinCost`, `requiredMods` when set) |
| `GET /api/catalog` | Website catalog (same as manifest; includes `userHasUpvoted` when signed in) |
| `GET /api/v1/buildings/:id/prefab.json` | Prefab download |
| `GET /api/v1/buildings/:id/building.json` | Building definition |
| `GET /api/v1/buildings/:id/icon.png` | Icon thumbnail |
| `POST /api/v1/buildings/:id/download` | Record an in-game install (rate limited; returns `{ downloadCount }`) |
| `POST /api/v1/submissions` | Upload (`X-Player-Uuid` from game server; rate limited) |
| `POST /api/buildings/:id/upvote` | Toggle upvote (Hytale OAuth session required; rate limited) |
| `GET /api/my-buildings/:id` | Owner edit payload for a published build (name, description, gold, materials, screenshots) |
| `PATCH /api/my-buildings/:id` | Owner update of published build metadata (applies immediately) |
| `POST /api/my-submissions/:id/screenshots` | Upload screenshot for own pending submission (JPEG/PNG/WebP input, max 5 MB; stored as resized WebP full + card, up to 6 per build) |
| `POST /api/my-buildings/:id/screenshots` | Upload screenshot for own published build |
| `DELETE /api/my-screenshots/:id` | Remove own screenshot |
| `GET /api/buildings/:id/screenshots` | List approved screenshots for a published build (`url` full, `cardUrl` with `?variant=card`) |
| `GET /api/buildings/:id/screenshots/:screenshotId` | Serve screenshot image (`?variant=card` for card size; falls back to full if missing) |
| `GET /api/admin/buildings/:id` | Admin edit payload for any published build |
| `PATCH /api/admin/buildings/:id` | Admin update of published build (includes styleId/tags) |
| `GET /api/admin/submissions/:id` | Admin edit payload for a pending submission |
| `PATCH /api/admin/submissions/:id` | Admin update of pending submission metadata |
| `POST /api/admin/buildings/:id/screenshots` | Admin upload screenshot for published build (auto-approved) |
| `POST /api/admin/submissions/:id/screenshots` | Admin upload screenshot for pending submission (auto-approved) |
| `DELETE /api/admin/screenshots/:id` | Admin delete any screenshot |
| `POST /api/admin/buildings/:id/cover` | Admin set/clear marketplace card cover |
| `GET /api/admin/screenshots/pending` | Admin screenshot review queue |

Catalog entries include `treasuryGoldCoinCost` when the building has a gold cost, and `requiredMods` (`[{ id, name }]`) when the prefab uses blocks/items from other mods. Entries are sorted by `upvoteCount` (descending), then display name. Signed-in users can upvote published buildings on the website; creators cannot upvote their own builds. Download counts increment when a player installs a building from the in-game Community tab (not on preview or file GETs alone). Creators edit published builds from **My Submissions**; admins can edit any pending or published submission from the admin page. Owner screenshots require admin approval before appearing in the public gallery; admin uploads are approved immediately. Uploaded screenshots are resized and stored as WebP (full max edge 1920px, card max edge 800px); marketplace covers and thumbnails use `?variant=card`. In-game, buildings with missing required mods are hidden from the Community tab.

Approve/reject via admin website (Hytale OAuth) or optional `POST /api/v1/submissions/:id/approve` with `API_KEY`.
