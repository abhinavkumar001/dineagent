import { useState } from "react";

export default function Header({ cart, info, onAssistant }) {
  const [q, setQ] = useState("");
  const submit = (e) => {
    e.preventDefault();
    if (q.trim()) window.location.hash = `#/search?q=${encodeURIComponent(q.trim())}`;
  };
  return (
    <header className="header">
      <div className="header-in">
        <a href="#/" className="brand" aria-label="DineAgent home">
          <span className="brand-mark">D</span><span className="brand-name">DineAgent</span>
        </a>
        <span className="location" title="Delivery location">
          <b>Koramangala</b> Bangalore
        </span>
        <form className="search" onSubmit={submit} role="search">
          <span aria-hidden="true">⌕</span>
          <input value={q} onChange={(e) => setQ(e.target.value)}
                 placeholder="Try “spicy veg under ₹250” or “light dinner in 30 min”" aria-label="Search dishes" />
        </form>
        <nav className="nav">
          <button className="nav-link ai" onClick={onAssistant}><span aria-hidden="true">✦</span> Ask AI</button>
          <a className="nav-link" href="#/orders">Orders</a>
          <a className="nav-link cart-link" href="#/cart">
            Cart{cart?.count > 0 && <span className="badge">{cart.count}</span>}
          </a>
        </nav>
      </div>
      {info && !info.offline && !info.llmConfigured && (
        <div className="notice">AI features need a Gemini key: set GEMINI_API_KEY and restart the backend. Browsing and ordering still work.</div>
      )}
      {info?.offline && <div className="notice error">Can’t reach the backend on port 8080. Start the Spring Boot app.</div>}
    </header>
  );
}
