package com.openclaw3.orchestrator;

import java.util.*;

public class LoopDetector {

    private final int windowSize;
    private final int threshold;
    private final LinkedList<String> recentCalls = new LinkedList<>();

    public LoopDetector(int windowSize, int threshold) {
        this.windowSize = windowSize;
        this.threshold = threshold;
    }

    public boolean recordAndCheck(String toolName, Map<String, Object> args) {
        String fingerprint = toolName + ":" + stableHash(args);
        recentCalls.addLast(fingerprint);
        if (recentCalls.size() > windowSize) {
            recentCalls.removeFirst();
        }
        int count = 0;
        for (String call : recentCalls) {
            if (call.equals(fingerprint)) count++;
        }
        return count >= threshold;
    }

    private String stableHash(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "empty";
        TreeMap<String, Object> sorted = new TreeMap<>(args);
        return String.valueOf(sorted.hashCode());
    }

    public void clear() {
        recentCalls.clear();
    }
}
