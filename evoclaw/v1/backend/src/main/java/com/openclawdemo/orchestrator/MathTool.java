package com.openclawdemo.orchestrator;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MathTool implements Tool {

    @Override
    public String getName() {
        return "math";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        double a = parse(args, "a");
        double b = parse(args, "b");
        String op = args != null ? String.valueOf(args.getOrDefault("op", "add")) : "add";
        double result;
        switch (op) {
            case "sub" -> result = a - b;
            case "mul" -> result = a * b;
            case "div" -> result = b != 0 ? a / b : Double.NaN;
            default -> result = a + b;
        }
        Map<String, Object> res = new HashMap<>();
        res.put("result", result);
        return res;
    }

    private double parse(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(args.get(key)));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

