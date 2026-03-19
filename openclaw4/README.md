# OpenClaw4

Full LLM task-graph orchestrator with browser automation (Playwright), exec, memory, hooks, loop-detection, context compaction, sub-runs, and tool policies.

## Features (over v3)
- **Browser tool (Playwright)** — Navigate web pages, snapshot interactive elements (with ref numbers), click, type, press keys, take screenshots
- All v3 features: exec, memory, tool policies, loop detection, hooks, compaction, sub-runs, parallel execution

## Browser Workflow
1. `navigate(url)` — Go to a page
2. `snapshot()` — Get interactive elements with ref numbers: `[ref=1] button "Submit"`, `[ref=2] textbox "Search"`
3. `click(ref)` / `type(ref, text)` — Interact by ref number
4. `press_key(key)` — Press a keyboard key (e.g. Enter)
5. `screenshot(filename)` — Save a screenshot to sandbox

Refs expire after navigation — always snapshot after navigating.

## Setup

```bash
cd backend

# Install Playwright browsers (one-time)
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"

# Run
OPENAI_API_KEY=your-key TAVILY_API_KEY=your-tavily-key mvn spring-boot:run
```

Open http://localhost:8083

## Configuration
- `BROWSER_ENABLED=true/false` — Enable/disable browser tool
- `BROWSER_HEADLESS=true/false` — Headless or visible browser
- `EXECUTION_PARALLEL=true/false` — Parallel task execution
- `TOOL_PROFILE=full/minimal/coding/browser` — Tool profiles
- `TOOL_ALLOW`, `TOOL_DENY` — Fine-grained tool control

## Database
H2 in-memory. Tables: sessions, runs (with parent_run_id), messages, tasks, task_dependencies, task_steps, events, memory.
