# Aetherhaven Community Marketplace

Backend API and website for browsing, submitting, and moderating community plot buildings.

## Quick start (local)

```bash
cd community-marketplace
cp .env.example .env
# Fill in SESSION_SECRET and OIDC vars as needed
npm install
npm run sync-hytale-assets
npm run playwright-install
npm start
```

Single server on **http://127.0.0.1:3847** (API + website).

### Private townsfolk submissions

Players open `/ah create-townsfolk` in game, fill out a character sheet and submit their current avatar. The website saves both JSON files and generates a transparent 128px portrait with the existing townsfolk framing (scale 0.5, yaw 19°, translation 0/-110). Admin → Townsfolk character sheets shows the sheet and provides all three downloads. The signed website session must belong to `ADMIN_HYTALE_UUIDS`; player UUID headers never grant access to these files.

The creator has separate point pools: attributes start at 5 with 5 extra points, and skills start at 1 with 10 extra points. Each increase costs one point; each decrease refunds one to the same pool. Attributes range from 1 to 8 and skills from 0 to 5 when creating a character. A skill at 0 can never be trained. Unspent points are allowed. Both the editor and upload validation enforce these rules; existing residents' progression limits are unchanged.

Before enabling this on a deployed website, run `npm run sync-hytale-assets` and upload the refreshed asset directory. Portraits need `Common/Characters`, **`Common/Cosmetics`** (clothing), `Common/TintGradients` and `Cosmetics/CharacterCreator/GradientSets.json`. To repair an older local bundle without clearing its existing viewer assets or catalogs, run `npm run sync-hytale-assets -- --portraits-only`. The asset verifier checks the gradient registry, gradient textures and cosmetic models as well as buildings. Use a source pack containing cosmetics used by your players; retain compatibility assets for older submitted appearances.

Merge the refreshed files into the server's existing `HYTALE_ASSETS_DIR`. Do not use Railway's `--overwrite` on `/data` or the whole asset folder: it replaces the destination. If the CLI refuses to merge a directory, upload a new archive and extract its asset-only contents into the existing tree. Rendering runs in a bounded worker queue and uses the existing `sharp` dependency. Missing assets do not discard submissions: the admin card names the missing asset, and **Retry portrait** works after that asset is installed.

Private records and portraits live under `DATA_DIR/private-townsfolk`, outside public manifests and download folders. Include that folder in backups. Admin cards provide **Delete**, followed by a confirmation, to permanently remove a submission and all three files. Deletion waits until any active portrait render finishes. To install an accepted character, put its character JSON under `Server/Aetherhaven/Townsfolk`, its model JSON under `Server/Models/Townsfolk`, and its portrait under `Common/Icons/ModelsGenerated`, preserving the download filenames. Submission only creates a private record; it does not add the character to live towns.

### Prefab 3D viewer assets

Building cards open a zoomable / rotatable Three.js prefab preview. That needs Hytale (+ Aetherhaven) textures and models on disk — **not committed to git** (Hypixel IP). Blocks from other mods are skipped in the viewer.

The generated `web/hytale-assets/catalog/` JSON **is** committed. It is only block metadata (ids, draw types, state model paths), and keeping it with the code means a deploy can never pair new viewer logic with an older catalog uploaded to the volume — that mismatch silently drops block states such as roof corners and fence connections.

#### Local

1. Make sure Hytale assets exist at  
   `../HytaleSourceCode/hytale-shared-source/HytaleAssets`  
   (or set `HYTALE_ASSETS_SRC` to your pack path).
2. From `community-marketplace/`:

   ```bash
   npm run sync-hytale-assets
   ```

   This writes `web/hytale-assets/` and serves it at `/hytale-assets`. Everything under
   `Common/` is gitignored; `catalog/` is committed, so commit it whenever it changes.
3. Optional, for auto cover screenshots on new submissions:

   ```bash
   npm run playwright-install
   ```

Re-run `sync-hytale-assets` after game updates or Aetherhaven asset changes.

#### Production (Railway)

The catalog rides along with the git deploy and the server always serves the committed
copy at `/hytale-assets/catalog`. Models and textures are not in the deploy, so they go on
the volume:

1. On your PC, export a copy:

   ```bash
   cd community-marketplace
   # Windows PowerShell example:
   $env:OUT_DIR="C:\temp\hytale-assets"; npm run sync-hytale-assets
   ```

   The run also refreshes the committed `web/hytale-assets/catalog/`, whatever `OUT_DIR` is.

2. Upload that folder to the Railway volume at `/data/hytale-assets`
   (Railway CLI, volume browser, or any SFTP/rsync you use).
3. Set service variable `HYTALE_ASSETS_DIR=/data/hytale-assets` and redeploy.

Step 2 is only needed when models or textures change, such as after a game update. New or
changed block **states** ship in the catalog, so commit and push is enough for those.

See [docs/RailwayDeployment.md](../docs/RailwayDeployment.md) for volume + Chromium notes. If Chromium or assets are missing, submissions still work — only auto-covers / 3D preview are affected.

## Wiki

Public mod wiki at **`/wiki.html`** (Town Journal guide mirror + Addons + Crossmod). Content lives under `web/wiki/` and is regenerated from the mod repo:

```bash
npm run sync-wiki
```

Sources:

- Guide topics: `../src/main/resources/Server/Aetherhaven/GuideTopics/en-US/`
- Guide images: `../src/main/resources/Common/UI/Custom/Aetherhaven/wiki/`
- Crossmod guide: `../tutorials/crossmod-integration.md`
- Website-only pages: `web/wiki/site-pages/`
- Optional sibling Addon GuideTopics (when present beside this repo):
  - `../Machinaria/.../GuideTopics/en-US/villager_mechanic.md`
  - `../CozyTales-Fishing/.../GuideTopics/en-US/villager_fisherman.md`

Re-run `sync-wiki` after guide or addon guide edits, then commit the updated `web/wiki/topics`, `images`, `nav.json`, and `search-index.json`.

The English journal tree is the source of truth for both navigation and content. Add new pages to a hub's `sub-topics` list so players can find them in both places. Workplace setup pages live under `mechanic_apprentice_workplaces`; core production pages live under `mechanic_production_buildings`. Easter-egg villager pages are excluded from the public sync. Run `node --test test/wikiGuides.test.js` after syncing to check the mirror, navigation, search and page rendering.

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
| `POST /api/v1/buildings/:id/download` | Record a first in-game install for this save or server (`X-Install-Instance-Id` required to increment; rate limited; returns `{ downloadCount }`) |
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

Catalog entries include `treasuryGoldCoinCost` when the building has a gold cost, and `requiredMods` (`[{ id, name }]`) when the prefab uses blocks/items from other mods. Entries are sorted by `upvoteCount` (descending), then display name. Signed-in users can upvote published buildings on the website; creators cannot upvote their own builds. Download counts increment on the first in-game install of a building on each save or dedicated server (not on preview, file GETs, updates, or remove-and-redownload). Creators edit published builds from **My Submissions**; admins can edit any pending or published submission from the admin page. Owner screenshots require admin approval before appearing in the public gallery; admin uploads are approved immediately. Uploaded screenshots are resized and stored as WebP (full max edge 1920px, card max edge 800px); marketplace covers and thumbnails use `?variant=card`. In-game, buildings with missing required mods are hidden from the Community tab.

Approve/reject via admin website (Hytale OAuth) or optional `POST /api/v1/submissions/:id/approve` with `API_KEY`.
