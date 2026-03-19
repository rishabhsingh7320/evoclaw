package com.openclawdemo.orchestrator;

import java.util.Map;

public interface Tool {

    String getName();

    Map<String, Object> call(Map<String, Object> args);
}

