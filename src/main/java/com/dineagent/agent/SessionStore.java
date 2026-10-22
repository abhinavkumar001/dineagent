package com.dineagent.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    public AgentSession getOrCreate(String id) {
        String key = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        return sessions.computeIfAbsent(key, AgentSession::new);
    }

    public AgentSession fresh() {
        return getOrCreate(UUID.randomUUID().toString());
    }
}
