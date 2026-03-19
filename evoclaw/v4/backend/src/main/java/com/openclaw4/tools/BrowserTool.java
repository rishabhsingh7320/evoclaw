package com.openclaw4.tools;

import com.openclaw4.browser.BrowserSession;
import com.openclaw4.browser.BrowserSessionManager;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class BrowserTool implements Tool {

    private final BrowserSessionManager sessionManager;
    private final ThreadLocal<Long> currentRunId = new ThreadLocal<>();

    public BrowserTool(BrowserSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setCurrentRunId(Long runId) {
        this.currentRunId.set(runId);
    }

    @Override
    public String getName() { return "browser"; }

    @Override
    public String getDescription() {
        return "Control a headless Chrome browser to navigate web pages, interact with elements, and take screenshots. " +
               "Actions: navigate(url), snapshot() to get interactive elements with ref numbers, " +
               "click(ref) to click an element, type(ref, text) to type into an input, " +
               "press_key(key) to press a keyboard key (e.g. Enter), " +
               "screenshot(filename) to save a screenshot. " +
               "WORKFLOW: Always navigate first, then snapshot to see elements and get refs, then click/type by ref. " +
               "Refs expire after navigation — always take a new snapshot after navigating.";
    }

    @Override
    public String getParameterSchema() {
        return "{ \"action\": \"navigate|snapshot|click|type|press_key|screenshot\", " +
               "\"url\": \"string (for navigate)\", " +
               "\"ref\": \"number (for click/type)\", " +
               "\"text\": \"string (for type)\", " +
               "\"key\": \"string (for press_key, e.g. Enter)\", " +
               "\"filename\": \"string (for screenshot, optional)\" }";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        String action = args != null ? String.valueOf(args.getOrDefault("action", "")) : "";

        if (!sessionManager.isEnabled()) {
            result.put("success", false);
            result.put("error", "Browser is disabled. Set BROWSER_ENABLED=true to enable.");
            return result;
        }

        Long runId = currentRunId.get();
        if (runId == null) {
            result.put("success", false);
            result.put("error", "No run context available for browser operations");
            return result;
        }

        BrowserSession session = sessionManager.getOrCreate(runId);
        if (session == null) {
            result.put("success", false);
            result.put("error", "Failed to create browser session");
            return result;
        }

        try {
            switch (action) {
                case "navigate" -> {
                    String url = String.valueOf(args.getOrDefault("url", ""));
                    String output = session.navigate(url);
                    result.put("success", true);
                    result.put("output", output);
                }
                case "snapshot" -> {
                    String output = session.snapshot();
                    result.put("success", true);
                    result.put("output", output);
                }
                case "click" -> {
                    int ref = parseRef(args);
                    String output = session.click(ref);
                    result.put("success", true);
                    result.put("output", output);
                }
                case "type" -> {
                    int ref = parseRef(args);
                    String text = String.valueOf(args.getOrDefault("text", ""));
                    String output = session.type(ref, text);
                    result.put("success", true);
                    result.put("output", output);
                }
                case "press_key" -> {
                    String key = String.valueOf(args.getOrDefault("key", "Enter"));
                    String output = session.pressKey(key);
                    result.put("success", true);
                    result.put("output", output);
                }
                case "screenshot" -> {
                    String filename = String.valueOf(args.getOrDefault("filename", "screenshot.png"));
                    Map<String, Object> shot = session.screenshot(filename);
                    appendViewportBase64(session, shot);
                    return shot;
                }
                default -> {
                    result.put("success", false);
                    result.put("error", "Unknown browser action: " + action + ". Use: navigate, snapshot, click, type, press_key, screenshot");
                }
            }
            if (Boolean.TRUE.equals(result.get("success"))) {
                appendViewportBase64(session, result);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Browser error: " + e.getMessage());
        }
        return result;
    }

    private void appendViewportBase64(BrowserSession session, Map<String, Object> result) {
        try {
            String b64 = session.getViewportScreenshotBase64();
            if (b64 != null) result.put("viewportBase64", b64);
        } catch (Exception ignored) { }
    }

    private int parseRef(Map<String, Object> args) {
        try {
            return Integer.parseInt(String.valueOf(args.getOrDefault("ref", "0")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
