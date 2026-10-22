package com.dineagent.agent;

import com.dineagent.llm.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> all) {
        all.forEach(t -> tools.put(t.name(), t));
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(Tool::spec).toList();
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }
}
