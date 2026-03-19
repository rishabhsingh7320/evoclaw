package com.openclaw4.tools;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MathTool implements Tool {

    @Override
    public String getName() { return "math"; }

    @Override
    public String getDescription() { return "Perform arithmetic calculations. ALWAYS use this tool for any math — do not calculate in your head. Supports: add, sub, mul, div."; }

    @Override
    public String getParameterSchema() {
        return "{ \"a\": \"number\", \"b\": \"number\", \"op\": \"add|sub|mul|div\" }";
    }

    @Override
    public Map<String, Object> call(Map<String, Object> args) {
        double a = parseNum(args, "a");
        double b = parseNum(args, "b");
        String op = args != null ? String.valueOf(args.getOrDefault("op", "add")) : "add";
        double result = switch (op) {
            case "sub" -> a - b;
            case "mul" -> a * b;
            case "div" -> b != 0 ? a / b : Double.NaN;
            default -> a + b;
        };
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("output", String.valueOf(result));
        return res;
    }

    private double parseNum(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) return 0;
        try { return Double.parseDouble(String.valueOf(args.get(key))); }
        catch (NumberFormatException e) { return 0; }
    }
}
