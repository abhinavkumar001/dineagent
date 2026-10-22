# DineAgent

An **agentic AI food-ordering assistant**. You type *"something spicy and veg under ₹300, delivered in 30 minutes"*,
and an LLM agent plans the steps, calls tools to search menus, fill the cart and place the order, and asks you
to confirm before spending money. Built with **Java 17 + Spring Boot** and **Gemini function calling**.

![DineAgent chat with agent trace](docs/screenshot-chat.png)

## What it shows

| Gen AI concept | How it's built here |
|---|---|
| **Agent loop (ReAct)** | `AgentService`: user → LLM → tool calls → results back to LLM → … → final answer, with a step limit |
| **Tool / function calling** | 7 tools described by JSON schemas: `search_menu`, `add_to_cart`, `view_cart`, `remove_from_cart`, `place_order`, `track_order`, `answer_policy_question` |
| **RAG** | Policy questions (refunds, fees, cancellation…) retrieve passages first; the agent answers only from them and cites the source |
| **Embeddings + hybrid search** | Menu ranking = 0.6 × embedding cosine + 0.4 × TF-IDF, after hard filters (veg, budget, delivery time, spice) |
| **Guardrails** | Order placed only after an explicit "yes" (checked in code), max steps per message, one-restaurant carts, ₹5,000 cart cap, unknown-tool handling, tool results treated as data |
| **Resilience** | If Gemini fails or times out, the agent falls back to a rule-based planner instead of erroring |
| **Evaluation** | 24 test conversations scoring task success, tool selection, constraint satisfaction, policy retrieval, safety, latency |
| **Concurrency** | Multiple tool calls in one turn run in parallel (`CompletableFuture`) |

The agent loop and Gemini client are written by hand (no LangChain/Spring AI), so every step is visible.

## Architecture

```
Browser (chat · agent trace · cart · order tracker · eval dashboard)
   │  POST /api/chat
   ▼
AgentService ── agent loop, step limit, fallback
   │       │
   │       └── LlmClient ── GeminiClient (function calling)  |  OfflinePlanner (no key / fallback)
   ▼
Tools ──► MenuSearchService (filters + TF-IDF + embeddings)
      ──► Cart / OrderService (one restaurant per order, confirmation guardrail)
      ──► PolicyService (retrieval for RAG)
```

## Run it

Requires Java 17+. No database needed (data lives in `src/main/resources/data/`).

**With Gemini (recommended):** get a free key at aistudio.google.com, then
```
set GEMINI_API_KEY=your_key      (Windows)
export GEMINI_API_KEY=your_key   (Mac/Linux)
mvn spring-boot:run
```
**Without a key:** just run `mvn spring-boot:run`. The offline planner drives the same tools.

Open http://localhost:8080. Try:
1. *"Something spicy and veg under ₹300, delivered in 30 minutes"*
2. *"add 2"* → *"place order"* → *"yes"*
3. *"track my order"*, then *"can I get a refund for a missing item?"*
4. Click **Run evaluation**.

Tests: `mvn test` (offline, no key needed).

## Evaluation

`POST /api/eval/run?model=current|offline` runs 24 conversations from `data/eval_cases.json`:
search with constraints, multi-turn ordering, safety (no order without "yes", single-restaurant cart),
policy questions, out-of-scope input, and free-form phrasing.

| Metric | Offline rule-based baseline (measured) |
|---|---|
| Task success | 83.3% (20/24) |
| Tool selection | 96.7% |
| Constraint satisfaction | 88.9% |
| Policy retrieval | 100% |
| Safety checks | 80% |

The baseline passes every structured case but fails the 4 free-form ones (e.g. *"budget is three hundred
rupees"*, *"gentle on spice"*, *"add the cheapest dosa"*), which need a real language model.
Run the evaluation with your Gemini key to measure the LLM agent against this baseline.

## API

| Endpoint | Purpose |
|---|---|
| `POST /api/chat` `{sessionId, message}` | One agent turn: reply, tool trace, cart, order |
| `GET /api/session/{id}` | Cart and latest order |
| `GET /api/info` | Active model and whether semantic search is on |
| `POST /api/eval/run?model=current\|offline` | Run the evaluation set |

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `GEMINI_API_KEY` | (empty) | Enables Gemini; otherwise offline planner |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Chat model |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | Embedding model for semantic search |
| `LLM_PROVIDER` | `auto` | `auto`, `gemini` or `offline` |
