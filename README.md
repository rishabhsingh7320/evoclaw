# AI Code / OpenClaw

Monorepo with **OpenClaw** demos (v1–v4): LLM orchestrators with tools, task DAGs, browser automation (v4), and full debugging UI.

| Project | Port | Description |
|---------|------|-------------|
| `backend/` or `openclawdemo` | 8080 | OpenClaw 1 – single agent |
| `omenclawdemo2/` | 8081 | OpenClaw 2 – LLM task graph |
| `openclaw3/` | 8082 | OpenClaw 3 – exec, memory, policies, sub-runs |
| `openclaw4/` | 8083 | OpenClaw 4 – Playwright browser + live viewport |

## Docs

- **QUERIES.md** – Demo queries and how to run each version  
- **OPENCLAW_CONTEXT_AND_VISION.md** – Full context, roadmap (OpenClaw 5–10)  
- **OPENCLAW3_OPENCLAW4_PLAN.md** – Original v3/v4 build plan  

## API keys

Copy `.env.example` to `.env` and add your keys. **`.env` is gitignored** — never commit secrets.

```bash
source .env
./scripts/run-openclaw.sh openclaw4/backend
```

## Author

[rishabhsingh7320](https://github.com/rishabhsingh7320)
