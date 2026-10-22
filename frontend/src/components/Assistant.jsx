import { useEffect, useRef, useState } from "react";
import { api, rupees } from "../api.js";

function RichText({ text }) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith("**") && part.endsWith("**") ? <b key={i}>{part.slice(2, -2)}</b> : part);
}

export default function Assistant({ open, seed, sessionId, info, onClose, onCart }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [showTrace, setShowTrace] = useState(null);
  const endRef = useRef(null);
  const lastSeed = useRef(null);

  useEffect(() => { endRef.current?.scrollIntoView({ block: "end", behavior: "smooth" }); }, [messages, busy]);

  const send = async (text) => {
    const message = text.trim();
    if (!message || busy) return;
    setMessages((m) => [...m, { from: "me", text: message }]);
    setBusy(true);
    try {
      const r = await api.chat(sessionId, message);
      const search = [...r.toolResults].reverse().find((t) => t.tool === "search_menu");
      setMessages((m) => [...m, { from: "bot", text: r.reply, dishes: search?.result?.dishes ?? [], run: r }]);
      onCart(r.cart);
      if (r.order && r.trace.some((t) => t.tool === "place_order" && !t.error)) {
        window.location.hash = `#/order/${r.order.orderId}`;
      }
    } catch (e) {
      setMessages((m) => [...m, { from: "bot", text: e.message, error: true }]);
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (open && seed && seed !== lastSeed.current) {
      lastSeed.current = seed;
      send(seed.text);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, seed]);

  if (!open) return null;

  return (
    <aside className="assistant" aria-label="DineAgent assistant">
      <div className="assistant-head">
        <span className="brand-mark small">D</span>
        <div><b>DineAgent</b><small>{info?.llmConfigured ? info.model : "AI assistant"}</small></div>
        <button className="icon-btn" onClick={onClose} aria-label="Close assistant">✕</button>
      </div>

      <div className="assistant-body" aria-live="polite">
        {messages.length === 0 && (
          <div className="assistant-hello">
            <p>Tell me what you feel like eating. I can search every menu, fill your cart and place the order once you say yes.</p>
            {["Spicy veg under ₹300", "Light dinner in 30 minutes", "How do refunds work?"].map((s) => (
              <button key={s} className="chip" onClick={() => send(s)}>{s}</button>
            ))}
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className="a-turn">
            <div className={`bubble ${m.from} ${m.error ? "error" : ""}`}><RichText text={m.text} /></div>
            {m.dishes?.length > 0 && (
              <div className="a-dishes">
                {m.dishes.slice(0, 4).map((d) => (
                  <button key={d.itemId} className="a-dish" disabled={busy}
                          onClick={() => send(`Add 1 ${d.name} (${d.itemId}) to my cart`)}>
                    <span className="a-emoji" aria-hidden="true">🍽</span>
                    <span><b>{d.name}</b><small>{d.restaurant} · {rupees(d.price)} · {d.deliveryMinutes} min</small></span>
                    <span className="a-add">Add</span>
                  </button>
                ))}
              </div>
            )}
            {m.run && m.run.trace.length > 0 && (
              <button className="trace-toggle" onClick={() => setShowTrace(showTrace === i ? null : i)}>
                {showTrace === i ? "Hide" : "How I did this"} ({m.run.trace.length} tool call{m.run.trace.length > 1 ? "s" : ""})
              </button>
            )}
            {showTrace === i && (
              <ol className="trace">
                {m.run.trace.map((t, k) => (
                  <li key={k} className={t.error ? "err" : ""}>
                    <code>{t.tool}</code><span className="args">{JSON.stringify(t.args)}</span>
                    <span className="sum">{t.summary} · {t.millis} ms</span>
                  </li>
                ))}
                <li className="final"><span className="sum">{m.run.llmCalls} model calls, {(m.run.latencyMs / 1000).toFixed(1)} s</span></li>
              </ol>
            )}
          </div>
        ))}
        {busy && <div className="bubble bot thinking">Thinking…</div>}
        <div ref={endRef} />
      </div>

      <form className="assistant-input" onSubmit={(e) => { e.preventDefault(); send(input); setInput(""); }}>
        <input value={input} onChange={(e) => setInput(e.target.value)} maxLength={1000} placeholder="Ask for food, an order, or a policy…" aria-label="Message DineAgent" />
        <button className="btn primary" disabled={busy || !input.trim()}>Send</button>
      </form>
    </aside>
  );
}
