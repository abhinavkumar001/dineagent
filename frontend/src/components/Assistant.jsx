import { useEffect, useRef, useState } from "react";
import { chatStream, rupees } from "../api.js";
import { DishImage } from "./DishArt.jsx";

const STEP_LABEL = {
  search_menu: "Searching menus", add_to_cart: "Adding to your cart", remove_from_cart: "Updating your cart",
  view_cart: "Checking your cart", clear_cart: "Clearing your cart", place_order: "Placing your order",
  track_order: "Checking your order", lookup_policy: "Reading the policy",
};

function RichText({ text }) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith("**") && part.endsWith("**") ? <b key={i}>{part.slice(2, -2)}</b> : part);
}

export default function Assistant({ open, seed, sessionId, info, onClose, onCart, checkout, notify }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [liveSteps, setLiveSteps] = useState([]);
  const [showTrace, setShowTrace] = useState(null);
  const [paying, setPaying] = useState(false);
  const endRef = useRef(null);
  const lastSeed = useRef(null);

  useEffect(() => { endRef.current?.scrollIntoView({ block: "end", behavior: "smooth" }); }, [messages, busy, liveSteps]);

  const send = (text) => {
    const message = text.trim();
    if (!message || busy) return;
    setMessages((m) => [...m, { from: "me", text: message }]);
    setBusy(true);
    setLiveSteps([]);
    chatStream(sessionId, message, {
      onStep: (step) => setLiveSteps((s) => [...s, step]),
      onDone: (r) => {
        const search = [...r.toolResults].reverse().find((t) => t.tool === "search_menu");
        const payment = r.toolResults.find((t) => t.tool === "place_order" && t.result.status === "PAYMENT_REQUIRED");
        setMessages((m) => [...m, {
          from: "bot", text: r.reply, run: r,
          dishes: search?.result?.dishes ?? [],
          payAmount: payment ? payment.result.toPay : null,
        }]);
        onCart(r.cart);
        if (r.order && r.trace.some((t) => t.tool === "place_order" && !t.error && !payment)) {
          window.location.hash = `#/order/${r.order.orderId}`;
        }
        setBusy(false);
        setLiveSteps([]);
      },
      onError: (e) => {
        setMessages((m) => [...m, { from: "bot", text: e.message, error: true }]);
        setBusy(false);
        setLiveSteps([]);
      },
    });
  };

  const pay = async () => {
    setPaying(true);
    try {
      const order = await checkout();
      setMessages((m) => [...m, { from: "bot", text: `Payment received. Order **${order.orderId}** is confirmed and on its way.` }]);
    } catch (e) {
      notify(e.message, e.message === "Payment cancelled" ? "ok" : "error");
    } finally {
      setPaying(false);
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
            <p>Tell me what you feel like eating. I can search every menu, fill your cart and get your order ready. You always confirm and pay yourself.</p>
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
                    <DishImage item={{ id: d.itemId, name: d.name, tags: d.tags }} className="a-thumb" />
                    <span><b>{d.name}</b><small>{d.restaurant} · {rupees(d.price)} · {d.deliveryMinutes} min</small></span>
                    <span className="a-add">Add</span>
                  </button>
                ))}
              </div>
            )}

            {m.payAmount != null && (
              <button className="btn primary pay-btn" onClick={pay} disabled={paying}>
                {paying ? "Waiting for payment…" : `Pay ${rupees(m.payAmount)} with Razorpay`}
              </button>
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

        {busy && (
          <div className="bubble bot thinking live">
            {liveSteps.length === 0 ? "Thinking…" : (
              <ul className="live-steps">
                {liveSteps.map((s, k) => (
                  <li key={k} className={s.error ? "err" : "ok"}>{STEP_LABEL[s.tool] ?? s.tool}: {s.summary}</li>
                ))}
                <li className="pending">Working…</li>
              </ul>
            )}
          </div>
        )}
        <div ref={endRef} />
      </div>

      <form className="assistant-input" onSubmit={(e) => { e.preventDefault(); send(input); setInput(""); }}>
        <input value={input} onChange={(e) => setInput(e.target.value)} maxLength={1000} placeholder="Ask for food, an order, or a policy…" aria-label="Message DineAgent" />
        <button className="btn primary" disabled={busy || !input.trim()}>Send</button>
      </form>
    </aside>
  );
}
