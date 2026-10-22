import { useEffect, useRef, useState } from "react";
import DishCards from "./DishCards.jsx";

const STARTERS = [
  "Something spicy and veg under ₹300",
  "Light dinner that arrives in 30 minutes",
  "Is there a delivery fee on a ₹150 order?",
  "Best biryani you have",
];

/** Renders **bold** from the model's reply safely (React escapes everything else). */
function RichText({ text }) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith("**") && part.endsWith("**") ? <b key={i}>{part.slice(2, -2)}</b> : part);
}

export default function ChatPanel({ messages, busy, onSend }) {
  const [input, setInput] = useState("");
  const endRef = useRef(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end", behavior: "smooth" });
  }, [messages, busy]);

  const submit = (e) => {
    e.preventDefault();
    onSend(input);
    setInput("");
  };

  return (
    <main>
      <div className="chat" aria-live="polite">
        {messages.length === 0 && (
          <div className="welcome">
            <h1>Tell me what you’re craving.</h1>
            <p>I search menus across Bangalore restaurants, build your cart and place the order once you say yes.</p>
            <div className="starters">
              {STARTERS.map((s) => <button key={s} onClick={() => onSend(s)}>{s}</button>)}
            </div>
          </div>
        )}

        {messages.map((m, i) => (
          <div key={i} className="turn">
            <div className={`msg ${m.from} ${m.error ? "error" : ""}`}><RichText text={m.text} /></div>
            {m.dishes?.length > 0 && (
              <DishCards dishes={m.dishes} disabled={busy}
                         onAdd={(d) => onSend(`Add 1 ${d.name} (${d.itemId}) to my cart`)} />
            )}
          </div>
        ))}

        {busy && <div className="msg bot thinking">Thinking…</div>}
        <div ref={endRef} />
      </div>

      <form className="composer" onSubmit={submit}>
        <input value={input} onChange={(e) => setInput(e.target.value)} maxLength={1000} autoComplete="off"
               placeholder="e.g. a filling lunch under ₹250, not too spicy" aria-label="Message" />
        <button className="btn primary" disabled={busy || !input.trim()}>Send</button>
      </form>
    </main>
  );
}
