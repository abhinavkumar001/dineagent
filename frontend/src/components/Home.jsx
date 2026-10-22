import { useEffect, useState } from "react";
import { api, CUISINES, cuisineEmoji, rupees } from "../api.js";
import { Stars } from "./Bits.jsx";
import { DishImage } from "./DishArt.jsx";

const AI_IDEAS = [
  "Light veg dinner under ₹250",
  "Something spicy that arrives in 25 minutes",
  "High protein lunch",
  "Dessert for two under ₹300",
];

export default function Home({ askAssistant }) {
  const [cuisine, setCuisine] = useState("");
  const [pureVeg, setPureVeg] = useState(false);
  const [sort, setSort] = useState("relevance");
  const [restaurants, setRestaurants] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.restaurants({ cuisine, pureVeg, sort }).then(setRestaurants).catch((e) => setError(e.message));
  }, [cuisine, pureVeg, sort]);

  return (
    <>
      <section className="ai-hero">
        <div>
          <h1>Hungry? Just say what you feel like.</h1>
          <p>DineAgent reads menus across the city, builds your cart and orders when you say yes.</p>
        </div>
        <div className="ai-ideas">
          {AI_IDEAS.map((idea) => (
            <button key={idea} onClick={() => askAssistant(idea)}><span aria-hidden="true">✦</span> {idea}</button>
          ))}
        </div>
      </section>

      <section>
        <h2 className="section-title">What’s on your mind?</h2>
        <div className="cuisines">
          {CUISINES.map((c) => (
            <button key={c.name} className={`cuisine ${cuisine === c.name ? "on" : ""}`}
                    onClick={() => setCuisine(cuisine === c.name ? "" : c.name)} aria-pressed={cuisine === c.name}>
              <span className="cuisine-icon" aria-hidden="true">{c.emoji}</span>{c.name}
            </button>
          ))}
        </div>
      </section>

      <section>
        <h2 className="section-title">{cuisine ? `${cuisine} restaurants` : "Restaurants delivering to you"}</h2>
        <div className="chips">
          <button className={`chip ${pureVeg ? "on" : ""}`} aria-pressed={pureVeg} onClick={() => setPureVeg(!pureVeg)}>Pure veg</button>
          {[["rating", "Rating 4.5+ first"], ["delivery", "Fast delivery"], ["cost", "Low to high cost"]].map(([key, label]) => (
            <button key={key} className={`chip ${sort === key ? "on" : ""}`} aria-pressed={sort === key}
                    onClick={() => setSort(sort === key ? "relevance" : key)}>{label}</button>
          ))}
        </div>
        {error && <p className="error-text">{error}</p>}
        {restaurants?.length === 0 && <p className="muted">No restaurants match. Clear a filter.</p>}
        <div className="restaurant-grid">
          {restaurants?.map((r) => (
            <a key={r.id} className="restaurant-card" href={`#/restaurant/${r.id}`}>
              <div className="r-cover">
                {r.coverItem ? <DishImage item={r.coverItem} className="cover-img" />
                  : <span className="cover-emoji" aria-hidden="true">{cuisineEmoji(r.cuisine)}</span>}
                {r.offer && <b className="offer">{r.offer}</b>}
              </div>
              <h3>{r.name}</h3>
              <p className="r-meta"><Stars rating={r.rating} /> · {r.deliveryMinutes} min</p>
              <p className="muted">{r.cuisine}{r.pureVeg ? ", pure veg" : ""} · {rupees(r.costForTwo)} for two</p>
              <p className="muted">{r.area}</p>
            </a>
          ))}
        </div>
      </section>
    </>
  );
}
