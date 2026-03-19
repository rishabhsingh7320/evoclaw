## OpenClaw-style Demo: How a Query Flows Through the System

This walkthrough shows how to demo the architecture to your team: what happens on a query, how multi-agent/tool orchestration looks, and how the database is used.

### 1. Start the backend

From the `backend` directory run your usual Spring Boot command, for example:

```bash
mvn spring-boot:run
# or with your IDE's Run configuration
```

Open `http://localhost:8080/` in a browser to see the UI.

### 2. Walkthrough 1 – What happens on a query

**Script**

1. In the UI, type:
   > show me how openclaw works internally
2. Click **Run**.
3. Point out the header strip:
   - `Session` – maps to the `sessions` table row.
   - `Run` – maps to the `runs` table row.
   - `Status` – mirrors `runs.status`.
4. On the left, show the **Conversation** column:
   - The **user** row comes from inserting into `messages` with `role="user"`.
   - The **assistant** row comes from the orchestrator creating a new message with `role="assistant"`.
5. On the right, step through the **Internal timeline**:
   - `run_started` – new row in `events` with `kind="run_started"`.
   - `step_started` – new `steps` row and matching `events` row.
   - `assistant_message` – event emitted when the orchestrator saves the assistant reply.
   - `run_finished` – final update of the `runs` row and event with status.

**DB view you can show**

- `sessions` – one row per conversation.
- `messages` – filter by `session_id` to show all messages.
- `runs` – a single run per user query in this demo.
- `steps` – a small sequence of rows (`step_index` 0..N).
- `events` – the fine-grained timeline used by the WebSocket.

### 3. Walkthrough 2 – Tool calls and pseudo multi-agent behavior

Use inputs that trigger different behaviors in `ManagerAgent`:

1. **Search-style query**
   - Input:  
     > search openclaw architecture
   - Explain:
     - `ManagerAgent` reads the text and decides to call the `search` tool.
     - The orchestrator:
       - Creates a `steps` row with `agent_id="manager"`.
       - Inserts a `tool_calls` row with `tool_name="search"`.
       - Writes events:
         - `tool_started` – tool call begins.
         - `tool_finished` – fake search result is stored in `tool_calls.result_json`.
       - Emits `step_finished` and then `run_finished`.
     - In the timeline column you should see:
       - `run_started` → `step_started` → `tool_started` → `tool_finished` → `step_finished` → `run_finished`.

2. **Math-style query**
   - Input:  
     > calculate something with numbers 2 and 3
   - Explain:
     - The manager recognizes a numeric query and routes to the `math` tool.
     - The pattern in `events` is the same, but `tool_name="math"` and `result_json` contains the arithmetic result.

Use this section to emphasize:

- **Agents vs tools** – the `ManagerAgent` plans, tools act.
- **Observability** – each call is stored in `tool_calls` and mirrored into `events` for the UI.

### 4. Walkthrough 3 – Mapping UI to database schema

Show the mapping from the UI into tables:

- **Conversation panel**
  - Reads from `messages` filtered by `session_id`.
  - `role` column distinguishes `user` vs `assistant`.
- **Internal timeline panel**
  - Reads streamed `events` for a given `run_id`.
  - Each badge corresponds to one row in `events`:
    - `kind` → badge text and color.
    - `created_at` → timestamp in the UI.
    - Optional `step_id` → lets you group events by logical step.
- **Runs**
  - For each user submit, a new row in `runs` is created.
  - `status`, `started_at`, `finished_at` show lifecycle.

### 5. Walkthrough 4 – Architecture story (verbal)

While the demo is running, narrate the architecture in terms of the components:

1. **Gateway / API layer**
   - `POST /api/query` accepts the query, ensures a `session`, creates a `run`, and kicks off the orchestrator.
2. **Orchestrator**
   - Implements a simple loop of:
     1. Read the user message.
     2. Ask `ManagerAgent` what to do.
     3. Optionally call a `Tool`.
     4. Log `steps`, `tool_calls`, and `events`.
     5. Emit a final assistant message and mark the run as finished.
3. **Event logger + WebSocket**
   - Every important action (run started, step started, tool started/finished, assistant message, run finished) is:
     - Written to the `events` table.
     - Pushed over `/ws/runs/{runId}` so the UI can update live.

This gives your team a concrete, end-to-end view of how an OpenClaw-style orchestrator stores and exposes its internal state.

