# OpenClaw3 and OpenClaw4 Build Plan (with Full Debugging UI)

> **Repo layout (current):** All four versions live under **`evoclaw/`** — v1 `evoclaw/v1/backend`, v2 `evoclaw/v2/backend`, v3 `evoclaw/v3/backend`, v4 `evoclaw/v4/backend`. See **`evoclaw/README.md`**.  
> Historical references below to `omenclawdemo2/`, `openclaw3/`, `openclaw4/` describe the original copy-from paths when the plan was written.

This document extends the build plan so that **all debugging and internal-viewing capabilities** from OpenClaw 1 and OpenClaw 2 are **preserved and extended** in OpenClaw 3 and OpenClaw 4, making it easy to explain each version.

---

## Part 0: Debugging and Viewing Internals (Required in v3 and v4)

### What exists today

**OpenClaw 1 (openclawdemo, `backend/`)**

- **Header:** Session ID, Run ID, Status (idle / running / finished).
- **Conversation:** User message + assistant messages (from events).
- **Internal timeline:** Real-time events over WebSocket: `run_started`, `step_started`, `tool_started`, `tool_finished`, `assistant_message`, `run_finished`. Each event can show payload (e.g. tool name, result snippet).
- **No** task graph, no DB inspector, no plan JSON, no “LLM prompts” tab (single manager agent, no DAG).

**OpenClaw 2 (omenclawdemo2)**

- **Header:** Session ID, Run ID, Phase (idle / planning / executing / succeeded / failed).
- **Tabs:**
  - **Live View:** Task graph (Cytoscape/dagre) with nodes for each task, edges for dependencies, live status (pending → running → completed/failed); Internal timeline (events); Task status list; Conversation.
  - **Tasks & Steps:** Per-task list with expandable steps: each tool call (tool name, arguments JSON, result JSON) and the final “complete” step with response text.
  - **LLM Prompts & Context:** Chronological list of every LLM call (planning + per-task execution) with system prompt excerpt, user prompt (including prior task results and last tool result), and LLM response/decision.
  - **DB Inspector:** “Refresh DB” button; tables for current run: session, run, messages, tasks, task_dependencies, task_steps, events (all rows for that run).
  - **Plan JSON:** Raw JSON of the LLM-generated task DAG.
- **WebSocket:** Same run-scoped events pushed in real time; frontend subscribes to `/ws/runs/{runId}`.
- **REST:** `GET /api/runs/{runId}` (run + tasks + steps + plan JSON), `GET /api/runs/{runId}/db` (full DB snapshot).

### Requirement for OpenClaw 3 and OpenClaw 4

When building **openclaw3** and **openclaw4**:

1. **Start from the OpenClaw 2 UI**  
   Copy the full `index.html` (and any static assets) from omenclawdemo2 so that v3 and v4 have the same tabbed layout: Live View (task graph + timeline + task list + conversation), Tasks & Steps, LLM Prompts & Context, DB Inspector, Plan JSON.

2. **Keep all existing events and views**  
   All event types and payloads that v2 emits must still be emitted and displayed (e.g. `run_started`, `planning_started`, `plan_ready`, `task_started`, `task_step`, `task_completed`, `task_failed`, `llm_call`, `llm_response`, `assistant_message`, `run_finished`, `error`). The timeline and “LLM Prompts & Context” tab must continue to work.

3. **Extend for new behavior (OpenClaw 3)**  
   - **New events to log and show in timeline:**  
     `run_queued` (when run is queued because another run is active), `run_dequeued`, `hook_before_tool`, `hook_after_tool`, `tool_exec` (exec tool: command + exit code / output snippet), `tool_memory_get` / `tool_memory_put`, `loop_detection_warning`, `sub_run_started`, `sub_run_finished`, `compaction_applied` (optional).  
   - **DB Inspector:** Include new tables/columns: e.g. `memory` (session_id, key, value), and `runs.parent_run_id` when sub-runs exist. If there is a queue table or run status “queued”, show it.  
   - **Tasks & Steps:** For exec tool steps, show command and stdout/stderr (truncated if long). For memory tool steps, show key and value (or “get key X” / “put key X”). For loop_detection, show a step or inline message that the call was skipped/warned.  
   - **Header:** Show phase “queued” when the run is waiting; optionally show “Queue position: 2” or “Active run: 5” for clarity.

4. **Extend for new behavior (OpenClaw 4)**  
   - **New events to log and show in timeline:**  
     `browser_navigate`, `browser_snapshot` (with ref count or snippet), `browser_click`, `browser_type`, `browser_screenshot` (e.g. path or “screenshot captured”).  
   - **Tasks & Steps:** For browser tool steps, show: action (navigate/snapshot/click/type/screenshot), arguments (url, ref, text), and result (e.g. snapshot text preview, “Clicked ref 3”, screenshot path or inline image if stored).  
   - **Optional new tab or card:** “Browser state” – last snapshot text (or ref list) and/or last screenshot thumbnail, so the audience can see “what the agent saw” when explaining.  
   - **DB Inspector:** If browser sessions or screenshots are stored (e.g. in task_steps or a new table), expose them so DB view stays complete.

5. **Consistent “How it works” and labels**  
   - In v3: Update the collapsible “How it works” to mention queue, exec, memory, loop-detection, hooks, optional sub-runs and compaction.  
   - In v4: Add a bullet for browser (navigate → snapshot → act by ref).  
   - Use titles like “OpenClaw3 – …” and “OpenClaw4 – …” so it’s clear which version is running.

6. **No removal of v2 internals**  
   Do not remove or hide the task graph, plan JSON, DB Inspector, or LLM Prompts tab when adding v3/v4 features. Add new events and new columns/sections; keep existing ones so that explaining “we added X, and you still see the same DAG and timeline” is easy.

---

## Part 1: Build OpenClaw3

**Source:** Copy from `omenclawdemo2/` into a new project `openclaw3/` (package `com.openclaw3`).

**UI / debugging:** Implement **Part 0** for OpenClaw 3 (copy v2 UI, add new events and DB sections for queue, exec, memory, hooks, loop-detection, sub-run, compaction).

**Backend additions:** Tool allow/deny + profiles, per-session run queue, ExecTool, MemoryTool, loop-detection, OrchestratorHook (before/after tool, on_run_end), **memory compaction** (see below), optional parallel task execution, optional sub-run. Emit the new event types above so the UI can show them.

### Memory compaction in OpenClaw 3 (stay within context and proceed)

Yes. OpenClaw 3 includes **memory compaction** so that when the task/run is going out of (context) memory, it can create small memories and proceed.

- **When it triggers:** Before building the prompt for the next task (or next step), estimate the size of “prior task results” + “last tool result” (e.g. character count or token count). If this exceeds a configured **reserve budget** (e.g. 4000 tokens or ~16k chars for gpt-4o-mini), compaction runs.
- **What it does:** Instead of passing all prior task results verbatim, the system **creates small memories**:
  - **Option A (summarize):** Send the over-size block to the LLM with a prompt like “Summarize the following in 2–3 short paragraphs, preserving key facts and outcomes.” Replace the old block with this summary in the context passed to the next task/step. Optionally append “(earlier results summarized)” and keep the last N task results in full.
  - **Option B (truncate + summary):** Truncate the oldest task results and prepend a one-line summary (e.g. “Task 1–3: [brief summary].”) so the model still has a compact “memory” of what happened earlier.
- **Result:** The run does not hit the model’s context limit; the agent continues with a compressed view of earlier work. This is the same idea as OpenClaw’s compaction: keep recent detail, replace older context with a short summary so the run can proceed.
- **Observability:** Emit a `compaction_applied` event (e.g. with `reason: "context_over_budget"`, `beforeChars`, `afterChars`, or `summarizedTaskCount`) so the Internal timeline and debugging UI show when compaction happened and that “small memories” were created to proceed.

**Deliverable:** `openclaw3/` runnable (e.g. port 8082) with full debugging UI and all new features visible in timeline, Tasks & Steps, and DB Inspector.

---

## Part 2: Build OpenClaw4

**Source:** Copy from `openclaw3/` into `openclaw4/` (package `com.openclaw4`).

**UI / debugging:** Implement **Part 0** for OpenClaw 4 (browser events, browser steps in Tasks & Steps, optional “Browser state” card/tab, DB Inspector including any browser-related storage).

**Backend additions:** Playwright dependency, BrowserTool (navigate, snapshot, click, type, screenshot) with ref map and lifecycle. Emit browser_* events.

**Deliverable:** `openclaw4/` runnable (e.g. port 8083) with full debugging UI including browser internals.

---

## Part 3: Demo Queries for OpenClaw 1–4

Use these to show what each version **can** and **cannot** do, and to demonstrate the **same internals** (timeline, graph, DB, prompts) in v2, v3, and v4.

### OpenClaw 1 (openclawdemo)

| Query | Can do? | Notes |
|-------|--------|--------|
| “What is 15 times 27?” | Yes | Math tool. |
| “Search for OpenClaw architecture.” | Yes | Mock search. |
| “Create a file hello.txt with Hello World.” | Yes | File tool. |
| “Check the weather in Bangalore and save it to a file.” | Partially | No multi-step plan. |
| “Break into 3 tasks: search weather, compare cities, write report.” | No | No DAG. Need v2. |
| “Run `ls -la` and tell me the output.” | No | No exec. Need v3. |
| “Remember my favorite color is blue.” | No | No memory. Need v3. |
| “Go to example.com and click the first link.” | No | No browser. Need v4. |

**What you can show in the UI:** Session, Run, Status; Conversation; **Internal timeline** (run_started → step_started → tool_started → tool_finished → assistant_message → run_finished). No task graph or DB inspector.

---

### OpenClaw 2 (omenclawdemo2)

| Query | Can do? | Notes |
|-------|--------|--------|
| “Break into 3 tasks: search weather, compare cities, write report.” | Yes | DAG + execution. |
| “Check the weather in Bangalore and save it to a file.” | Yes | Plan + context passing. |
| “Research quantum computing and save a summary to quantum.txt.” | Yes | Search + file. |
| “Run `ls -la` and tell me the output.” | No | Need v3. |
| “Remember my name is Alice.” | No | Need v3. |
| “Go to example.com and click the first link.” | No | Need v4. |

**What you can show in the UI:** **Task graph** (nodes/edges, live status); **Internal timeline** (planning_started, plan_ready, task_started, task_step, task_completed, llm_call, llm_response, run_finished); **Tasks & Steps** (each tool call with args/results); **LLM Prompts & Context**; **DB Inspector** (all tables for the run); **Plan JSON**. Use these to explain “we see the DAG, every tool call, and every prompt.”

---

### OpenClaw 3 (openclaw3)

| Query | Can do? | Notes |
|-------|--------|--------|
| “Run `ls -la` in the sandbox and tell me the output.” | Yes | Exec tool. |
| “Remember the project deadline is March 20.” | Yes | Memory tool. |
| “Only use search and file for this request.” | Yes | Tool allow/deny. |
| “Delegate ‘fetch top 5 headlines’ to a sub-task and summarize.” | Yes | Sub-run (if implemented). |
| “Do 8–10 tasks in sequence, each producing a long paragraph; then summarize all.” | Yes | Long context triggers **memory compaction**; later tasks see summarized “small memories” so the run stays within context and proceeds. |
| “Go to example.com and click the first link.” | No | Need v4. |

**What you can show in the UI:** Same as v2 **plus**: in **timeline**, events like `run_queued`, `tool_exec`, `tool_memory_put`, `loop_detection_warning`, `sub_run_started`/`sub_run_finished`, and **`compaction_applied`** (when context was over budget and small memories were created so the run could proceed); in **Tasks & Steps**, exec output and memory get/put; in **DB Inspector**, `memory` table and `runs.parent_run_id`. Explain “same DAG and prompts as v2, plus we now see exec, memory, queue, hooks, and **memory compaction** when the run would otherwise run out of context.”

---

### OpenClaw 4 (openclaw4)

| Query | Can do? | Notes |
|-------|--------|--------|
| “Go to https://example.com and click the ‘More information’ link.” | Yes | Navigate → snapshot → click by ref. |
| “Open wikipedia.org, type ‘OpenClaw’ in the search box, press Enter.” | Yes | Navigate → snapshot → type → press. |
| “Take a screenshot of the current page and save it.” | Yes | Browser screenshot + file. |

**What you can show in the UI:** Same as v3 **plus**: in **timeline**, `browser_navigate`, `browser_snapshot`, `browser_click`, `browser_type`, `browser_screenshot`; in **Tasks & Steps**, browser steps with refs and snapshot/screenshot; optional **Browser state** card with last snapshot and last screenshot. Explain “same internals as v3, plus we see exactly what the agent saw in the browser and which ref it clicked.”

---

## Summary

- **OpenClaw 1:** Conversation + internal timeline (steps and tool events). No DAG, no DB inspector.
- **OpenClaw 2:** Add task graph, Tasks & Steps, LLM Prompts & Context, DB Inspector, Plan JSON; same WebSocket timeline.
- **OpenClaw 3:** Keep **all** v2 debugging views; add events and data for queue, exec, memory, hooks, loop-detection, sub-run, compaction so they are visible in the same UI.
- **OpenClaw 4:** Keep **all** v3 debugging views; add browser events and browser steps (and optional Browser state) so Chrome access is visible and explainable.

Planning and building v3 and v4 with this requirement ensures that **every version from 2 onward** supports the same style of “explain what’s going on inside” via the UI, and each new feature is visible in the timeline, task steps, and DB inspector.
