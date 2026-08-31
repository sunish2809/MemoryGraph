# Deploy MemoryGraph on Railway

Railway does **not** run `docker-compose.yml`. Create **four services** in one project from
this GitHub repo. Only **frontend** should have a public URL.

## If you see `Railpack could not determine how to build`

You connected GitHub to **one** service whose root is `/`. Railpack then looks at the
whole repo (`Backend/`, `Frontend/`, `faces/`) and finds no single language.

Do **not** add a `start.sh` at the repo root. Fix the service instead:

1. Open the failed service → **Settings**.
2. **Root Directory** = one of `Backend`, `Frontend`, or `faces` (not `/`).
3. **Builder** = **Dockerfile** (not Railpack). We already have a `Dockerfile` in each of those folders.
4. **Config as code** path (optional): `/Backend/railway.toml`, `/Frontend/railway.toml`, or `/faces/railway.toml`.

Then create the **other** services the same way. You need four services total, not one.

A “Deploy from repo” button that points at `sunish2809/MemoryGraph` with no root directory will always fail this way.

You need pgvector (for search embeddings). Railway’s one-click Postgres plugin is plain
Postgres — do **not** use it.

## Services

| Railway service name | Source | Public? | Volume mount |
| --- | --- | --- | --- |
| `postgres` | Docker image `pgvector/pgvector:pg17` | no | `/var/lib/postgresql/data` |
| `faces` | repo, root directory `faces` | no | `/models` |
| `backend` | repo, root directory `Backend` | no | `/var/lib/memorygraph/storage` |
| `frontend` | repo, root directory `Frontend` | **yes** | none |

Name them exactly like this so the private hostnames below match.

Give **faces** at least 2 GB RAM (4 GB is safer on first boot while models download).
Backend 1 GB, Postgres 512 MB, frontend 256 MB is enough for a handful of testers.

## 1. Postgres

1. New service → **Docker Image** → `pgvector/pgvector:pg17`
2. Variables:

   ```
   POSTGRES_DB=memorygraph
   POSTGRES_USER=memorygraph
   POSTGRES_PASSWORD=<generate a long random string>
   ```

3. Settings → Volume → mount `/var/lib/postgresql/data`
4. Do **not** generate a public domain.

## 2. Faces

1. New service from the GitHub repo, **Root Directory** = `faces`
2. Variables:

   ```
   HOST=::
   PORT=8090
   INSIGHTFACE_HOME=/models
   ```

3. Volume → `/models`
4. No public domain.

First deploy downloads InsightFace weights (several hundred MB) and can take a few minutes.

## 3. Backend

1. New service from the same repo, **Root Directory** = `Backend`
2. Volume → `/var/lib/memorygraph/storage` (photos live here; without it they vanish on redeploy)
3. Variables (Reference the other services where noted):

   ```
   SERVER_PORT=8080
   JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true
   DATABASE_URL=jdbc:postgresql://postgres.railway.internal:5432/memorygraph
   DATABASE_USERNAME=memorygraph
   DATABASE_PASSWORD=${{postgres.POSTGRES_PASSWORD}}
   JWT_SECRET=<openssl rand -base64 48>
   JWT_ISSUER=memorygraph
   JWT_ACCESS_TOKEN_TTL=PT168H
   REGISTRATION_INVITE_CODE=<openssl rand -base64 12>
   STORAGE_BACKEND=LOCAL
   STORAGE_LOCAL_ROOT=/var/lib/memorygraph/storage
   FACES_ENABLED=true
   FACE_SERVICE_URL=http://faces.railway.internal:8090
   PROCESSING_WORKER_THREADS=2
   AI_CHAT_PROVIDER=none
   AI_EMBEDDING_PROVIDER=none
   LOG_LEVEL_APP=INFO
   CORS_ALLOWED_ORIGINS=https://<your-frontend>.up.railway.app
   ```

   `${{postgres.POSTGRES_PASSWORD}}` is a Railway **reference variable**. After frontend has a
   domain, put that exact `https://…` origin in `CORS_ALLOWED_ORIGINS` (the browser only needs
   it for the Vite-style absolute API; same-origin `/api` via nginx is fine either way).

4. No public domain. Health check path: `/actuator/health/readiness`.

Leave `AI_*` on `none` unless you want to pay OpenAI for Ask/captions.

If testers will use Google Photos Picker, set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and
`GOOGLE_OAUTH_REDIRECT_URI=https://<frontend-domain>/import/google/callback` (same URI in
Google Cloud).

## 4. Frontend

1. New service from the repo, **Root Directory** = `Frontend`
2. Variables:

   ```
   BACKEND_HOST=backend.railway.internal
   BACKEND_PORT=8080
   DNS_RESOLVER=[fd12::10]
   ```

   Railway sets `PORT` itself. Do not override it.

3. **Generate a public domain** (Settings → Networking). That URL is what testers open.
4. Health check path: `/`

Redeploy backend after you know the public URL if you want `CORS_ALLOWED_ORIGINS` exact.

## Order

Deploy **postgres** first and wait until it is running, then **faces**, then **backend**, then
**frontend**. The app retries a bit, but Flyway needs a live database on boot.

## Invite testers

Send the frontend URL and `REGISTRATION_INVITE_CODE`. Do not share your own account.

## Cost (few users)

This is usage-based, not free. Four always-on services plus ~2–4 GB for faces is typically
**tens of dollars a month**, not $0. Turning `FACES_ENABLED=false` and skipping the faces
service saves the largest slice if you can live without auto face detect at first.

## Local Compose is unchanged

`docker compose up` still uses service names `backend` / `db` / `faces` and Docker DNS
`127.0.0.11`.
