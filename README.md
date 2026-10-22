# DineAgent: agentic AI food-ordering assistant

Tell it *"something spicy and veg under ₹300 that arrives in 30 minutes"* and an LLM agent plans the steps
itself: it searches menus, compares dishes, fills the cart, asks you to confirm, places the order and tracks it.
It also answers policy questions (refunds, fees, cancellation) with retrieval-augmented generation and cites the source.

**Backend:** Java 17 + Spring Boot with **Google Gemini**. **Frontend:** React (Vite).
The agent loop, tool calling, hybrid retrieval and evaluation are written by hand (no LangChain), so every part
can be explained.

## Features

- **Food-delivery app**: restaurants, menus with dish illustrations, search, cart with bill details, checkout, order history.
- **AI agent** (Gemini) in a side drawer: finds dishes, fills the shared cart, gets the order ready. You confirm and pay yourself.
- **Razorpay payments (test mode)**: amount computed on the server, HMAC signature verified before the order is created,
  webhook as a backup, one order per payment.
- **Real-time** with Server-Sent Events: live order tracking (status, minutes left, rider progress), cart synced across
  tabs and with the AI agent, and the agent's tool calls streamed live while it works.

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

**Optional: Razorpay test payments**
1. Sign up at dashboard.razorpay.com, stay in **Test Mode**, and generate API keys (Account & Settings → API Keys).
2. Add `RAZORPAY_KEY_ID=rzp_test_...` and `RAZORPAY_KEY_SECRET=...` to the backend's environment variables and restart.
3. At checkout, pay with Razorpay's test details (for UPI use `success@razorpay`; test cards are listed in Razorpay's docs).
   No real money moves. Without keys, the app uses pay on delivery.
4. Webhook (optional): set `RAZORPAY_WEBHOOK_SECRET`, expose the backend (e.g. with ngrok) and add
   `https://<your-url>/api/payments/webhook` in the Razorpay dashboard for the `payment.captured` event.

**Demo speed:** `ORDER_TIME_SCALE=10` (default) makes a 30-minute delivery play out in 3 minutes. Set it to `1` for real time.

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
  payment/    Razorpay client, signature verification, webhook
  realtime/   Server-Sent Events: live cart, order tracking, streaming agent steps
frontend/     React app: home, restaurant menus, dish illustrations, cart, Razorpay checkout, live tracking, AI drawer
```

## Payment flow (Razorpay)
```
React                         Spring Boot                          Razorpay
  | Pay ₹250 ----------------> | POST /api/payments/order             |
  |                            |   amount = cart total (server-side)  |
  |                            | ----------- create order ----------> |
  | <------ order_id, key_id --|                                      |
  | opens Checkout, user pays ------------------------------------->  |
  | <-------------------------- payment_id + signature -------------- |
  | POST /api/payments/verify  |                                      |
  |                            | HMAC_SHA256(order_id|payment_id, secret) == signature ?
  |                            | yes -> place food order (once only)  |
  | <------ order (live) ------|                                      |
  |                            | <---- webhook payment.captured ----- |  (backup if the tab was closed)
```

## Real-time (Server-Sent Events)
- `GET /api/events/{sessionId}` pushes `cart` and `order` events; a scheduler sends live order progress every 3 seconds.
- `GET /api/chat/stream?sessionId=&message=` streams each agent tool call as a `step` event, then `done`.
- SSE is one-way server→browser over plain HTTP with automatic reconnects, which is all tracking needs; WebSockets would
  only be needed for two-way messaging such as chat between rider and customer.

## API
Store: `GET /api/restaurants` · `GET /api/restaurants/{id}` · `GET /api/search` · `GET/DELETE /api/cart/{sid}` ·
`POST /api/cart/{sid}/items` · `PUT /api/cart/{sid}/items/{itemId}` · `POST /api/orders` · `GET /api/orders?sessionId=` · `GET /api/orders/{id}`
Payments: `POST /api/payments/order` · `POST /api/payments/verify` · `POST /api/payments/webhook`
Real-time: `GET /api/events/{sid}` · `GET /api/chat/stream`
AI: `POST /api/chat` · `GET /api/info` · `POST /api/eval?judge=true`

## Limits and next steps
Data is a demo catalog of 8 restaurants kept in memory. Next steps would be PostgreSQL with pgvector,
streaming replies (SSE), per-user auth, and tracking eval metrics per prompt version over time.
