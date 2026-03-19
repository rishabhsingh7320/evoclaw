package com.openclaw3.tools;

import com.openclaw3.model.Memory;
import com.openclaw3.model.Session;
import com.openclaw3.repository.MemoryRepository;
import com.openclaw3.repository.SessionRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MemoryTool implements Tool {

    private final MemoryRepository memoryRepository;
    private final SessionRepository sessionRepository;

    private final ThreadLocal<Long> currentSessionId = new ThreadLocal<>();

    public MemoryTool(MemoryRepository memoryRepository, SessionRepository sessionRepository) {
        this.memoryRepository = memoryRepository;
        this.sessionRepository = sessionRepository;
    }

    public void setCurrentSessionId(Long sessionId) {
        this.currentSessionId.set(sessionId);
    }

    @Override
    public String getName() { return "memory"; }

    @Override
    public String getDescription() {
        return "Persistent key-value memory that survives across runs in the same session. " +
               "Use memory_get to recall facts, memory_put to remember things for later. " +
               "Great for storing user preferences, project context, or intermediate results.";
    }

    @Override
    public String getParameterSchema() {
        return "{ \"op\": \"get|put|list\", \"key\": \"string\", \"value\": \"string (for put)\" }";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        String op = args != null ? String.valueOf(args.getOrDefault("op", "get")) : "get";
        String key = args != null ? String.valueOf(args.getOrDefault("key", "")) : "";
        Map<String, Object> result = new HashMap<>();

        Long sessionId = currentSessionId.get();
        if (sessionId == null) {
            result.put("success", false);
            result.put("error", "No session context available for memory operations");
            return result;
        }

        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            result.put("success", false);
            result.put("error", "Session not found: " + sessionId);
            return result;
        }
        Session session = sessionOpt.get();

        switch (op) {
            case "put" -> {
                String value = args != null ? String.valueOf(args.getOrDefault("value", "")) : "";
                Optional<Memory> existing = memoryRepository.findBySessionAndKey(session, key);
                Memory mem;
                if (existing.isPresent()) {
                    mem = existing.get();
                    mem.setValue(value);
                } else {
                    mem = new Memory();
                    mem.setSession(session);
                    mem.setKey(key);
                    mem.setValue(value);
                }
                memoryRepository.save(mem);
                result.put("success", true);
                result.put("output", "Stored '" + key + "' = '" + value + "'");
            }
            case "list" -> {
                List<Memory> all = memoryRepository.findBySessionOrderByUpdatedAtDesc(session);
                StringBuilder sb = new StringBuilder();
                sb.append("Memory entries (").append(all.size()).append("):\n");
                for (Memory m : all) {
                    sb.append("  ").append(m.getKey()).append(" = ").append(m.getValue()).append("\n");
                }
                result.put("success", true);
                result.put("output", sb.toString());
            }
            default -> {
                Optional<Memory> found = memoryRepository.findBySessionAndKey(session, key);
                if (found.isPresent()) {
                    result.put("success", true);
                    result.put("output", found.get().getValue());
                } else {
                    result.put("success", true);
                    result.put("output", "(no value stored for key '" + key + "')");
                }
            }
        }
        return result;
    }
}
