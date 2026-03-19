# OpenClaw3

Advanced LLM task-graph orchestrator with exec, memory, hooks, loop-detection, context compaction, sub-runs, and tool policies.

## Features (over v2)
- **Tool allow/deny + profiles** — Configure which tools are available via `TOOL_PROFILE`, `TOOL_ALLOW`, `TOOL_DENY`
- **Per-session run queue** — Rejects new runs if one is already active for the session
- **Exec tool** — Run shell commands in a sandboxed directory
- **Memory tool** — Persistent key-value store per session (survives across runs)
- **Loop detection** — Detects repeated identical tool calls and warns the LLM
- **Hooks** — `before_tool_call`, `after_tool_call`, `on_run_end` extension points
- **Context compaction** — Summarizes prior task results when they exceed the token budget
- **Parallel execution** — Optional parallel task execution by depth level (`EXECUTION_PARALLEL=true`)
- **Sub-runs** — LLM can delegate subtasks to child runs (no nesting)

## Run

```bash
cd backend
OPENAI_API_KEY=your-key TAVILY_API_KEY=your-tavily-key mvn spring-boot:run
```

Open http://localhost:8082

## Database
H2 in-memory. Tables: sessions, runs (with parent_run_id), messages, tasks, task_dependencies, task_steps, events, memory.

## UI Tabs
- **Live View** — Task graph, timeline, task status, conversation
- **Tasks & Steps** — Tool calls with args/results, delegate steps
- **LLM Prompts & Context** — Full system/user prompts and LLM decisions
- **DB Inspector** — All table rows including memory
- **Plan JSON** — Raw LLM-generated plan
