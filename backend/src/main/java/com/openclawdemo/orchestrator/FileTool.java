package com.openclawdemo.orchestrator;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class FileTool implements Tool {

    private final Path baseDir = Path.of("sandbox");

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        String op = args != null ? String.valueOf(args.getOrDefault("op", "read")) : "read";
        String pathStr = args != null ? String.valueOf(args.getOrDefault("path", "note.txt")) : "note.txt";
        Path target = baseDir.resolve(pathStr).normalize();

        Map<String, Object> res = new HashMap<>();
        try {
            Files.createDirectories(baseDir);
            if ("write".equals(op)) {
                String content = String.valueOf(args.getOrDefault("content", ""));
                Files.writeString(target, content);
                res.put("status", "ok");
            } else {
                if (Files.exists(target)) {
                    res.put("content", Files.readString(target));
                } else {
                    res.put("content", "");
                }
            }
        } catch (IOException e) {
            res.put("error", e.getMessage());
        }
        return res;
    }
}

