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

## OAuth

See [docs/CommunityMarketplaceOAuthSetup.md](../docs/CommunityMarketplaceOAuthSetup.md).

## API

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/health` | Health check |
| `GET /api/v1/manifest` | Catalog metadata (sorted by upvote count; includes `upvoteCount`, `downloadCount`, `description` when set) |
| `GET /api/catalog` | Website catalog (same as manifest; includes `userHasUpvoted` when signed in) |
| `GET /api/v1/buildings/:id/prefab.json` | Prefab download |
| `GET /api/v1/buildings/:id/building.json` | Building definition |
| `GET /api/v1/buildings/:id/icon.png` | Icon thumbnail |
| `POST /api/v1/buildings/:id/download` | Record an in-game install (rate limited; returns `{ downloadCount }`) |
| `POST /api/v1/submissions` | Upload (`X-Player-Uuid` from game server; rate limited) |
| `POST /api/buildings/:id/upvote` | Toggle upvote (Hytale OAuth session required; rate limited) |

Catalog entries are sorted by `upvoteCount` (descending), then display name. Signed-in users can upvote published buildings on the website; creators cannot upvote their own builds. Download counts increment when a player installs a building from the in-game Community tab (not on preview or file GETs alone).

Approve/reject via admin website (Hytale OAuth) or optional `POST /api/v1/submissions/:id/approve` with `API_KEY`.
