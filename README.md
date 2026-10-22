# DineAgent: agentic AI food-ordering assistant

Tell it *"something spicy and veg under ₹300 that arrives in 30 minutes"* and an LLM agent plans the steps
itself: it searches menus, compares dishes, fills the cart, asks you to confirm, places the order and tracks it.
It also answers policy questions (refunds, fees, cancellation) with retrieval-augmented generation and cites the source.

**Backend:** Java 17 + Spring Boot with **Google Gemini**. **Frontend:** React (Vite).
The agent loop, tool calling, hybrid retrieval and evaluation are written by hand (no LangChain), so every part
can be explained.

## What it shows

| Gen AI concept | Where |
|---|---|
| **Agent loop (ReAct style)** with tool calling: LLM → tool calls → results → LLM … → answer | `agent/AgentService` |
| **Function calling** over raw REST (`generateContent` + `functionDeclarations`) | `llm/GeminiClient` |
| **8 tools**: search_menu, add_to_cart, remove_from_cart, view_cart, clear_cart, place_order, track_order, lookup_policy | `agent/tools/` |
| **RAG** over policy documents, answers cite their source | `knowledge/PolicyRetriever`, `LookupPolicyTool` |
| **Hybrid retrieval**: Gemini embeddings (cosine, in-memory vector store) + TF-IDF keywords, 0.65 / 0.35 | `ai/HybridRetriever` |
| **Prompt engineering**: versioned system prompt that maps wishes to filters | `agent/Prompts` |
| **Guardrails in code**, not only in the prompt | see below |
| **Evaluation**: 16 test conversations, agent metrics + **LLM-as-judge** groundedness | `eval/` |
| Parallel tool execution, rate-limit retry with backoff, token and latency tracking | `AgentService`, `GeminiClient` |

### Guardrails
- **No order without a real "yes"**: `place_order` checks the customer's latest message itself, so a confused or
  prompt-injected model still cannot spend money.
- **No invented dishes**: `add_to_cart` only accepts item IDs that exist in the catalog.
- **Hard filters are exact**: veg, price, spice and delivery limits are applied in Java, not left to the model.
- **Step limit**: at most 6 model calls per message, so a looping agent stops and cost stays bounded.
- **Business rules**: one restaurant per cart, max quantity and cart value.
- Tool output is treated as data; unknown tools and tool errors are returned to the model instead of crashing.

## Run it

**1. Backend (Spring Boot, port 8080)**
1. Get a free API key at **aistudio.google.com** → *Get API key*.
2. In IntelliJ: open the `dineagent` folder, then Run configuration → Environment variables →
   `GEMINI_API_KEY=your-key`, and run `DineAgentApplication`.
   Or in a terminal: `set GEMINI_API_KEY=your-key` (Windows) / `export GEMINI_API_KEY=your-key` (Mac/Linux),
   then `mvn spring-boot:run`.
3. Check that http://localhost:8080/api/info shows `"llmConfigured": true`.

**2. Frontend (React, port 5173)**
```
cd frontend
npm install
npm run dev
```
Open **http://localhost:5173**. The Vite dev server forwards `/api` calls to the backend.

Try: *"light dinner under ₹250"* → *"add the soup"* → *"what's in my cart?"* → *"yes, place it"* → *"where is my order?"*
and *"can I cancel after the food is being prepared?"*. The right panel shows every tool call the agent made.

**Evaluation:** click *Run evaluation* in the app, or `POST /api/eval`. It runs the 16 cases in
`src/main/resources/data/eval-cases.json` against the real model (about 50–80 API calls).

**Tests:** `mvn test`. They use a scripted fake LLM, so they need no key or network.

## Metrics reported by the evaluation

| Metric | Meaning |
|---|---|
| Task success rate | every check in the case passed |
| Tool selection accuracy | called the expected tools and none of the forbidden ones |
| Constraint satisfaction | share of returned dishes that respect veg / price / spice / time limits |
| Guardrail pass rate | no order placed without confirmation (includes a prompt-injection case) |
| Groundedness (1–5) | LLM-as-judge: is the reply supported by the tool data? |
| Avg / p95 latency, LLM calls per turn, tokens | speed and cost |

## Project layout
```
src/main/java/com/dineagent/
  ai/         tokenizer, TF-IDF index, vector store, hybrid retriever
  llm/        Gemini REST client (chat + tools + embeddings), message types
  catalog/    restaurants and menus (data/catalog.json), filtered hybrid search
  agent/      agent loop, session memory, prompts, tools/
  knowledge/  policy RAG (data/policies.json)
  cart/ order/
  eval/       test cases, metrics, LLM-as-judge
  web/        REST API and CORS config
frontend/     React app: chat, dish cards, cart, order status, agent trace, evaluation panel
```

## API
`POST /api/chat {sessionId, message}` · `GET /api/session/{id}` · `GET /api/info` · `POST /api/eval?judge=true`

## Limits and next steps
Data is a demo catalog of 8 restaurants kept in memory. Next steps would be PostgreSQL with pgvector,
streaming replies (SSE), per-user auth, and tracking eval metrics per prompt version over time.
