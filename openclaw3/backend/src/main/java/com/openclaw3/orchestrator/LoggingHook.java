package com.openclaw3.orchestrator;

import com.openclaw3.model.Run;
import com.openclaw3.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LoggingHook implements OrchestratorHook {

    private static final Logger log = LoggerFactory.getLogger(LoggingHook.class);

    @Override
    public void beforeToolCall(Run run, Task task, String toolName, Map<String, Object> args) {
        log.info("[Hook] before_tool_call: run={}, task={}, tool={}", run.getId(), task.getExternalId(), toolName);
    }

    @Override
    public void afterToolCall(Run run, Task task, String toolName, Map<String, Object> args, Map<String, Object> result) {
        boolean success = Boolean.TRUE.equals(result.get("success"));
        log.info("[Hook] after_tool_call: run={}, task={}, tool={}, success={}", run.getId(), task.getExternalId(), toolName, success);
    }

    @Override
    public void onRunEnd(Run run, String phase) {
        log.info("[Hook] on_run_end: run={}, phase={}", run.getId(), phase);
    }
}
