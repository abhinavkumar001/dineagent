import { useEffect, useState } from "react";
import { api } from "./api.js";
import ChatPanel from "./components/ChatPanel.jsx";
import CartPanel from "./components/CartPanel.jsx";
import OrderPanel from "./components/OrderPanel.jsx";
import TracePanel from "./components/TracePanel.jsx";
import EvalPanel from "./components/EvalPanel.jsx";

const SESSION_KEY = "dineagent.session";

function loadSessionId() {
  let id = null;
  try { id = localStorage.getItem(SESSION_KEY); } catch { /* storage unavailable */ }
  if (!id) {
    id = crypto.randomUUID();
    try { localStorage.setItem(SESSION_KEY, id); } catch { /* storage unavailable */ }
  }
  return id;
}

export default function App() {
  const [sessionId, setSessionId] = useState(loadSessionId);
  const [info, setInfo] = useState(null);
  const [infoError, setInfoError] = useState("");
  const [messages, setMessages] = useState([]); // { from: "me" | "bot", text, dishes? }
  const [cart, setCart] = useState(null);
  const [order, setOrder] = useState(null);
  const [lastRun, setLastRun] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.info().then(setInfo).catch((e) => setInfoError(e.message));
  }, []);

  useEffect(() => {
    api.session(sessionId).then((s) => { setCart(s.cart); setOrder(s.order); }).catch(() => {});
  }, [sessionId]);

  const send = async (text) => {
    const message = text.trim();
    if (!message || busy) return;
    setMessages((m) => [...m, { from: "me", text: message }]);
    setBusy(true);
    try {
      const r = await api.chat(sessionId, message);
      const search = [...r.toolResults].reverse().find((t) => t.tool === "search_menu");
      setMessages((m) => [...m, { from: "bot", text: r.reply, dishes: search?.result?.dishes ?? [] }]);
      setCart(r.cart);
      setOrder(r.order);
      setLastRun(r);
    } catch (e) {
      setMessages((m) => [...m, { from: "bot", text: e.message, error: true }]);
    } finally {
      setBusy(false);
    }
  };

  const newChat = () => {
    const id = crypto.randomUUID();
    try { localStorage.setItem(SESSION_KEY, id); } catch { /* storage unavailable */ }
    setSessionId(id);
    setMessages([]);
    setCart(null);
    setOrder(null);
    setLastRun(null);
  };

  return (
    <div className="app">
      <header className="bar">
        <span className="logo">DineAgent</span>
        <span className="status">
          {infoError ? infoError
            : !info ? "Connecting…"
            : info.llmConfigured
              ? <><b>{info.model}</b>, {info.semanticSearch ? "semantic + keyword search" : "keyword search"}, {info.dishes} dishes</>
              : <><b>No model configured.</b> Set GEMINI_API_KEY and restart the backend.</>}
        </span>
        <span className="spacer" />
        <button className="btn" onClick={newChat}>New chat</button>
      </header>

      <ChatPanel messages={messages} busy={busy} onSend={send} />

      <aside>
        <CartPanel cart={cart} onReview={() => send("Show my cart and place the order")} />
        <OrderPanel order={order} />
        <TracePanel run={lastRun} />
        <EvalPanel cases={info?.evalCases} />
      </aside>
    </div>
  );
}
