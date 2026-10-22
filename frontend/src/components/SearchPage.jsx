import { useEffect, useState } from "react";
import { api } from "../api.js";
import { DishRow } from "./Bits.jsx";

/** Turns plain words into exact filters, so "veg under 250 in 30 min" works without an AI call. */
function parse(q) {
  const t = q.toLowerCase();
  const p = { q };
  const price = t.match(/(?:under|below|less than)\s*₹?\s*(\d+)/);
  if (price) p.maxPrice = price[1];
  const mins = t.match(/(\d+)\s*min/);
  if (mins) p.maxMinutes = mins[1];
  if (/non[- ]?veg/.test(t)) p.veg = false;
  else if (/\bveg\b|vegetarian/.test(t)) p.veg = true;
  if (/not (too )?spicy|mild/.test(t)) p.maxSpice = 1;
  else if (/spicy|hot/.test(t)) p.minSpice = 2;
  return p;
}

export default function SearchPage({ query, cartActions, askAssistant }) {
  const [results, setResults] = useState(null);
  const [error, setError] = useState("");
  const filters = parse(query);

  useEffect(() => {
    setResults(null);
    api.search(parse(query)).then(setResults).catch((e) => setError(e.message));
  }, [query]);

  const applied = [
    filters.veg === true && "Veg", filters.veg === false && "Non-veg",
    filters.maxPrice && `Under ₹${filters.maxPrice}`, filters.maxMinutes && `Within ${filters.maxMinutes} min`,
    filters.minSpice && "Spicy", filters.maxSpice && "Not too spicy",
  ].filter(Boolean);

  return (
    <div className="search-page">
      <a href="#/" className="back">← Home</a>
      <h1>Results for “{query}”</h1>
      {applied.length > 0 && (
        <p className="chips">{applied.map((a) => <span key={a} className="chip on">{a}</span>)}</p>
      )}
      <div className="ai-banner">
        <span>Want help choosing, or the cart filled for you?</span>
        <button className="btn primary" onClick={() => askAssistant(query)}>✦ Ask DineAgent</button>
      </div>
      {error && <p className="error-text">{error}</p>}
      {!results && !error && <p className="muted">Searching…</p>}
      {results?.length === 0 && <p className="muted">No dishes match. Try fewer filters, or ask DineAgent.</p>}
      {results?.map((r) => <DishRow key={r.item.id} item={r.item} restaurant={r.restaurant} cartActions={cartActions} />)}
    </div>
  );
}
