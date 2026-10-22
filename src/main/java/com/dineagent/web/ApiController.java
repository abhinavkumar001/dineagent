package com.dineagent.web;

import com.dineagent.agent.AgentService;
import com.dineagent.agent.ChatResponse;
import com.dineagent.agent.Prompts;
import com.dineagent.agent.ToolRegistry;
import com.dineagent.catalog.CatalogStore;
import com.dineagent.catalog.MenuSearchService;
import com.dineagent.eval.EvalReport;
import com.dineagent.eval.EvalService;
import com.dineagent.llm.LlmClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ApiController {

    public record ChatRequest(String sessionId, @NotBlank @Size(max = 1000) String message) {}

    private final AgentService agent;
    private final EvalService eval;
    private final LlmClient llm;
    private final MenuSearchService search;
    private final ToolRegistry tools;
    private final CatalogStore catalog;

    public ApiController(AgentService agent, EvalService eval, LlmClient llm, MenuSearchService search,
                         ToolRegistry tools, CatalogStore catalog) {
        this.agent = agent;
        this.eval = eval;
        this.llm = llm;
        this.search = search;
        this.tools = tools;
        this.catalog = catalog;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agent.chat(request.sessionId(), request.message().trim());
    }

    @GetMapping("/session/{id}")
    public Map<String, Object> session(@PathVariable String id) {
        return agent.state(id);
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", llm.name());
        m.put("llmConfigured", llm.available());
        m.put("semanticSearch", search.semanticEnabled());
        m.put("promptVersion", Prompts.VERSION);
        m.put("tools", tools.names());
        m.put("restaurants", catalog.restaurants().size());
        m.put("dishes", catalog.items().size());
        m.put("evalCases", eval.cases().size());
        return m;
    }

    /** Runs the evaluation set. Makes real LLM calls, so it takes a minute and uses API quota. */
    @PostMapping("/eval")
    public EvalReport runEval(@RequestParam(defaultValue = "true") boolean judge,
                              @RequestParam(required = false) Set<String> cases) {
        return eval.run(judge, cases);
    }
}
