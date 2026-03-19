# Demo Queries for OpenClaw v1–v4

Use these queries to demonstrate what each version **can** and **cannot** do, and why you need the next version.

---

## OpenClaw 1 (`openclawdemo` – port 8080)

Single manager agent; read/think/act loop (max 3 steps); tools: search (mock), math, file. No task DAG, no LLM planning.

| # | Query | Works? | What to show |
|---|-------|--------|-------------|
| 1 | "What is 15 times 27?" | Yes | Math tool used, single-step result |
| 2 | "Search for OpenClaw architecture." | Yes | Mock search returns results |
| 3 | "Create a file hello.txt with Hello World." | Yes | File tool writes to sandbox |
| 4 | "Check the weather in Bangalore and save it to a file." | Partial | Manager may do one tool then stop; no multi-step plan |
| 5 | "Break this into 3 tasks: search weather, compare two cities, write a report." | **No** | No LLM planning, no task DAG → **need v2** |
| 6 | "Run the command `ls -la` and tell me the output." | **No** | No exec tool → **need v3** |
| 7 | "Remember that my favorite color is blue." | **No** | No memory → **need v3** |
| 8 | "Go to example.com and click the first link." | **No** | No browser → **need v4** |

**Summary:** Good for single-step tool use. Cannot plan multi-task workflows, run shell commands, remember across turns, or control a browser.

---

## OpenClaw 2 (`omenclawdemo2` – port 8081)

LLM plans a task DAG; executes tasks in topological order; tools: search (Tavily), file, math, http. No recursion, no exec, no memory, no browser.

| # | Query | Works? | What to show |
|---|-------|--------|-------------|
| 1 | "Search weather in Bangalore and save it to a file." | Yes | Plan: task 1 (search) → task 2 (file); context passed between tasks |
| 2 | "Research quantum computing and save a summary to quantum.txt." | Yes | Search + file in one run; task graph visible |
| 3 | "Compare the weather in Tokyo and London, then write a comparison report to weather_report.txt." | Yes | 3-task DAG with dependencies |
| 4 | "What is 15 * 27 + 33 / 3? Show the steps." | Yes | Math tool, multi-step calculation |
| 5 | "Run the command `ls -la` and tell me the output." | **No** | No exec tool → **need v3** |
| 6 | "Remember my name is Alice; use it in the next message." | **No** | No memory → **need v3** |
| 7 | "If the first task fails, retry it once then continue." | **No** | No retry/queue semantics → **need v3** |
| 8 | "Only allow search and file tools for this run." | **No** | No tool allow/deny → **need v3** |
| 9 | "Go to example.com and click the first link." | **No** | No browser → **need v4** |

**What to show in UI:** Task graph (DAG), Internal Timeline, Tasks & Steps (tool calls with args/results), LLM Prompts & Context, DB Inspector (all tables), Plan JSON.

**Summary:** Multi-task planning and execution with context passing. Cannot run shell, remember, restrict tools, or use a browser.

---

## OpenClaw 3 (`openclaw3` – port 8082)

Everything in v2 plus: tool allow/deny + profiles, per-session run queue, sandboxed exec tool, memory tool, loop-detection, hooks, context compaction, optional parallel execution, optional sub-run.

| # | Query | Works? | What to show |
|---|-------|--------|-------------|
| 1 | "Run the command `ls -la` in the sandbox and tell me the output." | Yes | Exec tool runs shell command; output in Tasks & Steps |
| 2 | "Remember that the project deadline is March 20. Then tell me what you remember." | Yes | Memory tool put + get; memory table in DB Inspector |
| 3 | "Create a Python script that prints hello world, save it, then run it with exec." | Yes | File tool (write) → exec tool (run); cross-tool task chain |
| 4 | "Search for the latest AI news and save a summary. Only use search and file tools." | Yes | Tool policy: set `TOOL_ALLOW=search,file` or `TOOL_PROFILE=minimal` |
| 5 | "Do 6 research tasks in sequence, each producing a long paragraph, then summarize all of them." | Yes | Context compaction kicks in; compaction_applied event visible |
| 6 | "Delegate 'fetch the top 5 headlines' to a sub-task and then summarize them." | Yes | Sub-run created; sub_run_started/finished events visible |
| 7 | "Search for X 10 times in a row with the exact same query." | Yes | Loop detection triggers; loop_detection_warning event visible |
| 8 | "Go to example.com and click the first link." | **No** | No browser → **need v4** |
| 9 | "Take a screenshot of a webpage." | **No** | No browser → **need v4** |

**What to show in UI:** Everything from v2 plus: hook_before_tool events, loop_detection_warning events, compaction_applied events, sub_run_started/finished events, memory table in DB Inspector, runs.parent_run_id column, delegate steps in Tasks & Steps.

**Summary:** Everything in v2 plus exec, memory, tool policies, queue, loop-detection, hooks, compaction, sub-runs. Still no Chrome control.

---

## OpenClaw 4 (`openclaw4` – port 8083)

Everything in v3 plus: browser tool (Playwright for Java) — navigate, snapshot (refs), click, type, press_key, screenshot.

| # | Query | Works? | What to show |
|---|-------|--------|-------------|
| 1 | "Go to https://example.com and click the 'More information' link." | Yes | Navigate → snapshot → click by ref |
| 2 | "Open https://wikipedia.org, take a snapshot, type 'OpenClaw' in the search box, and press Enter." | Yes | Navigate → snapshot → type(ref, text) → press_key(Enter) |
| 3 | "Take a screenshot of https://example.com and save it to example.png." | Yes | Navigate → screenshot; file saved in sandbox |
| 4 | "Go to https://news.ycombinator.com, snapshot the page, and tell me the top 3 headlines." | Yes | Navigate → snapshot → LLM reads the element text |
| 5 | "Run `echo hello`, remember the output, then open example.com and take a screenshot." | Yes | Exec + memory + browser in one run |
| 6 | "Search for 'OpenClaw', then go to the first result URL in the browser and take a screenshot." | Yes | Search tool → browser navigate → screenshot |
| 7 | "Remember that my name is Alice, then search for AI news and save to a file." | Yes | All v3 features still work |

**What to show in UI:** Everything from v3 plus: browser tool steps in Tasks & Steps (showing snapshot output with refs, click/type actions), browser-related events in timeline.

**Summary:** Everything in v3 plus full browser automation via ref-based snapshot and actions, so the agent can "see" and interact with live web pages.

---

## API Keys (shared across all OpenClaw projects)

Keys are stored in a **single file** at the repo root so every OpenClaw (and future ones) can reuse them:

- **`.env`** – Your real keys (do not commit; it’s in `.gitignore`).
- **`.env.example`** – Template; copy to `.env` and fill in: `cp .env.example .env`

**Use the keys when running any backend:**

```bash
# From repo root: load keys, then run the backend you want
source .env
cd openclaw3/backend && mvn spring-boot:run
# or
cd openclaw4/backend && mvn spring-boot:run
```

**Or use the helper script (sources .env for you):**

```bash
./scripts/run-openclaw.sh openclaw3/backend   # port 8082
./scripts/run-openclaw.sh openclaw4/backend   # port 8083
```

---

## Running the Demos

```bash
# OpenClaw 1 (port 8080)
cd openclawdemo/backend
source ../../.env 2>/dev/null || true
OPENAI_API_KEY=${OPENAI_API_KEY:-your-key} mvn spring-boot:run

# OpenClaw 2 (port 8081)
cd omenclawdemo2/backend
source ../../.env 2>/dev/null || true
OPENAI_API_KEY=${OPENAI_API_KEY:-your-key} TAVILY_API_KEY=${TAVILY_API_KEY:-your-tavily-key} mvn spring-boot:run

# OpenClaw 3 (port 8082) — use shared .env
./scripts/run-openclaw.sh openclaw3/backend
# or: source .env && cd openclaw3/backend && mvn spring-boot:run

# OpenClaw 4 (port 8083) — install Playwright browsers once, then from repo root:
# mvn -f openclaw4/backend exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
./scripts/run-openclaw.sh openclaw4/backend
# or: source .env && cd openclaw4/backend && mvn spring-boot:run
```

### Tool Profile Examples (v3 & v4)

```bash
# Only allow search and file tools
TOOL_PROFILE=minimal mvn spring-boot:run

# Allow all tools except http
TOOL_DENY=http mvn spring-boot:run

# Only allow specific tools
TOOL_ALLOW=search,file,exec mvn spring-boot:run

# Enable parallel task execution
EXECUTION_PARALLEL=true mvn spring-boot:run
```
