package com.openclaw3.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ExecTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ExecTool.class);

    @Value("${sandbox.dir:sandbox}")
    private String sandboxDir;

    @Override
    public String getName() { return "exec"; }

    @Override
    public String getDescription() {
        return "Execute a shell command in a sandboxed directory. Use for running scripts, listing files, compiling code, etc. " +
               "Commands run inside the sandbox directory and cannot access files outside it.";
    }

    @Override
    public String getParameterSchema() {
        return "{ \"command\": \"string (the shell command to run)\", \"timeoutSeconds\": \"number (optional, default 30)\" }";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        String command = args != null ? String.valueOf(args.getOrDefault("command", "echo hello")) : "echo hello";
        int timeout = 30;
        if (args != null && args.containsKey("timeoutSeconds")) {
            try { timeout = Integer.parseInt(String.valueOf(args.get("timeoutSeconds"))); }
            catch (NumberFormatException ignored) {}
        }

        Map<String, Object> result = new HashMap<>();
        try {
            Path sandbox = Path.of(sandboxDir).toAbsolutePath();
            Files.createDirectories(sandbox);

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(sandbox.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 200) {
                    output.append(line).append("\n");
                    lineCount++;
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("error", "Command timed out after " + timeout + " seconds");
                result.put("output", output.toString());
                return result;
            }

            int exitCode = process.exitValue();
            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);
            String out = output.toString();
            if (out.length() > 5000) {
                out = out.substring(0, 5000) + "\n... (truncated)";
            }
            result.put("output", out);

        } catch (Exception e) {
            log.error("Exec tool failed: {}", e.getMessage());
            result.put("success", false);
            result.put("error", "Exec failed: " + e.getMessage());
        }
        return result;
    }
}
