package com.openclaw4.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.Base64;

public class BrowserSession {

    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private final Map<Integer, Locator> refMap = new LinkedHashMap<>();
    private int nextRef = 1;

    private static final AriaRole[] SCAN_ROLES = {
        AriaRole.LINK, AriaRole.BUTTON, AriaRole.TEXTBOX,
        AriaRole.CHECKBOX, AriaRole.RADIO, AriaRole.COMBOBOX,
        AriaRole.MENUITEM, AriaRole.TAB
    };

    /** Max width for live-viewport screenshots sent to UI (scale down to keep WebSocket payload smaller). */
    private static final int LIVE_VIEWPORT_MAX_WIDTH = 960;

    public BrowserSession(boolean headless) {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(headless)
        );
        this.context = browser.newContext(new Browser.NewContextOptions().setViewportSize(LIVE_VIEWPORT_MAX_WIDTH, 600));
        this.page = context.newPage();
    }

    public String navigate(String url) {
        try {
            page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
            clearRefs();
            String title = page.title();
            return "Navigated to: " + url + " (title: " + title + ")";
        } catch (Exception e) {
            return "Navigation failed: " + e.getMessage();
        }
    }

    public String snapshot() {
        clearRefs();
        StringBuilder sb = new StringBuilder();
        sb.append("Page: ").append(page.url()).append("\n");
        sb.append("Title: ").append(page.title()).append("\n\n");
        sb.append("Interactive elements:\n");

        for (AriaRole role : SCAN_ROLES) {
            try {
                Locator locator = page.getByRole(role);
                int count = locator.count();
                for (int i = 0; i < count && i < 30; i++) {
                    Locator el = locator.nth(i);
                    try {
                        if (!el.isVisible()) continue;
                        String text = "";
                        try { text = el.innerText().trim(); } catch (Exception ignored) {}
                        if (text.length() > 80) text = text.substring(0, 80) + "...";
                        if (text.isEmpty()) {
                            try {
                                String placeholder = el.getAttribute("placeholder");
                                String ariaLabel = el.getAttribute("aria-label");
                                String name = el.getAttribute("name");
                                text = placeholder != null ? placeholder :
                                       ariaLabel != null ? ariaLabel :
                                       name != null ? name : "(unnamed)";
                            } catch (Exception ignored) {
                                text = "(unnamed)";
                            }
                        }
                        int ref = nextRef++;
                        refMap.put(ref, el);
                        sb.append("[ref=").append(ref).append("] ").append(role.name().toLowerCase())
                          .append(" \"").append(text).append("\"\n");
                    } catch (Exception e) {
                        // Element may have detached
                    }
                }
            } catch (Exception e) {
                // Role not supported or no elements
            }
        }

        if (refMap.isEmpty()) {
            sb.append("(no interactive elements found)\n");
        }

        return sb.toString();
    }

    public String click(int ref) {
        Locator loc = refMap.get(ref);
        if (loc == null) return "Ref " + ref + " not found. Take a new snapshot to get fresh refs.";
        try {
            loc.click(new Locator.ClickOptions().setTimeout(10000));
            try { page.waitForLoadState(); } catch (Exception ignored) {}
            return "Clicked ref=" + ref + ". Page URL is now: " + page.url();
        } catch (Exception e) {
            return "Click failed on ref=" + ref + ": " + e.getMessage() + ". Take a new snapshot.";
        }
    }

    public String type(int ref, String text) {
        Locator loc = refMap.get(ref);
        if (loc == null) return "Ref " + ref + " not found. Take a new snapshot to get fresh refs.";
        try {
            loc.fill(text);
            return "Typed '" + text + "' into ref=" + ref;
        } catch (Exception e) {
            return "Type failed on ref=" + ref + ": " + e.getMessage() + ". Take a new snapshot.";
        }
    }

    public String pressKey(String key) {
        try {
            page.keyboard().press(key);
            try { page.waitForLoadState(); } catch (Exception ignored) {}
            return "Pressed key '" + key + "'. Page URL: " + page.url();
        } catch (Exception e) {
            return "Key press failed: " + e.getMessage();
        }
    }

    public Map<String, Object> screenshot(String filename) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (filename == null || filename.isBlank()) filename = "screenshot.png";
            Path path = Path.of("sandbox", filename);
            java.nio.file.Files.createDirectories(path.getParent());
            page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            result.put("success", true);
            result.put("output", "Screenshot saved to: " + path);
            result.put("path", path.toString());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Screenshot failed: " + e.getMessage());
        }
        return result;
    }

    public String getPageInfo() {
        return "URL: " + page.url() + ", Title: " + page.title();
    }

    /**
     * Capture the current viewport as PNG and return as base64 for live-streaming to the UI.
     * Called after each browser action (navigate, snapshot, click, type, screenshot) so the UI can show what the browser is doing.
     */
    public String getViewportScreenshotBase64() {
        try {
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
            return png != null && png.length > 0 ? Base64.getEncoder().encodeToString(png) : null;
        } catch (Exception e) {
            log.debug("Viewport screenshot failed: {}", e.getMessage());
            return null;
        }
    }

    private void clearRefs() {
        refMap.clear();
        nextRef = 1;
    }

    public void close() {
        try {
            context.close();
            browser.close();
            playwright.close();
        } catch (Exception e) {
            log.warn("Error closing browser session: {}", e.getMessage());
        }
    }
}
