# Aetherhaven Community Marketplace

Backend API and website for browsing, submitting, and moderating community plot buildings.

## Quick start (local)

```bash
cd community-marketplace
cp .env.example .env
# Fill in API_KEY, SESSION_SECRET, and OIDC vars as needed
npm install
npm start
```

Single server on **http://127.0.0.1:3847** (API + website).

## Production (Railway)

See **[docs/RailwayDeployment.md](../docs/RailwayDeployment.md)** for full setup: volume, variables, OAuth, and game server configuration.

## Mod configuration

In `mods/Hexvane_Aetherhaven/config.json` — **no secrets**:

```json
"CommunityMarketplace": {
  "Enabled": true,
  "ApiBaseUrl": "https://your-app.up.railway.app",
  "ManifestRefreshMinutes": 5
}
```

### API key (game server environment — not config.json)

```bash
export AETHERHAVEN_COMMUNITY_API_KEY="<same value as Railway API_KEY>"
```

Or point at a secret file:

```bash
export AETHERHAVEN_COMMUNITY_API_KEY_FILE="/path/to/secret/file"
```

## OAuth

See [docs/CommunityMarketplaceOAuthSetup.md](../docs/CommunityMarketplaceOAuthSetup.md).

## API (mod / server)

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/health` | Health check |
| `GET /api/v1/manifest` | Lightweight catalog metadata |
| `GET /api/v1/buildings/:id/prefab.json` | Prefab download |
| `GET /api/v1/buildings/:id/building.json` | Building definition |
| `GET /api/v1/buildings/:id/icon.png` | Icon thumbnail |
| `POST /api/v1/submissions` | Upload (requires `X-Api-Key`, `X-Player-Uuid`) |

Approve/reject via admin website or `POST /api/v1/submissions/:id/approve` with API key.
