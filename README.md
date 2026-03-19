# Evoclaw — *Evolution of OpenClaw*

Monorepo with **OpenClaw v1–v4** under **`evoclaw/`**: LLM orchestrators with tools, task DAGs, browser automation (v4), and full debugging UI.

| Path | Port | Description |
|------|------|-------------|
| **`evoclaw/v1/backend`** | 8080 | OpenClaw 1 – single agent |
| **`evoclaw/v2/backend`** | 8081 | OpenClaw 2 – LLM task graph |
| **`evoclaw/v3/backend`** | 8082 | OpenClaw 3 – exec, memory, policies, sub-runs |
| **`evoclaw/v4/backend`** | 8083 | OpenClaw 4 – Playwright browser + live viewport |

See **`evoclaw/README.md`** for the version map and run commands.

## Docs

- **QUERIES.md** – Demo queries and how to run each version  
- **OPENCLAW_CONTEXT_AND_VISION.md** – Full context, roadmap (OpenClaw 5–10)  
- **OPENCLAW3_OPENCLAW4_PLAN.md** – Original v3/v4 build plan  

## API keys

Copy `.env.example` to `.env` and add your keys. **`.env` is gitignored** — never commit secrets.

```bash
source .env
./scripts/run-openclaw.sh evoclaw/v4/backend
```

## Author

[rishabhsingh7320](https://github.com/rishabhsingh7320)
