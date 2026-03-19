# Evoclaw — *Evolution of OpenClaw*

**Evoclaw** is the umbrella project for four generations of the OpenClaw LLM orchestrator demo. Each version lives in its own folder so you can run, compare, and extend them side by side.

| Folder | Version | Port | Codename / focus |
|--------|---------|------|------------------|
| **`v1/`** | OpenClaw 1 | **8080** | Single manager agent — mock search, math, file |
| **`v2/`** | OpenClaw 2 | **8081** | LLM task DAG — Tavily search, file, math, http |
| **`v3/`** | OpenClaw 3 | **8082** | Exec, memory, tool policies, hooks, compaction, sub-runs |
| **`v4/`** | OpenClaw 4 | **8083** | Everything in v3 + **Playwright browser** + live viewport UI |

## Run (from repo root)

```bash
source .env   # OPENAI_API_KEY, TAVILY_API_KEY

cd evoclaw/v1/backend && mvn spring-boot:run   # 8080
cd evoclaw/v2/backend && mvn spring-boot:run   # 8081
cd evoclaw/v3/backend && mvn spring-boot:run   # 8082
cd evoclaw/v4/backend && mvn spring-boot:run   # 8083
```

Or use the helper script:

```bash
./scripts/run-openclaw.sh evoclaw/v3/backend
./scripts/run-openclaw.sh evoclaw/v4/backend
```

**OpenClaw 4:** install Chromium once:

```bash
mvn -f evoclaw/v4/backend exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

## Docs at repo root

- **QUERIES.md** — demo queries per version  
- **OPENCLAW_CONTEXT_AND_VISION.md** — full context & roadmap (v5–v10)  
- **OPENCLAW3_OPENCLAW4_PLAN.md** — original v3/v4 build plan  

---

*Evoclaw: from one agent to a full browser-capable orchestrator — one repo, four evolutions.*
