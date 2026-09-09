# SMARTPREP

AI-based mock interview preparation portal.

## Stack

- **Frontend:** HTML, Tailwind CSS, JavaScript
- **Backend:** Java Spring Boot 3
- **Database:** MySQL (H2 for local default)
- **AI:** NVIDIA NIM (OpenAI-compatible) with local coach fallback

## Project structure

```
Smartprep_Ai/
├── frontend/          # Static UI pages
├── backend/           # Spring Boot API
└── database/          # MySQL schema
```

## Quick start (local)

### Backend

```bash
cd backend
mvn spring-boot:run
```

API: `http://localhost:8080`

### Frontend

Open `frontend/index.html`, or:

```bash
cd frontend
npx --yes serve -p 5500
```

Set API URL in `frontend/js/config.js` if needed.

## NVIDIA AI on Render

Set these **Environment Variables** on your Render backend service:

| Variable | Example | Required |
|---|---|---|
| `NVIDIA_API_KEY` | `nvapi-...` | Yes |
| `SMARTPREP_AI_ENABLED` | `true` | Yes |
| `SMARTPREP_AI_BASE_URL` | `https://integrate.api.nvidia.com/v1` | Recommended |
| `SMARTPREP_AI_MODEL` | `meta/llama-3.1-70b-instruct` | Recommended |
| `SMARTPREP_AI_MAX_TOKENS` | `1024` | Optional |
| `JWT_SECRET` | long random string | Recommended for prod |

Optional aliases also supported: `SMARTPREP_AI_API_KEY`, `OPENAI_API_KEY`.

### Verify AI is live

After deploy, open:

`https://YOUR-BACKEND.onrender.com/api/health`

You should see `"aiEnabled": true`.

### Point frontend at Render API

Edit `frontend/js/config.js`:

```js
window.SMARTPREP_API_BASE = "https://YOUR-BACKEND.onrender.com/api";
```

## Notes

- If the NVIDIA call fails, SMARTPREP falls back to the local interview coach (check Render logs for `Live AI chat failed`).
- Use an NVIDIA model id from [build.nvidia.com](https://build.nvidia.com/), not `gpt-4o-mini`.
