# SMARTPREP

AI-based mock interview preparation portal.

## Stack

- **Frontend:** HTML, Tailwind CSS, JavaScript
- **Backend:** Java Spring Boot 3
- **Database:** PostgreSQL (Supabase) in production · H2 in-memory for local default · MySQL optional
- **AI:** NVIDIA NIM (OpenAI-compatible) with local coach fallback

## Project structure

```
Smartprep_Ai/
├── frontend/          # Static UI pages
├── backend/           # Spring Boot API
└── database/          # MySQL + Postgres schemas
```

## Quick start (local)

### Backend

```bash
cd backend
mvn spring-boot:run
```

API: `http://localhost:8080`  
Local DB is H2 in-memory (resets when the process stops). Console: `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:mem:smartprep` · user `sa` · empty password.

### Frontend

Open `frontend/index.html`, or:

```bash
cd frontend
npx --yes serve -p 5500
```

Set API URL in `frontend/js/config.js` if needed.

## Persistent database (production)

Sessions were disappearing because Render used **H2 in-memory**. Production should use **Supabase Postgres**.

### Supabase project

- Org: **ujjwaldubey1's Org**
- Project: **SmartPrep**
- Host: `db.pyxouvrwdejxwwogmgqk.supabase.co`
- Open: [Dashboard → SmartPrep](https://supabase.com/dashboard/project/pyxouvrwdejxwwogmgqk)
- Tables: `users`, `interview_sessions`, `questions`, `answers`, `evaluations`
- View data: **Table Editor** in that project

### Render environment variables

Set these on the backend service (Dashboard → Environment), then **redeploy**:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db.pyxouvrwdejxwwogmgqk.supabase.co:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password from Supabase → **Project Settings → Database** |
| `NVIDIA_API_KEY` | `nvapi-...` |
| `SMARTPREP_AI_ENABLED` | `true` |
| `SMARTPREP_AI_MODEL` | `openai/gpt-oss-20b` (or another live model) |
| `JWT_SECRET` | long random string |

If the direct host fails to connect from Render (IPv6), use the **Session pooler** URI from Supabase → **Connect**, with username `postgres.pyxouvrwdejxwwogmgqk`.

### Verify persistence

`https://YOUR-BACKEND.onrender.com/api/health` should include:

- `"profiles":["postgres"]`
- `"database":"PostgreSQL (persistent)"`

Then complete an interview and check rows in Supabase **Table Editor**.

## Deploy on Render (Docker)

1. Language / Runtime: **Docker**
2. Root Directory: leave empty (uses root `Dockerfile`)  
   **or** set Root Directory to `backend` (uses `backend/Dockerfile`)
3. Dockerfile Path: `Dockerfile`
4. Add the env vars above
5. Deploy (push the latest code that includes the Postgres driver)

Health check: `https://YOUR-SERVICE.onrender.com/api/health`

## NVIDIA AI on Render

| Variable | Example | Required |
|---|---|---|
| `NVIDIA_API_KEY` | `nvapi-...` | Yes |
| `SMARTPREP_AI_ENABLED` | `true` | Yes |
| `SMARTPREP_AI_BASE_URL` | `https://integrate.api.nvidia.com/v1` | Recommended |
| `SMARTPREP_AI_MODEL` | `openai/gpt-oss-20b` | Recommended |
| `SMARTPREP_AI_MAX_TOKENS` | `1024` | Optional |
| `JWT_SECRET` | long random string | Recommended for prod |

Optional aliases: `SMARTPREP_AI_API_KEY`, `OPENAI_API_KEY`.

### Point frontend at Render API

Edit `frontend/js/config.js`:

```js
window.SMARTPREP_API_BASE = "https://YOUR-BACKEND.onrender.com/api";
```

## Voice interview coach

The interview room can **speak questions**, capture answers with the **mic**, then explain **what was right / wrong** aloud.

Voice quality priority:
1. **ElevenLabs Sarah** (best) — set `ELEVENLABS_API_KEY` on Render  
2. **OpenAI Nova TTS** — set `OPENAI_API_KEY`  
3. **Browser neural voice** — free fallback (Chrome/Edge recommended)

Mic dictation works best in **Chrome or Edge** (allow microphone permission).

## Notes

- If the NVIDIA call fails, SMARTPREP falls back to the local interview coach (check Render logs for `Live AI chat failed`).
- Use an NVIDIA model id from [build.nvidia.com](https://build.nvidia.com/), not `gpt-4o-mini`.
- Without `SPRING_PROFILES_ACTIVE=postgres` + datasource env vars, history will reset whenever Render sleeps or redeploys.
