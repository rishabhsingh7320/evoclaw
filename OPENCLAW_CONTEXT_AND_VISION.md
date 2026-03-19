# OpenClaw – Full Context & Vision (Future Reference)

**Purpose:** Single reference for everything we built, how to run it, and the roadmap for OpenClaw 5–10. Use this when restarting tomorrow or when building new versions.

---

## 1. What Exists Today: OpenClaw 1–4

| Version | Project Path | Port | Summary |
|--------|---------------|------|---------|
| **OpenClaw 1** | `openclawdemo/` or `backend/` | 8080 | Single manager agent; tools: search (mock), math, file. No DAG, no LLM planning. |
| **OpenClaw 2** | `omenclawdemo2/` | 8081 | LLM plans task DAG; tools: search (Tavily), file, math, http. No exec, memory, or browser. |
| **OpenClaw 3** | `openclaw3/` | 8082 | v2 + tool allow/deny, per-session queue, **ExecTool**, **MemoryTool**, loop-detection, hooks, context compaction, sub-runs, optional parallel execution. |
| **OpenClaw 4** | `openclaw4/` | 8083 | v3 + **Browser tool (Playwright)**: navigate, snapshot (refs), click, type, press_key, screenshot; **live viewport streaming** in UI. |

### Key Paths (Backends)

- OpenClaw 1: `backend/` (or `openclawdemo/backend/`) – package `com.openclawdemo`
- OpenClaw 2: `omenclawdemo2/backend/` – package `com.omenclawdemo2`
- OpenClaw 3: `openclaw3/backend/` – package `com.openclaw3`
- OpenClaw 4: `openclaw4/backend/` – package `com.openclaw4`

### Docs to Read

- **QUERIES.md** – Demo queries per version, what works, API keys, run commands.
- **OPENCLAW3_OPENCLAW4_PLAN.md** – Original build plan for v3/v4 (debugging UI, events, DB Inspector).
- **DEMO_WALKTHROUGH.md** – Walkthrough for demos.
- **openclaw3/README.md**, **openclaw4/README.md** – Feature lists and config.

---

## 2. What We Built / Fixed Today (Session Summary)

- **Screenshot task using search instead of browser**  
  For “take a screenshot” the orchestrator was forcing the **search** tool at step 0. Fixed by: (1) detecting screenshot tasks (`isScreenshotTask`, `extractScreenshotFilename`) and forcing **browser** with `action=screenshot` and filename instead; (2) adding TOOL GUIDANCE and sub-run hints so the LLM uses browser for screenshots, not search.  
  Files: `openclaw4/backend/.../OrchestratorService.java`, helpers and forced-tool branch.

- **Live stream of browser in UI**  
  After each browser action (navigate, snapshot, click, type, screenshot), the viewport is captured as base64 and sent over WebSocket. The **Browser (live)** card in OpenClaw 4 shows a live-updating image + status text.  
  Files: `BrowserSession.getViewportScreenshotBase64()`, `BrowserTool` appends viewport to result, `OrchestratorService` includes `viewportBase64` in `browser_*` events, `index.html` has `#browserViewport` img and updates it on browser events.

- **LLM timeouts**  
  OpenClaw 3 and 4: `LlmClient` uses `RestTemplateBuilder` with 30s connect, 120s read timeout so planning/execution cannot hang forever. Timeout errors are surfaced clearly.

- **409 and Run button**  
  When backend returns 409 (run already active), the OpenClaw 4 UI re-enables the Run button so the user is not stuck.

- **API keys in one place**  
  Keys are in repo-root **`.env`** (OpenAI + Tavily). `.env` is in `.gitignore`. `.env.example` is the template. All OpenClaw backends (and future ones) can reuse by sourcing `.env` before run.

- **Run script**  
  `scripts/run-openclaw.sh` sources `.env` and runs a backend, e.g.  
  `./scripts/run-openclaw.sh openclaw3/backend` or `openclaw4/backend`.

- **Restarted OpenClaw 3 and 4** with keys from `.env`; both ran successfully. **Servers are to be shut down at end of session**; restart tomorrow using the same keys and script.

---

## 3. Keys and How to Run (Keep This Safe)

- **Keys are stored in:** `/Users/rishabhsingh/ai_code/.env`  
  Variables: `OPENAI_API_KEY`, `TAVILY_API_KEY`.  
  Do **not** commit `.env` (it’s in `.gitignore`). Use `.env.example` as a template for new setups.

- **To run any OpenClaw (today or tomorrow):**
  ```bash
  cd /Users/rishabhsingh/ai_code
  source .env
  cd openclaw3/backend && mvn spring-boot:run   # port 8082
  # or
  cd openclaw4/backend && mvn spring-boot:run   # port 8083
  ```
  Or use the script:
  ```bash
  ./scripts/run-openclaw.sh openclaw3/backend
  ./scripts/run-openclaw.sh openclaw4/backend
  ```

- **OpenClaw 4 one-time:** Install Playwright browser:
  ```bash
  mvn -f openclaw4/backend exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
  ```

- **URLs when running:**  
  OpenClaw 3: http://localhost:8082  
  OpenClaw 4: http://localhost:8083  
  Live browser view is only in OpenClaw 4 (LIVE VIEW tab → “Browser (live)” card).

---

## 4. Vision: OpenClaw 5–10 and Beyond

**Goal:** Extend the same orchestrator pattern (LLM plans a DAG, executes tasks with tools, sub-runs, memory, compaction) with **new capabilities** in future versions. Keep this vision in one place so we can build OpenClaw 5, 6, … 10 consistently.

- **OpenClaw 5+** – Add more **tools** and **integrations**, e.g.:
  - **Robotics / physical world:** Robot control (ROS, simulated or real robots), sensors, actuators. Tasks like “navigate to X”, “pick object”, “report sensor reading”.
  - **More agents / delegation:** Multi-agent handoffs, specialized sub-agents (e.g. coding agent, research agent), still within the same run/DAG model.
  - **Streaming / media:** Audio in/out, video, screen capture, as first-class tools.
  - **Databases / APIs:** Structured DB tool, graph DB, custom API tools as tools in the same registry.
  - **Safety / guardrails:** Tool allow/deny (already in v3/v4), rate limits, human-in-the-loop steps, audit log for high-risk tools (e.g. exec, robot).

- **Architecture to preserve:**
  - Same **plan → execute** flow: LLM produces a task DAG, executor runs tasks in order (or parallel by depth), with prior context and tool results.
  - **Tool registry** with allow/deny/profiles so new tools (e.g. robot, camera) can be added and gated.
  - **Sub-runs** for delegation; **memory** for persistence; **compaction** for long runs; **hooks** for logging/observability.
  - **UI:** Keep Live View, task graph, timeline, DB Inspector, LLM Prompts, Plan JSON; add new event types and cards (e.g. “Robot (live)”, “Camera feed”) similar to Browser (live).

- **When building OpenClaw 5 (e.g. + robot tool):**
  - Copy from `openclaw4/` to `openclaw5/`, new package `com.openclaw5`, new port (e.g. 8084).
  - Add `RobotTool` (or similar), register it, define actions (e.g. move, grasp, get_sensor).
  - Emit events like `robot_move`, `robot_sensor` and optionally stream robot state or camera to the UI.
  - Reuse same `.env` for API keys; same `scripts/run-openclaw.sh` pattern for running.

---

## 5. Shutdown and Restart (Tomorrow)

- **Shutdown:** Stop OpenClaw 3 and 4 processes (e.g. `pkill -f openclaw3`, `pkill -f openclaw4` or close the terminals that run them). No need to change any code or keys.

- **Restart tomorrow:**
  1. `cd /Users/rishabhsingh/ai_code`
  2. `./scripts/run-openclaw.sh openclaw3/backend` (in one terminal)
  3. `./scripts/run-openclaw.sh openclaw4/backend` (in another terminal)
  4. Open http://localhost:8082 (v3) and http://localhost:8083 (v4). Keys are already in `.env`; the script loads them.

---

## 6. Quick Reference: Important Files

| What | Where |
|------|--------|
| API keys | `.env` (root); template `.env.example` |
| Run script | `scripts/run-openclaw.sh` |
| Demo queries & run commands | `QUERIES.md` |
| v3/v4 build plan | `OPENCLAW3_OPENCLAW4_PLAN.md` |
| This context & vision | `OPENCLAW_CONTEXT_AND_VISION.md` |
| v3 orchestrator | `openclaw3/backend/.../OrchestratorService.java` |
| v4 orchestrator + browser events | `openclaw4/backend/.../OrchestratorService.java` |
| v4 browser session + viewport | `openclaw4/backend/.../BrowserSession.java`, `BrowserTool.java` |
| v4 UI (live viewport) | `openclaw4/backend/src/main/resources/static/index.html` |
| LLM client (timeouts) | `openclaw3/.../LlmClient.java`, `openclaw4/.../LlmClient.java` |

Keep this file and `.env` safe; use them to resume work and to build OpenClaw 5–10 with robots and other new capabilities.
