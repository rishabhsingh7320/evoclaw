package com.openclaw4.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BrowserSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BrowserSessionManager.class);

    @Value("${browser.headless:true}")
    private boolean headless;

    @Value("${browser.enabled:true}")
    private boolean enabled;

    private final Map<Long, BrowserSession> sessions = new ConcurrentHashMap<>();

    public boolean isEnabled() { return enabled; }

    public BrowserSession getOrCreate(Long runId) {
        if (!enabled) return null;
        return sessions.computeIfAbsent(runId, id -> {
            log.info("Creating browser session for run {}", id);
            return new BrowserSession(headless);
        });
    }

    public void closeSession(Long runId) {
        BrowserSession session = sessions.remove(runId);
        if (session != null) {
            log.info("Closing browser session for run {}", runId);
            session.close();
        }
    }

    @PreDestroy
    public void closeAll() {
        for (Map.Entry<Long, BrowserSession> entry : sessions.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("Error closing browser session for run {}: {}", entry.getKey(), e.getMessage());
            }
        }
        sessions.clear();
    }
}
