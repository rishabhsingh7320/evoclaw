package com.openclaw3.orchestrator;

import com.openclaw3.model.Run;
import com.openclaw3.model.Task;

import java.util.Map;

public interface OrchestratorHook {

    default void beforeToolCall(Run run, Task task, String toolName, Map<String, Object> args) {}

    default void afterToolCall(Run run, Task task, String toolName, Map<String, Object> args, Map<String, Object> result) {}

    default void onRunEnd(Run run, String phase) {}
}
