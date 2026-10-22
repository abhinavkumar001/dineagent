package com.dineagent;

import com.dineagent.llm.ChatMessage;
import com.dineagent.llm.LlmClient;
import com.dineagent.llm.LlmReply;
import com.dineagent.llm.ToolCall;
import com.dineagent.llm.ToolSpec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A fake LLM for tests: returns replies from a script, so agent-loop logic and guardrails
 * can be tested deterministically without network calls or API cost.
 */
public class ScriptedLlm implements LlmClient {

    private final Deque<LlmReply> script = new ArrayDeque<>();
    private boolean loopForever;

    public void reset() {
        script.clear();
        loopForever = false;
    }

    public ScriptedLlm callTool(String name, Map<String, Object> args) {
        script.add(new LlmReply("", List.of(new ToolCall(name, args)), null, 10, 5));
        return this;
    }

    public ScriptedLlm say(String text) {
        script.add(new LlmReply(text, List.of(), null, 10, 5));
        return this;
    }

    /** Simulates a confused model that never stops calling tools. */
    public void loopForever() {
        loopForever = true;
    }

    @Override
    public String name() {
        return "scripted-test-llm";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public LlmReply next(String systemPrompt, List<ChatMessage> history, List<ToolSpec> tools) {
        if (loopForever) {
            return new LlmReply("", List.of(new ToolCall("view_cart", Map.of())), null, 10, 5);
        }
        return script.isEmpty() ? new LlmReply("Done.", List.of(), null, 10, 5) : script.poll();
    }
}
