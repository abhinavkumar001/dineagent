package com.dineagent.agent;

import java.util.Map;

/** LLMs send numbers as ints, doubles or strings; these helpers read them safely. */
public final class Args {

    private Args() {
    }

    public static String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v == null ? null : v.toString().trim();
    }

    public static Integer integer(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v instanceof Number n) return (int) Math.round(n.doubleValue());
        if (v instanceof String s && !s.isBlank()) {
            try {
                return (int) Math.round(Double.parseDouble(s.replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Boolean bool(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return s.equalsIgnoreCase("true") ? Boolean.TRUE : s.equalsIgnoreCase("false") ? Boolean.FALSE : null;
        return null;
    }

    /** Small builder for JSON-schema parameter maps. */
    public static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }
}
