package com.dineagent.agent.tools;

import com.dineagent.agent.AgentSession;
import com.dineagent.agent.Tool;
import com.dineagent.knowledge.PolicyRetriever;
import com.dineagent.llm.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.dineagent.agent.Args.*;

/** RAG tool: retrieves policy passages; the LLM must answer only from them and cite the source. */
@Component
public class LookupPolicyTool implements Tool {

    private final PolicyRetriever retriever;

    public LookupPolicyTool(PolicyRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec("lookup_policy",
                "Search the company's policies on refunds, cancellation, delivery fees, late delivery, allergies, payments and tracking. "
                        + "Use for any policy question and answer only from the returned passages.",
                Map.of("type", "OBJECT", "properties", Map.of("question", prop("STRING", "The customer's question")),
                        "required", List.of("question")));
    }

    @Override
    public Map<String, Object> execute(AgentSession session, Map<String, Object> args) {
        List<PolicyRetriever.Retrieved> hits = retriever.retrieve(str(args, "question"), 3);
        List<Map<String, Object>> passages = hits.stream().map(h -> Map.<String, Object>of(
                "source", h.chunk().source(), "title", h.chunk().title(), "text", h.chunk().text(), "score", h.score())).toList();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("passages", passages);
        if (passages.isEmpty()) {
            out.put("note", "No relevant policy found. Say you don't know; do not guess.");
            out.put("summary", "no policy found");
        } else {
            out.put("summary", passages.size() + " passage(s): " + hits.get(0).chunk().source());
        }
        return out;
    }
}
