# Evoclaw — *Evolution of OpenClaw*

**Evoclaw** is a staged research and demo codebase: four versions of the same idea—**an LLM-driven orchestrator that uses tools to complete real work**—each version adding capabilities so you can see *why* the next layer matters.

---

## Complete objective of this project

The **end goal** is to explore and demonstrate a **capable, observable AI orchestrator** that can:

1. **Plan structured work** — Break a user goal into ordered (or parallel) tasks with dependencies, not just answer in one shot.
2. **Act in the world** — Call real tools: search the web, read/write files, run commands, remember facts, and (eventually) control a browser like a user would.
3. **Stay governable** — Restrict which tools exist per run, detect loops, queue runs per session, and support delegation via child runs without infinite nesting.
4. **Remain explainable** — Expose the **plan**, every **LLM call**, every **tool invocation**, **database state**, and **live timeline** so you can debug, demo, and teach how the system behaves.

**Evoclaw** implements that vision **incrementally**: each version (v1 → v4) is runnable and comparable. You can show *exactly* what breaks or succeeds when you add the next feature set—ideal for talks, coursework, or product thinking about “agentic” systems.

---

## How each version advances the objective

| Version | Folder | Port | What this version *adds* toward the objective |
|---------|--------|------|-----------------------------------------------|
| **OpenClaw 1** | `evoclaw/v1/backend` | **8080** | **Foundation: tools + a single agent loop.** The model chooses among a few tools (mock search, math, file) in a short read→think→act loop. **Gap vs goal:** No multi-task planning, no real web search, no shell, no memory, no browser—so complex goals fall apart or stop early. |
| **OpenClaw 2** | `evoclaw/v2/backend` | **8081** | **Structured planning + real retrieval.** The LLM outputs a **directed acyclic graph (DAG)** of tasks; the engine runs them in order, passing results forward. Tools include **real search (Tavily)**, file, math, HTTP. **Gap vs goal:** Still cannot run arbitrary commands, persist memory across steps/runs, enforce tool policy, or drive a GUI browser. |
| **OpenClaw 3** | `evoclaw/v3/backend` | **8082** | **Operations, memory, policy, and scale.** Adds **sandboxed exec**, **session memory**, **tool allow/deny and profiles**, **per-session run queue**, **loop detection**, **hooks**, **context compaction** for long runs, optional **parallel execution**, and **sub-runs** (delegate a sub-goal to a child run; no nesting). **Gap vs goal:** Still cannot “see” or click the live web like a human—needs browser automation. |
| **OpenClaw 4** | `evoclaw/v4/backend` | **8083** | **Web as an environment.** Everything in v3 plus a **Playwright-based browser tool**: navigate, **snapshot** (accessibility-style refs for elements), click/type by ref, keyboard, **screenshot to disk**, and **live viewport streaming** in the UI so observers see what the “browser agent” sees. This closes the loop for **research → act on real sites → capture evidence** in one orchestrated run. |

**In one sentence per step:**

- **v1** — *Can the agent use tools at all?*  
- **v2** — *Can it plan multiple tasks and use real data from the web?*  
- **v3** — *Can it run code, remember, enforce safety/policy, and handle long or delegated workflows?*  
- **v4** — *Can it operate the actual web UI, not just APIs and files?*

---

## Cumulative capability matrix

| Capability | v1 | v2 | v3 | v4 |
|------------|:--:|:--:|:--:|:--:|
| Multi-task plan (DAG) | — | ✓ | ✓ | ✓ |
| Real web search (Tavily) | — | ✓ | ✓ | ✓ |
| File / math / HTTP tools | partial | ✓ | ✓ | ✓ |
| Sandboxed **exec** (shell) | — | — | ✓ | ✓ |
| **Memory** (session KV) | — | — | ✓ | ✓ |
| Tool **profiles** / allow / deny | — | — | ✓ | ✓ |
| Per-session run **queue** | — | — | ✓ | ✓ |
| **Loop** detection, **hooks**, **compaction** | — | — | ✓ | ✓ |
| **Sub-runs** (delegate) | — | — | ✓ | ✓ |
| **Browser** (navigate, snapshot, interact, screenshot) | — | — | — | ✓ |
| **Live viewport** in UI (v4) | — | — | — | ✓ |
| Rich **debug UI** (graph, timeline, LLM prompts, DB, plan JSON) | basic | full | full | full |

---

## Repository layout

```
ai_code/
├── evoclaw/
│   ├── README.md              # Quick version map & run commands
│   ├── v1/backend/            # OpenClaw 1
│   ├── v2/backend/            # OpenClaw 2 (+ v2/tests)
│   ├── v3/backend/            # OpenClaw 3
│   └── v4/backend/            # OpenClaw 4
├── scripts/run-openclaw.sh    # Run any backend with repo-root .env
├── QUERIES.md                 # Demo queries: what works in each version
├── OPENCLAW_CONTEXT_AND_VISION.md   # Session context & roadmap (e.g. v5–v10)
├── OPENCLAW3_OPENCLAW4_PLAN.md      # Original v3/v4 UI/plan requirements
├── DEMO_WALKTHROUGH.md
├── .env.example               # Template for API keys (copy to .env)
└── README.md                  # This file
```

---

## Quick start

### Prerequisites

- **Java 17+**, **Maven**
- **OpenAI API key** (all versions that call the LLM)
- **Tavily API key** (v2–v4 for real search)
- **Playwright Chromium** (v4 only, one-time):  
  `mvn -f evoclaw/v4/backend exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"`

### API keys

```bash
cp .env.example .env
# Edit .env: OPENAI_API_KEY=...  TAVILY_API_KEY=...
```

`.env` is **gitignored**—never commit secrets.

### Run a version

From the **repository root**:

```bash
source .env

cd evoclaw/v1/backend && mvn spring-boot:run   # http://localhost:8080
cd evoclaw/v2/backend && mvn spring-boot:run   # http://localhost:8081
cd evoclaw/v3/backend && mvn spring-boot:run   # http://localhost:8082
cd evoclaw/v4/backend && mvn spring-boot:run   # http://localhost:8083
```

Or:

```bash
./scripts/run-openclaw.sh evoclaw/v3/backend
./scripts/run-openclaw.sh evoclaw/v4/backend
```

### Try it

See **[QUERIES.md](./QUERIES.md)** for example prompts that **succeed** or **fail by design** in each version—so you can narrate the objective version-by-version.

---

## Documentation index

| Document | Purpose |
|----------|---------|
| **[evoclaw/README.md](./evoclaw/README.md)** | Compact version table and run lines |
| **[QUERIES.md](./QUERIES.md)** | Demo queries per version |
| **[OPENCLAW_CONTEXT_AND_VISION.md](./OPENCLAW_CONTEXT_AND_VISION.md)** | Build notes, fixes, and **future direction** (e.g. robotics / OpenClaw 5+) |
| **[OPENCLAW3_OPENCLAW4_PLAN.md](./OPENCLAW3_OPENCLAW4_PLAN.md)** | Original requirements for debugging UI and events |
| **[DEMO_WALKTHROUGH.md](./DEMO_WALKTHROUGH.md)** | Walkthrough for live demos |
| **[GITHUB_PUSH.md](./GITHUB_PUSH.md)** | Publishing this repo to GitHub |

---

## Roadmap (beyond v4)

Future versions (conceptually **OpenClaw 5–10**) can extend the same pattern: **new tools** (e.g. robotics, cameras, enterprise APIs), **stricter guardrails**, and **new live panels** in the UI—without dropping the core ideas: **plan → execute with tools → observe everything**. Details live in **OPENCLAW_CONTEXT_AND_VISION.md**.

---

## Author

**[rishabhsingh7320](https://github.com/rishabhsingh7320)**

---

*Evoclaw: same objective, four checkpoints—from a single tool-using agent to a browser-capable, policy-aware orchestrator you can see inside.*
