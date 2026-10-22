import { useEffect, useMemo, useState } from "react";
import { api, rupees } from "../api.js";
import { DishRow, Stars } from "./Bits.jsx";

export default function RestaurantPage({ id, cartActions, askAssistant }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [vegOnly, setVegOnly] = useState(false);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    setData(null);
    api.restaurant(id).then(setData).catch((e) => setError(e.message));
  }, [id]);

  const sections = useMemo(() => (data?.menu ?? []).map((s) => ({
    ...s,
    items: s.items.filter((i) => (!vegOnly || i.veg)
      && (!filter || (i.name + " " + i.description).toLowerCase().includes(filter.toLowerCase()))),
  })).filter((s) => s.items.length > 0), [data, vegOnly, filter]);

  if (error) return <p className="error-text">{error}</p>;
  if (!data) return <p className="muted">Loading menu…</p>;

  return (
    <div className="restaurant">
      <a href="#/" className="back">← All restaurants</a>
      <div className="r-head">
        <div>
          <h1>{data.name}</h1>
          <p className="muted">{data.cuisine} · {data.area}</p>
          <p className="r-meta"><Stars rating={data.rating} /> · {data.deliveryMinutes} min · {rupees(data.costForTwo)} for two</p>
        </div>
        {data.offer && <div className="offer-box">{data.offer}</div>}
      </div>

      <div className="menu-tools">
        <input type="search" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Search in menu" aria-label="Search in menu" />
        {!data.pureVeg && (
          <label className="toggle"><input type="checkbox" checked={vegOnly} onChange={(e) => setVegOnly(e.target.checked)} /> Veg only</label>
        )}
        <button className="link-btn" onClick={() => askAssistant(`What should I order from ${data.name}?`)}>✦ Ask AI what to order</button>
      </div>

      {sections.length === 0 && <p className="muted">No dishes match.</p>}
      {sections.map((s) => (
        <section key={s.category} className="menu-section">
          <h2>{s.category} <span className="muted">({s.items.length})</span></h2>
          {s.items.map((item) => <DishRow key={item.id} item={item} cartActions={cartActions} />)}
        </section>
      ))}
    </div>
  );
}
